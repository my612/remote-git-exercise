package com.mobuk.app.domain.logic

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

data class ParsedQuantity(
    val amount: Double?,
    val unit: String?,
    /** Whatever was left after the number and unit, usually the ingredient name. */
    val rest: String,
    /** Set when the source gave a range such as "2-3"; [amount] holds the lower bound. */
    val upperAmount: Double? = null,
)

/**
 * Parses free-text ingredient measures ("1 1/2 cups", "½ tsp", "200g", "2-3 cloves", "a pinch").
 * Pure Kotlin so it can be unit tested without Android.
 */
object QuantityParser {

    private val unicodeFractions = mapOf(
        '¼' to 0.25, '½' to 0.5, '¾' to 0.75, '⅓' to 1.0 / 3, '⅔' to 2.0 / 3,
        '⅛' to 0.125, '⅜' to 0.375, '⅝' to 0.625, '⅞' to 0.875, '⅕' to 0.2, '⅖' to 0.4, '⅗' to 0.6, '⅘' to 0.8,
    )

    /** Canonical unit -> accepted spellings. Order matters: longer spellings first when matching. */
    private val unitAliases: Map<String, List<String>> = linkedMapOf(
        "tbsp" to listOf("tablespoons", "tablespoon", "tbsps", "tbsp", "tbs", "tbl", "T"),
        "tsp" to listOf("teaspoons", "teaspoon", "tsps", "tsp", "t"),
        "cup" to listOf("cups", "cup", "c"),
        "kg" to listOf("kilograms", "kilogram", "kgs", "kg"),
        "g" to listOf("grams", "gram", "gr", "g"),
        "mg" to listOf("milligrams", "mg"),
        "l" to listOf("litres", "liters", "litre", "liter", "l"),
        "ml" to listOf("millilitres", "milliliters", "ml", "mls"),
        "oz" to listOf("ounces", "ounce", "oz"),
        "fl oz" to listOf("fluid ounces", "fluid ounce", "fl oz", "fl. oz", "floz"),
        "lb" to listOf("pounds", "pound", "lbs", "lb"),
        "pint" to listOf("pints", "pint", "pt"),
        "quart" to listOf("quarts", "quart", "qt"),
        "gallon" to listOf("gallons", "gallon", "gal"),
        "clove" to listOf("cloves", "clove"),
        "can" to listOf("cans", "can", "tins", "tin"),
        "jar" to listOf("jars", "jar"),
        "pack" to listOf("packs", "pack", "packet", "packets", "package", "packages", "pkg"),
        "slice" to listOf("slices", "slice"),
        "piece" to listOf("pieces", "piece", "pcs", "pc"),
        "bunch" to listOf("bunches", "bunch"),
        "sprig" to listOf("sprigs", "sprig"),
        "stalk" to listOf("stalks", "stalk", "stick", "sticks", "rib", "ribs"),
        "handful" to listOf("handfuls", "handful"),
        "pinch" to listOf("pinches", "pinch"),
        "dash" to listOf("dashes", "dash"),
        "drop" to listOf("drops", "drop"),
        "leaf" to listOf("leaves", "leaf"),
        "head" to listOf("heads", "head"),
        "fillet" to listOf("fillets", "fillet"),
        "breast" to listOf("breasts", "breast"),
        "thigh" to listOf("thighs", "thigh"),
        "large" to listOf("large", "lg"),
        "medium" to listOf("medium", "med"),
        "small" to listOf("small", "sm"),
        "whole" to listOf("whole"),
        "sheet" to listOf("sheets", "sheet"),
        "block" to listOf("blocks", "block"),
        "bottle" to listOf("bottles", "bottle"),
        "envelope" to listOf("envelopes", "envelope", "sachet", "sachets"),
        "scoop" to listOf("scoops", "scoop"),
        "stick" to listOf("sticks", "stick"),
        "ball" to listOf("balls", "ball"),
        "container" to listOf("containers", "container", "tub", "tubs", "carton", "cartons"),
    )

    private val aliasToUnit: List<Pair<String, String>> = unitAliases
        .flatMap { (unit, aliases) -> aliases.map { it to unit } }
        .sortedByDescending { it.first.length }

    private val vagueAmounts = mapOf(
        "a pinch" to 0.0, "pinch" to 0.0, "to taste" to 0.0, "dash" to 0.0, "a dash" to 0.0,
        "a splash" to 0.0, "splash" to 0.0, "some" to 0.0, "a little" to 0.0, "a few" to 3.0,
        "a handful" to 1.0, "handful" to 1.0, "a couple" to 2.0, "half" to 0.5, "a" to 1.0, "an" to 1.0,
    )

    private val numberToken = Regex("""^(\d+(?:[.,]\d+)?)$""")
    private val fractionToken = Regex("""^(\d+)/(\d+)$""")
    private val rangeSeparator = Regex("""^(?:-|–|—|to|or)$""")

    fun parse(input: String): ParsedQuantity {
        val text = input.trim().replace(Regex("\\s+"), " ")
        if (text.isEmpty()) return ParsedQuantity(null, null, "")

        val tokens = text.split(" ").toMutableList()
        var amount: Double? = null
        var upper: Double? = null
        var index = 0

        // Amount: handles "1", "1.5", "1/2", "1 1/2", "½", "1½", "2-3", "2 to 3".
        val first = tokens.getOrNull(index)
        if (first != null) {
            val parsed = parseNumberToken(first)
            if (parsed != null) {
                amount = parsed
                index++
                val second = tokens.getOrNull(index)
                if (second != null) {
                    val frac = fractionToken.matchEntire(second)
                    if (frac != null) {
                        amount = amount + frac.groupValues[1].toDouble() / frac.groupValues[2].toDouble()
                        index++
                    } else if (unicodeFractions.containsKey(second.firstOrNull() ?: ' ') && second.length == 1) {
                        amount = amount + unicodeFractions.getValue(second.first())
                        index++
                    }
                }
                // Range
                val sep = tokens.getOrNull(index)
                if (sep != null && rangeSeparator.matches(sep.lowercase())) {
                    val up = tokens.getOrNull(index + 1)?.let { parseNumberToken(it) }
                    if (up != null) {
                        upper = up
                        index += 2
                    }
                } else if (sep != null && sep.contains('-')) {
                    val parts = sep.split('-')
                    val up = parts.getOrNull(0)?.let { parseNumberToken(it) }
                    if (up != null && up > amount) { upper = up; index++ }
                }
            } else {
                // Embedded range like "2-3"
                val parts = first.split(Regex("[-–—]"))
                if (parts.size == 2) {
                    val lo = parseNumberToken(parts[0])
                    val hi = parseNumberToken(parts[1])
                    if (lo != null && hi != null) {
                        amount = lo; upper = hi; index++
                    }
                }
                if (amount == null) {
                    // Number glued to unit e.g. "200g", "1.5kg", "400ml"
                    val glued = Regex("""^(\d+(?:[.,]\d+)?)([a-zA-Z]+)$""").matchEntire(first)
                    if (glued != null) {
                        val unit = canonicalUnit(glued.groupValues[2])
                        if (unit != null) {
                            val rest = tokens.drop(1).joinToString(" ")
                            return ParsedQuantity(glued.groupValues[1].replace(',', '.').toDouble(), unit, cleanRest(rest))
                        }
                    }
                }
            }
        }

        if (amount == null) {
            // Vague amounts: "a pinch of salt", "to taste"
            val lower = text.lowercase()
            for ((phrase, value) in vagueAmounts.entries.sortedByDescending { it.key.length }) {
                if (lower.startsWith("$phrase ")) {
                    val rest = text.substring(phrase.length).trim()
                    val unitAndRest = takeUnit(rest.split(" "))
                    return ParsedQuantity(
                        value.takeIf { it > 0 },
                        unitAndRest.first ?: phrase.removePrefix("a ").takeIf { it in setOf("pinch", "dash", "splash", "handful") },
                        cleanRest(unitAndRest.second),
                    )
                }
            }
            return ParsedQuantity(null, null, cleanRest(text))
        }

        val (unit, rest) = takeUnit(tokens.drop(index))
        return ParsedQuantity(amount, unit, cleanRest(rest), upper)
    }

    private fun takeUnit(tokens: List<String>): Pair<String?, String> {
        if (tokens.isEmpty()) return null to ""
        // Two-word units first ("fl oz", "fluid ounce")
        if (tokens.size >= 2) {
            val two = (tokens[0] + " " + tokens[1]).lowercase().trimEnd('.', ',')
            canonicalUnit(two)?.let { return it to tokens.drop(2).joinToString(" ") }
        }
        val one = tokens[0].trimEnd('.', ',')
        val unit = canonicalUnit(one)
        // Avoid treating a single "t"/"T"/"c" as a unit when it's actually the start of a word? Those are
        // whole tokens so they are safe.
        return if (unit != null) unit to tokens.drop(1).joinToString(" ") else null to tokens.joinToString(" ")
    }

    fun canonicalUnit(raw: String): String? {
        val key = raw.trim()
        if (key.isEmpty()) return null
        val exact = aliasToUnit.firstOrNull { it.first == key }
        if (exact != null) return exact.second
        val lower = key.lowercase()
        return aliasToUnit.firstOrNull { it.first.lowercase() == lower && it.first.length > 1 }?.second
    }

    private fun parseNumberToken(token: String): Double? {
        val t = token.trim()
        numberToken.matchEntire(t)?.let { return it.groupValues[1].replace(',', '.').toDouble() }
        fractionToken.matchEntire(t)?.let { m ->
            val d = m.groupValues[2].toDouble()
            return if (d == 0.0) null else m.groupValues[1].toDouble() / d
        }
        if (t.length == 1 && unicodeFractions.containsKey(t[0])) return unicodeFractions.getValue(t[0])
        // "1½"
        if (t.length >= 2 && unicodeFractions.containsKey(t.last())) {
            val whole = t.dropLast(1).toDoubleOrNull() ?: return null
            return whole + unicodeFractions.getValue(t.last())
        }
        return null
    }

    private fun cleanRest(rest: String): String =
        rest.trim().removePrefix("of ").removePrefix("Of ").trim().trimStart(',', '-', ':').trim()
}

object QuantityFormatter {
    private val fractions = listOf(
        0.125 to "⅛", 0.25 to "¼", 1.0 / 3 to "⅓", 0.375 to "⅜", 0.5 to "½",
        0.625 to "⅝", 2.0 / 3 to "⅔", 0.75 to "¾", 0.875 to "⅞",
    )

    /** Formats 1.5 as "1½", 0.333 as "⅓", 200.0 as "200", 2.37 as "2.4". */
    fun format(amount: Double?): String {
        if (amount == null) return ""
        if (amount <= 0.0) return ""
        val whole = floor(amount).toInt()
        val frac = amount - whole
        if (frac < 0.03) return whole.toString()
        if (frac > 0.97) return (whole + 1).toString()
        val nearest = fractions.minByOrNull { abs(it.first - frac) }
        if (nearest != null && abs(nearest.first - frac) < 0.04) {
            return if (whole == 0) nearest.second else "$whole${nearest.second}"
        }
        val rounded = (amount * 10).roundToInt() / 10.0
        return if (rounded == floor(rounded)) rounded.toInt().toString() else rounded.toString()
    }

    fun formatWithUnit(amount: Double?, unit: String?): String {
        val a = format(amount)
        return when {
            a.isEmpty() && unit.isNullOrBlank() -> ""
            a.isEmpty() -> unit ?: ""
            unit.isNullOrBlank() -> a
            unit in setOf("g", "kg", "ml", "l", "mg") -> "$a$unit"
            else -> "$a $unit"
        }
    }
}

object ServingScaler {
    fun scaleAmount(amount: Double?, fromServings: Int?, toServings: Int): Double? {
        if (amount == null) return null
        val from = fromServings?.takeIf { it > 0 } ?: return amount
        if (toServings <= 0) return amount
        return amount * toServings / from.toDouble()
    }
}
