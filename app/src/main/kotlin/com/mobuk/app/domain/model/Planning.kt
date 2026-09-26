package com.mobuk.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class MealSlot(val label: String, val order: Int) {
    BREAKFAST("Breakfast", 0),
    LUNCH("Lunch", 1),
    DINNER("Dinner", 2),
    SNACK("Snacks", 3),
}

@Serializable
data class PlannerEntry(
    val id: Long = 0,
    /** Day as epoch day (java.time.LocalDate.toEpochDay) so it survives time zones. */
    val epochDay: Long,
    val slot: MealSlot,
    val recipeId: String,
    val servings: Int,
    val cooked: Boolean = false,
    val note: String? = null,
)

@Serializable
data class PlannedMeal(
    val entry: PlannerEntry,
    val recipe: RecipeSummary?,
)

@Serializable
data class ShoppingItem(
    val id: Long = 0,
    val name: String,
    val quantity: Double? = null,
    val unit: String? = null,
    val aisle: Aisle = Aisle.OTHER,
    val checked: Boolean = false,
    /** Recipe ids that contributed this item; empty for items the user typed themselves. */
    val recipeIds: List<String> = emptyList(),
    val isCustom: Boolean = false,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
data class RecipeCollection(
    val id: Long = 0,
    val name: String,
    val description: String? = null,
    val coverImageUrl: String? = null,
    val recipeCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

/** A curated multi-day plan, e.g. "The High-Protein One". Built from real recipes on the device. */
@Serializable
data class MealPlan(
    val id: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val coverImageUrl: String? = null,
    val recipeIds: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val generatedByAi: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
data class McpServerConfig(
    val id: Long = 0,
    val name: String,
    val url: String,
    val bearerToken: String? = null,
    val enabled: Boolean = true,
    val lastStatus: String? = null,
    val toolNames: List<String> = emptyList(),
)

@Serializable
enum class ChatRole { USER, ASSISTANT, TOOL, SYSTEM }

@Serializable
data class ChatMessage(
    val id: Long = 0,
    val role: ChatRole,
    val content: String,
    val recipeIds: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
)
