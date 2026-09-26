package com.mobuk.app.ai.features

import com.mobuk.app.ai.JsonExtract
import com.mobuk.app.ai.JsonExtract.double
import com.mobuk.app.ai.LlmRouter
import com.mobuk.app.ai.Prompts
import com.mobuk.app.domain.model.Nutrition
import com.mobuk.app.domain.model.Recipe

/** Estimates macros with the on-device model when the source has none. Always flagged as an estimate. */
class NutritionEstimator(private val router: LlmRouter) {
    suspend fun estimate(recipe: Recipe): Nutrition? {
        if (recipe.ingredients.isEmpty()) return null
        val response = router.generateOrNull(Prompts.estimateNutrition(recipe)) ?: return null
        val json = JsonExtract.firstObject(response) ?: return null
        val calories = json.double("calories") ?: json.double("kcal") ?: return null
        if (calories <= 0 || calories > 5000) return null
        return Nutrition(
            calories = calories,
            proteinG = json.double("protein_g") ?: json.double("protein"),
            carbsG = json.double("carbs_g") ?: json.double("carbohydrates"),
            fatG = json.double("fat_g") ?: json.double("fat"),
            fibreG = json.double("fibre_g") ?: json.double("fiber_g"),
            sugarG = json.double("sugar_g"),
            perServing = true,
            isEstimate = true,
        )
    }
}
