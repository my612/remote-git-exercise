package com.mobuk.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {
    @Upsert
    suspend fun upsert(recipe: RecipeEntity)

    @Upsert
    suspend fun upsertAll(recipes: List<RecipeEntity>)

    @Query("SELECT * FROM recipes WHERE id = :id")
    suspend fun getById(id: String): RecipeEntity?

    @Query("SELECT * FROM recipes WHERE id = :id")
    fun observeById(id: String): Flow<RecipeEntity?>

    @Query("SELECT * FROM recipes WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<RecipeEntity>

    @Query("SELECT * FROM recipes WHERE id IN (:ids)")
    fun observeByIds(ids: List<String>): Flow<List<RecipeEntity>>

    @Query("SELECT * FROM recipes WHERE isSaved = 1 ORDER BY savedAt DESC")
    fun observeSaved(): Flow<List<RecipeEntity>>

    @Query("SELECT * FROM recipes WHERE isSaved = 1 ORDER BY savedAt DESC")
    suspend fun getSaved(): List<RecipeEntity>

    @Query("SELECT COUNT(*) FROM recipes WHERE isSaved = 1")
    fun observeSavedCount(): Flow<Int>

    @Query("SELECT * FROM recipes WHERE lastOpenedAt > 0 ORDER BY lastOpenedAt DESC LIMIT :limit")
    fun observeRecentlyViewed(limit: Int): Flow<List<RecipeEntity>>

    @Query("SELECT * FROM recipes WHERE lastCookedAt > 0 ORDER BY lastCookedAt DESC LIMIT :limit")
    suspend fun recentlyCooked(limit: Int): List<RecipeEntity>

    @Query("SELECT * FROM recipes ORDER BY cachedAt DESC LIMIT :limit")
    suspend fun mostRecentlyCached(limit: Int): List<RecipeEntity>

    @Query("SELECT * FROM recipes WHERE isSaved = 1 OR openCount > 0 OR cookCount > 0")
    suspend fun interactedWith(): List<RecipeEntity>

    @Query("SELECT * FROM recipes WHERE title LIKE '%' || :query || '%' OR json LIKE '%' || :query || '%' LIMIT :limit")
    suspend fun searchCached(query: String, limit: Int): List<RecipeEntity>

    @Query("SELECT * FROM recipes WHERE source = :source")
    suspend fun bySource(source: String): List<RecipeEntity>

    @Query("SELECT * FROM recipes")
    suspend fun all(): List<RecipeEntity>

    @Query("SELECT COUNT(*) FROM recipes")
    fun observeCount(): Flow<Int>

    @Query("UPDATE recipes SET isSaved = :saved, savedAt = :at WHERE id = :id")
    suspend fun setSaved(id: String, saved: Boolean, at: Long)

    @Query("UPDATE recipes SET lastOpenedAt = :at, openCount = openCount + 1 WHERE id = :id")
    suspend fun markOpened(id: String, at: Long)

    @Query("UPDATE recipes SET lastCookedAt = :at, cookCount = cookCount + 1 WHERE id = :id")
    suspend fun markCooked(id: String, at: Long)

    @Query("UPDATE recipes SET userRating = :rating WHERE id = :id")
    suspend fun rate(id: String, rating: Int)

    @Query("DELETE FROM recipes WHERE id = :id")
    suspend fun delete(id: String)

    /** Evicts unsaved, unplanned, uncollected recipes older than [olderThan] to keep the cache bounded. */
    @Query(
        """DELETE FROM recipes WHERE isSaved = 0 AND cachedAt < :olderThan AND openCount = 0 AND cookCount = 0
           AND id NOT IN (SELECT recipeId FROM planner_entries)
           AND id NOT IN (SELECT recipeId FROM collection_recipes)""",
    )
    suspend fun evictStale(olderThan: Long)
}

@Dao
interface CollectionDao {
    @Insert
    suspend fun insert(collection: CollectionEntity): Long

    @Update
    suspend fun update(collection: CollectionEntity)

    @Query("DELETE FROM collections WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM collections ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<CollectionEntity>>

    @Query("SELECT * FROM collections WHERE id = :id")
    fun observeById(id: Long): Flow<CollectionEntity?>

    @Query("SELECT * FROM collections WHERE id = :id")
    suspend fun getById(id: Long): CollectionEntity?

    @Query("SELECT * FROM collections WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): CollectionEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addRecipe(ref: CollectionRecipeCrossRef)

    @Query("DELETE FROM collection_recipes WHERE collectionId = :collectionId AND recipeId = :recipeId")
    suspend fun removeRecipe(collectionId: Long, recipeId: String)

    @Query("DELETE FROM collection_recipes WHERE collectionId = :collectionId")
    suspend fun clearCollection(collectionId: Long)

    @Query("SELECT recipeId FROM collection_recipes WHERE collectionId = :collectionId ORDER BY addedAt DESC")
    fun observeRecipeIds(collectionId: Long): Flow<List<String>>

    @Query("SELECT recipeId FROM collection_recipes WHERE collectionId = :collectionId ORDER BY addedAt DESC")
    suspend fun recipeIds(collectionId: Long): List<String>

    @Query("SELECT collectionId FROM collection_recipes WHERE recipeId = :recipeId")
    fun observeCollectionIdsForRecipe(recipeId: String): Flow<List<Long>>

    @Query("SELECT collectionId, COUNT(*) AS count FROM collection_recipes GROUP BY collectionId")
    fun observeCounts(): Flow<List<CollectionCount>>

    @Query("SELECT r.imageUrl FROM recipes r INNER JOIN collection_recipes c ON r.id = c.recipeId WHERE c.collectionId = :collectionId AND r.imageUrl IS NOT NULL ORDER BY c.addedAt DESC LIMIT 1")
    suspend fun latestCoverImage(collectionId: Long): String?
}

data class CollectionCount(val collectionId: Long, val count: Int)

@Dao
interface PlannerDao {
    @Insert
    suspend fun insert(entry: PlannerEntryEntity): Long

    @Update
    suspend fun update(entry: PlannerEntryEntity)

    @Query("DELETE FROM planner_entries WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM planner_entries WHERE epochDay BETWEEN :fromDay AND :toDay ORDER BY epochDay, slot")
    fun observeRange(fromDay: Long, toDay: Long): Flow<List<PlannerEntryEntity>>

    @Query("SELECT * FROM planner_entries WHERE epochDay BETWEEN :fromDay AND :toDay ORDER BY epochDay, slot")
    suspend fun range(fromDay: Long, toDay: Long): List<PlannerEntryEntity>

    @Query("SELECT * FROM planner_entries WHERE epochDay >= :fromDay ORDER BY epochDay, slot")
    fun observeUpcoming(fromDay: Long): Flow<List<PlannerEntryEntity>>

    @Query("SELECT * FROM planner_entries WHERE id = :id")
    suspend fun getById(id: Long): PlannerEntryEntity?

    @Query("SELECT * FROM planner_entries")
    suspend fun all(): List<PlannerEntryEntity>

    @Query("DELETE FROM planner_entries WHERE epochDay BETWEEN :fromDay AND :toDay")
    suspend fun clearRange(fromDay: Long, toDay: Long)

    @Query("UPDATE planner_entries SET cooked = :cooked WHERE id = :id")
    suspend fun setCooked(id: Long, cooked: Boolean)

    @Query("SELECT COUNT(*) FROM planner_entries WHERE epochDay >= :fromDay")
    fun observeUpcomingCount(fromDay: Long): Flow<Int>
}

@Dao
interface ShoppingDao {
    @Insert
    suspend fun insert(item: ShoppingItemEntity): Long

    @Insert
    suspend fun insertAll(items: List<ShoppingItemEntity>)

    @Update
    suspend fun update(item: ShoppingItemEntity)

    @Delete
    suspend fun delete(item: ShoppingItemEntity)

    @Query("DELETE FROM shopping_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM shopping_items ORDER BY checked ASC, aisle ASC, createdAt ASC")
    fun observeAll(): Flow<List<ShoppingItemEntity>>

    @Query("SELECT * FROM shopping_items")
    suspend fun all(): List<ShoppingItemEntity>

    @Query("SELECT COUNT(*) FROM shopping_items WHERE checked = 0")
    fun observeUncheckedCount(): Flow<Int>

    @Query("UPDATE shopping_items SET checked = :checked WHERE id = :id")
    suspend fun setChecked(id: Long, checked: Boolean)

    @Query("DELETE FROM shopping_items WHERE checked = 1")
    suspend fun clearChecked()

    @Query("DELETE FROM shopping_items WHERE isCustom = 0")
    suspend fun clearRecipeItems()

    @Query("DELETE FROM shopping_items")
    suspend fun clearAll()

    @Query("UPDATE shopping_items SET checked = 0")
    suspend fun uncheckAll()

    @Transaction
    suspend fun replaceRecipeItems(items: List<ShoppingItemEntity>) {
        clearRecipeItems()
        insertAll(items)
    }
}

@Dao
interface MealPlanDao {
    @Upsert
    suspend fun upsert(plan: MealPlanEntity)

    @Query("SELECT * FROM meal_plans ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<MealPlanEntity>>

    @Query("SELECT * FROM meal_plans WHERE id = :id")
    fun observeById(id: String): Flow<MealPlanEntity?>

    @Query("SELECT * FROM meal_plans WHERE id = :id")
    suspend fun getById(id: String): MealPlanEntity?

    @Query("DELETE FROM meal_plans WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT COUNT(*) FROM meal_plans")
    suspend fun count(): Int
}

@Dao
interface McpServerDao {
    @Insert
    suspend fun insert(server: McpServerEntity): Long

    @Update
    suspend fun update(server: McpServerEntity)

    @Query("DELETE FROM mcp_servers WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM mcp_servers ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<McpServerEntity>>

    @Query("SELECT * FROM mcp_servers WHERE enabled = 1 ORDER BY createdAt ASC")
    suspend fun enabled(): List<McpServerEntity>

    @Query("SELECT * FROM mcp_servers WHERE id = :id")
    suspend fun getById(id: Long): McpServerEntity?

    @Query("UPDATE mcp_servers SET lastStatus = :status, toolNames = :toolNames WHERE id = :id")
    suspend fun setStatus(id: Long, status: String, toolNames: String)
}

@Dao
interface ChatDao {
    @Insert
    suspend fun insert(message: ChatMessageEntity): Long

    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun observeAll(): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages ORDER BY timestamp DESC LIMIT :limit")
    suspend fun latest(limit: Int): List<ChatMessageEntity>

    @Query("DELETE FROM chat_messages")
    suspend fun clear()
}

@Dao
interface SearchHistoryDao {
    @Upsert
    suspend fun upsert(entry: SearchHistoryEntity)

    @Query("SELECT * FROM search_history ORDER BY searchedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<SearchHistoryEntity>>

    @Query("DELETE FROM search_history")
    suspend fun clear()
}

@Dao
interface QueryCacheDao {
    @Upsert
    suspend fun upsert(entry: QueryCacheEntity)

    @Query("SELECT * FROM query_cache WHERE `key` = :key")
    suspend fun get(key: String): QueryCacheEntity?

    @Query("DELETE FROM query_cache WHERE fetchedAt < :olderThan")
    suspend fun evict(olderThan: Long)
}
