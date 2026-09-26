package com.mobuk.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Recipes are cached as a JSON blob (the full domain [com.mobuk.app.domain.model.Recipe]) plus a handful of
 * indexed columns for fast list queries. This keeps the schema stable while the domain model evolves.
 */
@Entity(
    tableName = "recipes",
    indices = [Index("source"), Index("isSaved"), Index("cuisine"), Index("category"), Index("lastOpenedAt")],
)
data class RecipeEntity(
    @PrimaryKey val id: String,
    val source: String,
    val sourceId: String,
    val title: String,
    val imageUrl: String?,
    val cuisine: String?,
    val category: String?,
    val totalMinutes: Int?,
    val calories: Double?,
    val proteinG: Double?,
    /** Comma separated [com.mobuk.app.domain.model.DietTag] names. */
    val dietTags: String,
    val isComplete: Boolean,
    /** Full recipe serialised with kotlinx.serialization. */
    val json: String,
    val cachedAt: Long,
    val lastOpenedAt: Long = 0,
    val openCount: Int = 0,
    val lastCookedAt: Long = 0,
    val cookCount: Int = 0,
    val isSaved: Boolean = false,
    val savedAt: Long = 0,
    /** Set when the user rated a recipe in the app (1..5). */
    val userRating: Int = 0,
)

@Entity(tableName = "collections")
data class CollectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String?,
    val coverImageUrl: String?,
    val createdAt: Long,
)

@Entity(
    tableName = "collection_recipes",
    primaryKeys = ["collectionId", "recipeId"],
    indices = [Index("recipeId")],
)
data class CollectionRecipeCrossRef(
    val collectionId: Long,
    val recipeId: String,
    val addedAt: Long,
)

@Entity(tableName = "planner_entries", indices = [Index("epochDay"), Index("recipeId")])
data class PlannerEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val slot: String,
    val recipeId: String,
    val servings: Int,
    val cooked: Boolean = false,
    val note: String? = null,
    val createdAt: Long,
)

@Entity(tableName = "shopping_items", indices = [Index("checked"), Index("aisle")])
data class ShoppingItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val quantity: Double?,
    val unit: String?,
    val aisle: String,
    val checked: Boolean = false,
    /** Comma separated recipe ids. */
    val recipeIds: String = "",
    val isCustom: Boolean = false,
    val note: String? = null,
    val createdAt: Long,
)

@Entity(tableName = "meal_plans")
data class MealPlanEntity(
    @PrimaryKey val id: String,
    val title: String,
    val json: String,
    val createdAt: Long,
)

@Entity(tableName = "mcp_servers")
data class McpServerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val url: String,
    val bearerToken: String?,
    val enabled: Boolean = true,
    val lastStatus: String? = null,
    val toolNames: String = "",
    val createdAt: Long,
)

@Entity(tableName = "chat_messages", indices = [Index("timestamp")])
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String,
    val content: String,
    val recipeIds: String = "",
    val timestamp: Long,
)

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey val query: String,
    @ColumnInfo(name = "searchedAt") val searchedAt: Long,
)

/** Remembers which provider queries were already run so lists can be served from cache while offline. */
@Entity(tableName = "query_cache")
data class QueryCacheEntity(
    @PrimaryKey val key: String,
    /** Comma separated recipe ids in result order. */
    val recipeIds: String,
    val fetchedAt: Long,
)
