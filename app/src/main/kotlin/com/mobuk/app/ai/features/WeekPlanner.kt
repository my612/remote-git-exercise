package com.mobuk.app.ai.features

import com.mobuk.app.ai.JsonExtract
import com.mobuk.app.ai.JsonExtract.int
import com.mobuk.app.ai.JsonExtract.string
import com.mobuk.app.ai.LlmRouter
import com.mobuk.app.ai.Prompts
import com.mobuk.app.domain.model.DietTag
import com.mobuk.app.domain.model.MealSlot
import com.mobuk.app.domain.model.MealType
import com.mobuk.app.domain.model.Recipe
import com.mobuk.app.domain.model.RecipeSummary
import com.mobuk.app.domain.model.UserPrefs
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

data class PlannedSlot(val dayOffset: Int, val slot: MealSlot, val recipe: Recipe)

data class WeekPlan(val title: String, val slots: List<PlannedSlot>, val usedModel: Boolean)

/** Builds a week of meals from real recipes, with rule-based variety and an optional model pass for taste. */
class WeekPlanner(private val router: LlmRouter, private val recommender: Recommender) {

    suspend fun plan(candidates: List<Recipe>, prefs: UserPrefs, days: Int, slots: List<MealSlot>, history: Recommender.History): WeekPlan {
        val pool = candidates.filter { recommender.passesHardConstraints(it, prefs) && it.title.isNotBlank() }.distinctBy { it.id }
        val rules = rulePlan(pool, prefs, days, slots, history)
        if (pool.size < days * slots.size + 3) return rules
        val shortlist = pool.sortedByDescending { recommender.ruleScore(it, prefs, history).first }.take(28)
        val response = router.generateOrNull(Prompts.planWeek(prefs, shortlist.map { RecipeSummary.from(it) }, days, slots.map { it.name })) ?: return rules
        val json = JsonExtract.firstObject(response) ?: return rules
        val byId = shortlist.associateBy { it.id }
        val used = mutableSetOf<String>()
        val picked = (json["days"] as? JsonArray).orEmpty().mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val id = o.string("id") ?: return@mapNotNull null
            val day = o.int("day") ?: return@mapNotNull null
            val slot = o.string("slot")?.let { s -> MealSlot.entries.firstOrNull { it.name.equals(s, true) } } ?: slots.first()
            val recipe = byId[id] ?: return@mapNotNull null
            if (day !in 0 until days || slot !in slots || !used.add(id)) return@mapNotNull null
            PlannedSlot(day, slot, recipe)
        }
        if (picked.size < (days * slots.size) / 2) return rules
        // Fill any gaps the model left with the rule plan.
        val filled = picked.toMutableList()
        for (r in rules.slots) {
            if (filled.none { it.dayOffset == r.dayOffset && it.slot == r.slot } && filled.none { it.recipe.id == r.recipe.id }) filled += r
        }
        return WeekPlan(json.string("title") ?: rules.title, filled.sortedWith(compareBy({ it.dayOffset }, { it.slot.order })), usedModel = true)
    }

    fun rulePlan(pool: List<Recipe>, prefs: UserPrefs, days: Int, slots: List<MealSlot>, history: Recommender.History): WeekPlan {
        val scored = pool.map { it to recommender.ruleScore(it, prefs, history).first }.sortedByDescending { it.second }.map { it.first }
        val used = mutableSetOf<String>()
        val recentCuisines = ArrayDeque<String>()
        val result = mutableListOf<PlannedSlot>()
        for (day in 0 until days) {
            for (slot in slots) {
                val weeknight = day in 0..4
                val pick = scored.firstOrNull { r ->
                    r.id !in used && suits(r, slot) &&
                        (!weeknight || slot != MealSlot.DINNER || (r.effectiveMinutes ?: 40) <= 45) &&
                        (r.cuisine == null || r.cuisine !in recentCuisines)
                } ?: scored.firstOrNull { r -> r.id !in used && suits(r, slot) } ?: scored.firstOrNull { it.id !in used } ?: continue
                used += pick.id
                pick.cuisine?.let { recentCuisines.addLast(it); if (recentCuisines.size > 2) recentCuisines.removeFirst() }
                result += PlannedSlot(day, slot, pick)
            }
        }
        val title = when {
            DietTag.VEGAN in prefs.diets -> "The Plant-Powered Week"
            prefs.goals.any { it == com.mobuk.app.domain.model.CookingGoal.HIGH_PROTEIN } -> "The High-Protein Week"
            prefs.goals.any { it == com.mobuk.app.domain.model.CookingGoal.SAVE_TIME } -> "The Speedy Week"
            else -> "Your Week Sorted"
        }
        return WeekPlan(title, result, usedModel = false)
    }

    private fun suits(recipe: Recipe, slot: MealSlot): Boolean = when (slot) {
        MealSlot.BREAKFAST -> MealType.BREAKFAST in recipe.mealTypes
        MealSlot.LUNCH -> recipe.mealTypes.any { it == MealType.LUNCH || it == MealType.DINNER || it == MealType.SIDE } && MealType.DESSERT !in recipe.mealTypes
        MealSlot.DINNER -> (MealType.DINNER in recipe.mealTypes || recipe.mealTypes.isEmpty()) && MealType.DESSERT !in recipe.mealTypes && MealType.DRINK !in recipe.mealTypes
        MealSlot.SNACK -> recipe.mealTypes.any { it == MealType.SNACK || it == MealType.DESSERT || it == MealType.SIDE }
    }
}
