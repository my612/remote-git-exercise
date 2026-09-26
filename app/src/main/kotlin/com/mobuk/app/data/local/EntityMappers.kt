package com.mobuk.app.data.local

import com.mobuk.app.domain.model.Aisle
import com.mobuk.app.domain.model.ChatMessage
import com.mobuk.app.domain.model.ChatRole
import com.mobuk.app.domain.model.DietTag
import com.mobuk.app.domain.model.McpServerConfig
import com.mobuk.app.domain.model.MealPlan
import com.mobuk.app.domain.model.MealSlot
import com.mobuk.app.domain.model.PlannerEntry
import com.mobuk.app.domain.model.Recipe
import com.mobuk.app.domain.model.RecipeCollection
import com.mobuk.app.domain.model.RecipeSummary
import com.mobuk.app.domain.model.ShoppingItem
import kotlinx.serialization.json.Json

/** A single lenient Json instance shared by the cache, the network layer and the AI layer. */
val AppJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
    explicitNulls = false
    coerceInputValues = true
}

fun Recipe.toEntity(existing: RecipeEntity? = null): RecipeEntity = RecipeEntity(
    id = id,
    source = source.name,
    sourceId = sourceId,
    title = title,
    imageUrl = imageUrl,
    cuisine = cuisine,
    category = category,
    totalMinutes = effectiveMinutes,
    calories = nutrition?.calories,
    proteinG = nutrition?.proteinG,
    dietTags = dietTags.joinToString(",") { it.name },
    isComplete = isComplete,
    json = AppJson.encodeToString(Recipe.serializer(), this),
    cachedAt = if (cachedAt > 0) cachedAt else System.currentTimeMillis(),
    lastOpenedAt = existing?.lastOpenedAt ?: 0,
    openCount = existing?.openCount ?: 0,
    lastCookedAt = existing?.lastCookedAt ?: 0,
    cookCount = existing?.cookCount ?: 0,
    isSaved = existing?.isSaved ?: false,
    savedAt = existing?.savedAt ?: 0,
    userRating = existing?.userRating ?: 0,
)

fun RecipeEntity.toDomain(): Recipe = runCatching { AppJson.decodeFromString(Recipe.serializer(), json) }
    .getOrElse {
        Recipe(
            id = id,
            source = runCatching { enumValueOf<com.mobuk.app.domain.model.RecipeSource>(source) }.getOrDefault(com.mobuk.app.domain.model.RecipeSource.USER),
            sourceId = sourceId,
            title = title,
            imageUrl = imageUrl,
            cuisine = cuisine,
            category = category,
            totalMinutes = totalMinutes,
            isComplete = false,
        )
    }

fun RecipeEntity.toSummary(): RecipeSummary = RecipeSummary(
    id = id,
    title = title,
    imageUrl = imageUrl,
    source = runCatching { enumValueOf<com.mobuk.app.domain.model.RecipeSource>(source) }.getOrDefault(com.mobuk.app.domain.model.RecipeSource.USER),
    cuisine = cuisine,
    category = category,
    totalMinutes = totalMinutes,
    dietTags = dietTags.split(',').filter { it.isNotBlank() }.mapNotNull { runCatching { DietTag.valueOf(it) }.getOrNull() }.toSet(),
    calories = calories,
)

fun CollectionEntity.toDomain(count: Int = 0) = RecipeCollection(
    id = id, name = name, description = description, coverImageUrl = coverImageUrl, recipeCount = count, createdAt = createdAt,
)

fun PlannerEntryEntity.toDomain() = PlannerEntry(
    id = id,
    epochDay = epochDay,
    slot = runCatching { MealSlot.valueOf(slot) }.getOrDefault(MealSlot.DINNER),
    recipeId = recipeId,
    servings = servings,
    cooked = cooked,
    note = note,
)

fun PlannerEntry.toEntity() = PlannerEntryEntity(
    id = id, epochDay = epochDay, slot = slot.name, recipeId = recipeId, servings = servings, cooked = cooked, note = note,
    createdAt = System.currentTimeMillis(),
)

fun ShoppingItemEntity.toDomain() = ShoppingItem(
    id = id,
    name = name,
    quantity = quantity,
    unit = unit,
    aisle = runCatching { Aisle.valueOf(aisle) }.getOrDefault(Aisle.OTHER),
    checked = checked,
    recipeIds = recipeIds.split(',').filter { it.isNotBlank() },
    isCustom = isCustom,
    note = note,
    createdAt = createdAt,
)

fun ShoppingItem.toEntity() = ShoppingItemEntity(
    id = id,
    name = name,
    quantity = quantity,
    unit = unit,
    aisle = aisle.name,
    checked = checked,
    recipeIds = recipeIds.joinToString(","),
    isCustom = isCustom,
    note = note,
    createdAt = createdAt,
)

fun MealPlanEntity.toDomain(): MealPlan? = runCatching { AppJson.decodeFromString(MealPlan.serializer(), json) }.getOrNull()

fun MealPlan.toEntity() = MealPlanEntity(
    id = id, title = title, json = AppJson.encodeToString(MealPlan.serializer(), this), createdAt = createdAt,
)

fun McpServerEntity.toDomain() = McpServerConfig(
    id = id, name = name, url = url, bearerToken = bearerToken, enabled = enabled, lastStatus = lastStatus,
    toolNames = toolNames.split(',').filter { it.isNotBlank() },
)

fun McpServerConfig.toEntity(createdAt: Long = System.currentTimeMillis()) = McpServerEntity(
    id = id, name = name, url = url, bearerToken = bearerToken, enabled = enabled, lastStatus = lastStatus,
    toolNames = toolNames.joinToString(","), createdAt = createdAt,
)

fun ChatMessageEntity.toDomain() = ChatMessage(
    id = id,
    role = runCatching { ChatRole.valueOf(role) }.getOrDefault(ChatRole.ASSISTANT),
    content = content,
    recipeIds = recipeIds.split(',').filter { it.isNotBlank() },
    timestamp = timestamp,
)

fun ChatMessage.toEntity() = ChatMessageEntity(
    id = id, role = role.name, content = content, recipeIds = recipeIds.joinToString(","), timestamp = timestamp,
)
