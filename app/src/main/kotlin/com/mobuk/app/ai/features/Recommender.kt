package com.mobuk.app.ai.features

import com.mobuk.app.ai.JsonExtract
import com.mobuk.app.ai.JsonExtract.string
import com.mobuk.app.ai.LlmRouter
import com.mobuk.app.ai.Prompts
import com.mobuk.app.domain.logic.DietTagger
import com.mobuk.app.domain.model.CookingGoal
import com.mobuk.app.domain.model.DietTag
import com.mobuk.app.domain.model.Recipe
import com.mobuk.app.domain.model.RecipeSummary
import com.mobuk.app.domain.model.UserPrefs
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import java.time.LocalTime

data class Recommendation(
    val recipe: RecipeSummary,
    val reason: String,
    val score: Double,
)

/**
 * "For You" ranking. Hard constraints (diet, allergies, dislikes) are always applied by rules; the on-device
 * model then re-ranks the shortlist and writes a one-line reason. Without a model the rule score is used.
 */
class Recommender(private val router: LlmRouter) {

    data class History(
        val recentlyCookedTitles: List<String> = emptyList(),
        val recentlyViewedTitles: List<String> = emptyList(),
        val savedCuisines: Map<String, Int> = emptyMap(),
        val savedCategories: Map<String, Int> = emptyMap(),
    )

    fun passesHardConstraints(recipe: Recipe, prefs: UserPrefs): Boolean {
        if (prefs.diets.isNotEmpty() && recipe.ingredients.isNotEmpty() && !recipe.dietTags.containsAll(prefs.diets - setOf(DietTag.QUICK, DietTag.HIGH_PROTEIN, DietTag.LOW_CALORIE))) return false
        val names = recipe.ingredients.map { (it.name + " " + it.raw).lowercase() }
        if (prefs.allergies.any { a -> names.any { it.contains(a.lowercase()) } }) return false
        if (prefs.dislikedIngredients.any { d -> names.any { it.contains(d.lowercase()) } }) return false
        return true
    }

    fun ruleScore(recipe: Recipe, prefs: UserPrefs, history: History): Pair<Double, String> {
        var score = 1.0
        val reasons = mutableListOf<Pair<Double, String>>()
        recipe.cuisine?.let { c ->
            if (prefs.favouriteCuisines.any { it.equals(c, true) }) { score += 2.0; reasons += 2.0 to "You love $c food" }
            history.savedCuisines[c]?.let { n -> score += 0.4 * n.coerceAtMost(5); if (n >= 2) reasons += 1.0 to "You've saved lots of $c recipes" }
        }
        recipe.category?.let { c -> history.savedCategories[c]?.let { n -> score += 0.3 * n.coerceAtMost(5) } }
        val minutes = recipe.effectiveMinutes
        if (CookingGoal.SAVE_TIME in prefs.goals && minutes != null && minutes <= 30) { score += 1.5; reasons += 1.5 to "Ready in $minutes minutes" }
        if (CookingGoal.HIGH_PROTEIN in prefs.goals && DietTag.HIGH_PROTEIN in recipe.dietTags) { score += 1.5; reasons += 1.5 to "${recipe.nutrition?.proteinG?.toInt() ?: 25}g+ protein per serving" }
        if (CookingGoal.EAT_HEALTHIER in prefs.goals && DietTag.LOW_CALORIE in recipe.dietTags) { score += 1.2; reasons += 1.2 to "A lighter option" }
        if (CookingGoal.COOK_FOR_FAMILY in prefs.goals && (recipe.servings ?: 0) >= 4) { score += 0.8; reasons += 0.8 to "Feeds the whole family" }
        if (CookingGoal.SAVE_MONEY in prefs.goals && recipe.ingredients.size in 1..8) { score += 0.8; reasons += 0.8 to "Only ${recipe.ingredients.size} ingredients" }
        if (CookingGoal.LEARN_TO_COOK in prefs.goals && recipe.videoUrl != null) { score += 0.8; reasons += 0.8 to "Has a step-by-step video" }
        if (recipe.title in history.recentlyCookedTitles) score -= 3.0
        if (recipe.title in history.recentlyViewedTitles) score -= 0.5
        if (recipe.nutrition != null) score += 0.3
        if (recipe.imageUrl != null) score += 0.2
        val hour = LocalTime.now().hour
        val breakfasty = recipe.mealTypes.any { it == com.mobuk.app.domain.model.MealType.BREAKFAST }
        if (hour in 5..10 && breakfasty) { score += 1.0; reasons += 1.0 to "Perfect for this morning" }
        if (hour !in 5..10 && breakfasty && recipe.mealTypes.size == 1) score -= 1.0
        val reason = reasons.maxByOrNull { it.first }?.second ?: defaultReason(recipe)
        return score to reason
    }

    private fun defaultReason(recipe: Recipe): String = when {
        DietTag.QUICK in recipe.dietTags -> "On the table in ${recipe.effectiveMinutes} minutes"
        recipe.cuisine != null -> "A ${recipe.cuisine} favourite"
        recipe.category != null -> "Great ${recipe.category.lowercase()} option"
        else -> "Popular with the Mob"
    }

    suspend fun recommend(candidates: List<Recipe>, prefs: UserPrefs, history: History, slotHint: String? = null, limit: Int = 8): List<Recommendation> {
        val filtered = candidates.filter { passesHardConstraints(it, prefs) }.distinctBy { it.id }
        val scored = filtered.map { r -> val (s, why) = ruleScore(r, prefs, history); Recommendation(RecipeSummary.from(r), why, s) }
            .sortedByDescending { it.score }
        val shortlist = scored.take(20)
        if (shortlist.size <= 3) return shortlist.take(limit)

        val response = router.generateOrNull(
            Prompts.recommend(prefs, shortlist.map { it.recipe }, history.recentlyCookedTitles + history.recentlyViewedTitles, slotHint),
        ) ?: return shortlist.take(limit)
        val picks = (JsonExtract.firstObject(response)?.get("picks") as? JsonArray)
            ?.mapNotNull { it as? JsonObject }
            ?.mapNotNull { p -> val id = p.string("id") ?: return@mapNotNull null; id to (p.string("reason") ?: "") }
            .orEmpty()
        if (picks.isEmpty()) return shortlist.take(limit)
        val byId = shortlist.associateBy { it.recipe.id }
        val ordered = picks.mapNotNull { (id, reason) -> byId[id]?.let { it.copy(reason = reason.ifBlank { it.reason }) } }
        val rest = shortlist.filter { it.recipe.id !in ordered.map { o -> o.recipe.id } }
        return (ordered + rest).take(limit)
    }

    companion object {
        /** Quick check used by list screens to grey out recipes clashing with allergies. */
        fun clashes(recipe: Recipe, prefs: UserPrefs): List<String> {
            val names = recipe.ingredients.map { (it.name + " " + it.raw).lowercase() }
            return (prefs.allergies + prefs.dislikedIngredients).filter { a -> names.any { it.contains(a.lowercase()) } }
        }

        fun matchesDiet(recipe: Recipe, diet: DietTag): Boolean = diet in recipe.dietTags ||
            (recipe.ingredients.isEmpty() && recipe.tags.any { it.contains(diet.label, true) }) ||
            DietTagger.tag(recipe.ingredients, recipe.nutrition, recipe.effectiveMinutes, recipe.tags).contains(diet)
    }
}
