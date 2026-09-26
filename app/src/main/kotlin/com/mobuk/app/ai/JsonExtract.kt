package com.mobuk.app.ai

import com.mobuk.app.data.local.AppJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Small models wrap JSON in prose or code fences; these helpers dig the object out. */
object JsonExtract {

    fun firstObject(text: String): JsonObject? {
        val cleaned = text.replace("```json", "```").replace("```", "").trim()
        runCatching { AppJson.parseToJsonElement(cleaned) }.getOrNull()?.let { el ->
            (el as? JsonObject)?.let { return it }
            (el as? JsonArray)?.firstOrNull()?.let { (it as? JsonObject)?.let { o -> return o } }
        }
        // Scan for balanced braces.
        var depth = 0
        var start = -1
        var inString = false
        var escape = false
        for ((i, c) in cleaned.withIndex()) {
            if (inString) {
                when {
                    escape -> escape = false
                    c == '\\' -> escape = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                '{' -> { if (depth == 0) start = i; depth++ }
                '}' -> {
                    depth--
                    if (depth == 0 && start >= 0) {
                        val candidate = cleaned.substring(start, i + 1)
                        runCatching { AppJson.parseToJsonElement(candidate).jsonObject }.getOrNull()?.let { return it }
                        start = -1
                    }
                }
            }
        }
        return null
    }

    fun firstArray(text: String): JsonArray? {
        val cleaned = text.replace("```json", "```").replace("```", "").trim()
        runCatching { AppJson.parseToJsonElement(cleaned) }.getOrNull()?.let { el ->
            (el as? JsonArray)?.let { return it }
            (el as? JsonObject)?.values?.firstOrNull { it is JsonArray }?.let { return it.jsonArray }
        }
        val start = cleaned.indexOf('[')
        val end = cleaned.lastIndexOf(']')
        if (start in 0 until end) {
            runCatching { AppJson.parseToJsonElement(cleaned.substring(start, end + 1)).jsonArray }.getOrNull()?.let { return it }
        }
        return null
    }

    fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() && it != "null" }

    fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.content?.toDoubleOrNull()?.toInt()

    fun JsonObject.double(key: String): Double? = (this[key] as? JsonPrimitive)?.content?.toDoubleOrNull()

    fun JsonObject.strings(key: String): List<String> = when (val v = this[key]) {
        is JsonArray -> v.mapNotNull { el -> (el as? JsonPrimitive)?.content ?: (el as? JsonObject)?.let { o -> o.string("text") ?: o.string("name") } }
        is JsonPrimitive -> listOf(v.content)
        else -> emptyList()
    }

    fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

    fun JsonElement.asStringOrNull(): String? = runCatching { jsonPrimitive.content }.getOrNull()
}
