package com.mobuk.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        RecipeEntity::class,
        CollectionEntity::class,
        CollectionRecipeCrossRef::class,
        PlannerEntryEntity::class,
        ShoppingItemEntity::class,
        MealPlanEntity::class,
        McpServerEntity::class,
        ChatMessageEntity::class,
        SearchHistoryEntity::class,
        QueryCacheEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class MobDatabase : RoomDatabase() {
    abstract fun recipeDao(): RecipeDao
    abstract fun collectionDao(): CollectionDao
    abstract fun plannerDao(): PlannerDao
    abstract fun shoppingDao(): ShoppingDao
    abstract fun mealPlanDao(): MealPlanDao
    abstract fun mcpServerDao(): McpServerDao
    abstract fun chatDao(): ChatDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun queryCacheDao(): QueryCacheDao

    companion object {
        fun build(context: Context): MobDatabase =
            Room.databaseBuilder(context.applicationContext, MobDatabase::class.java, "mob.db")
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
