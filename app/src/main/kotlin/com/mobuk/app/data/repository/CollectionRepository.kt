package com.mobuk.app.data.repository

import com.mobuk.app.data.local.CollectionDao
import com.mobuk.app.data.local.CollectionEntity
import com.mobuk.app.data.local.CollectionRecipeCrossRef
import com.mobuk.app.data.local.toDomain
import com.mobuk.app.domain.model.RecipeCollection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class CollectionRepository(private val dao: CollectionDao) {

    val collections: Flow<List<RecipeCollection>> = combine(dao.observeAll(), dao.observeCounts()) { list, counts ->
        val countById = counts.associate { it.collectionId to it.count }
        list.map { it.toDomain(countById[it.id] ?: 0) }
    }

    fun observe(id: Long): Flow<RecipeCollection?> = dao.observeById(id).map { it?.toDomain() }

    fun observeRecipeIds(id: Long): Flow<List<String>> = dao.observeRecipeIds(id)

    fun observeCollectionIdsForRecipe(recipeId: String): Flow<List<Long>> = dao.observeCollectionIdsForRecipe(recipeId)

    suspend fun create(name: String, description: String? = null): Long =
        dao.insert(CollectionEntity(name = name.trim(), description = description?.trim()?.ifBlank { null }, coverImageUrl = null, createdAt = System.currentTimeMillis()))

    suspend fun rename(id: Long, name: String, description: String?) {
        val existing = dao.getById(id) ?: return
        dao.update(existing.copy(name = name.trim(), description = description?.trim()?.ifBlank { null }))
    }

    suspend fun delete(id: Long) {
        dao.clearCollection(id)
        dao.delete(id)
    }

    suspend fun addRecipe(collectionId: Long, recipeId: String) {
        dao.addRecipe(CollectionRecipeCrossRef(collectionId, recipeId, System.currentTimeMillis()))
        refreshCover(collectionId)
    }

    suspend fun removeRecipe(collectionId: Long, recipeId: String) {
        dao.removeRecipe(collectionId, recipeId)
        refreshCover(collectionId)
    }

    suspend fun toggle(collectionId: Long, recipeId: String) {
        val ids = dao.recipeIds(collectionId)
        if (recipeId in ids) removeRecipe(collectionId, recipeId) else addRecipe(collectionId, recipeId)
    }

    suspend fun getOrCreate(name: String): Long = dao.getByName(name)?.id ?: create(name)

    private suspend fun refreshCover(collectionId: Long) {
        val existing = dao.getById(collectionId) ?: return
        dao.update(existing.copy(coverImageUrl = dao.latestCoverImage(collectionId)))
    }
}
