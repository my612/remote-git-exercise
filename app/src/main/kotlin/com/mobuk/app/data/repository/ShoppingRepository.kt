package com.mobuk.app.data.repository

import com.mobuk.app.data.local.PlannerDao
import com.mobuk.app.data.local.RecipeDao
import com.mobuk.app.data.local.ShoppingDao
import com.mobuk.app.data.local.toDomain
import com.mobuk.app.data.local.toEntity
import com.mobuk.app.domain.logic.AisleClassifier
import com.mobuk.app.domain.logic.IngredientNormalizer
import com.mobuk.app.domain.logic.QuantityFormatter
import com.mobuk.app.domain.model.Aisle
import com.mobuk.app.domain.model.Recipe
import com.mobuk.app.domain.model.ShoppingItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

/**
 * The smart shopping list. Recipe items are derived from the planner (today onwards) and re-generated whenever
 * the planner changes; checked state and custom items are preserved across re-syncs.
 */
class ShoppingRepository(
    private val shoppingDao: ShoppingDao,
    private val plannerDao: PlannerDao,
    private val recipeDao: RecipeDao,
) {
    private val syncMutex = Mutex()

    val items: Flow<List<ShoppingItem>> = shoppingDao.observeAll().map { list -> list.map { it.toDomain() } }
    val uncheckedCount: Flow<Int> = shoppingDao.observeUncheckedCount()

    fun grouped(items: List<ShoppingItem>): Map<Aisle, List<ShoppingItem>> =
        items.groupBy { it.aisle }.toSortedMap(compareBy { it.ordinal })

    suspend fun addCustom(text: String) {
        val ing = IngredientNormalizer.fromLine(text)
        shoppingDao.insert(
            ShoppingItem(name = ing.name, quantity = ing.quantity, unit = ing.unit, aisle = AisleClassifier.classify(ing.name), isCustom = true, note = ing.note).toEntity(),
        )
    }

    suspend fun addRecipe(recipe: Recipe, servings: Int?) {
        val scale = servingsScale(recipe, servings)
        val existing = shoppingDao.all().map { it.toDomain() }
        val merged = IngredientNormalizer.toShoppingItems(recipe.id, recipe.ingredients, scale, existing)
        val newItems = merged.filter { it.id == 0L }
        val updated = merged.filter { it.id != 0L && existing.firstOrNull { e -> e.id == it.id } != it }
        updated.forEach { shoppingDao.update(it.toEntity()) }
        if (newItems.isNotEmpty()) shoppingDao.insertAll(newItems.map { it.toEntity() })
    }

    suspend fun setChecked(id: Long, checked: Boolean) = shoppingDao.setChecked(id, checked)
    suspend fun remove(id: Long) = shoppingDao.deleteById(id)
    suspend fun clearChecked() = shoppingDao.clearChecked()
    suspend fun clearAll() = shoppingDao.clearAll()
    suspend fun uncheckAll() = shoppingDao.uncheckAll()

    suspend fun rename(id: Long, name: String) {
        val item = shoppingDao.all().firstOrNull { it.id == id } ?: return
        shoppingDao.update(item.copy(name = name, aisle = AisleClassifier.classify(name).name))
    }

    /** Rebuilds recipe-derived items from planner entries dated today or later. */
    suspend fun syncFromPlanner() = syncMutex.withLock {
        val today = LocalDate.now().toEpochDay()
        val entries = plannerDao.all().filter { it.epochDay >= today && !it.cooked }
        val recipes = recipeDao.getByIds(entries.map { it.recipeId }.distinct()).associate { it.id to it.toDomain() }
        val current = shoppingDao.all().map { it.toDomain() }
        val customs = current.filter { it.isCustom }
        val checkedKeys = current.filter { it.checked && !it.isCustom }.map { IngredientNormalizer.normalizedKey(it.name) to it.unit }.toSet()
        var rebuilt: List<ShoppingItem> = emptyList()
        for (e in entries) {
            val recipe = recipes[e.recipeId] ?: continue
            rebuilt = IngredientNormalizer.toShoppingItems(recipe.id, recipe.ingredients, servingsScale(recipe, e.servings), rebuilt)
        }
        val withChecks = rebuilt.map { item ->
            if ((IngredientNormalizer.normalizedKey(item.name) to item.unit) in checkedKeys) item.copy(checked = true) else item
        }
        shoppingDao.replaceRecipeItems(withChecks.map { it.copy(id = 0).toEntity() })
        // Custom items are untouched by replaceRecipeItems (it only clears non-custom rows).
        customs.size
    }

    private fun servingsScale(recipe: Recipe, servings: Int?): Double {
        val base = recipe.servings?.takeIf { it > 0 } ?: return 1.0
        val target = servings?.takeIf { it > 0 } ?: base
        return target.toDouble() / base
    }

    fun shareText(items: List<ShoppingItem>): String = buildString {
        append("Shopping list\n")
        for ((aisle, list) in grouped(items.filter { !it.checked })) {
            append("\n").append(aisle.label).append('\n')
            list.forEach { i ->
                append(if (i.checked) "[x] " else "[ ] ")
                val qty = QuantityFormatter.formatWithUnit(i.quantity, i.unit)
                if (qty.isNotBlank()) append(qty).append(' ')
                append(i.name)
                i.note?.let { append(" (").append(it).append(')') }
                append('\n')
            }
        }
    }
}
