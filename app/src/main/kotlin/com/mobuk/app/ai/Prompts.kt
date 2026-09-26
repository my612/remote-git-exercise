package com.mobuk.app.ai

import com.mobuk.app.domain.model.Recipe
import com.mobuk.app.domain.model.RecipeSummary
import com.mobuk.app.domain.model.UserPrefs

/** Prompt templates. Kept short: on-device models have small context windows and slow prefill. */
object Prompts {

    const val PERSONA = "You are Mob, a friendly UK home-cooking assistant inside a recipe app. Be concise and practical. " +
        "Only recommend recipes that exist in the app's data; never invent recipe ids."

    fun describePrefs(prefs: UserPrefs): String = buildString {
        if (prefs.diets.isNotEmpty()) append("Diet: ").append(prefs.diets.joinToString { it.label }).append(". ")
        if (prefs.allergies.isNotEmpty()) append("Allergies (must avoid): ").append(prefs.allergies.joinToString()).append(". ")
        if (prefs.dislikedIngredients.isNotEmpty()) append("Dislikes: ").append(prefs.dislikedIngredients.joinToString()).append(". ")
        if (prefs.favouriteCuisines.isNotEmpty()) append("Favourite cuisines: ").append(prefs.favouriteCuisines.joinToString()).append(". ")
        if (prefs.goals.isNotEmpty()) append("Goals: ").append(prefs.goals.joinToString { it.label }).append(". ")
        append("Household size: ").append(prefs.householdSize).append(".")
    }

    fun summaryLine(s: RecipeSummary): String = buildString {
        append(s.id).append(" | ").append(s.title)
        s.cuisine?.let { append(" | ").append(it) }
        s.category?.let { append(" | ").append(it) }
        s.totalMinutes?.let { append(" | ").append(it).append(" min") }
        s.calories?.let { append(" | ").append(it.toInt()).append(" kcal") }
        if (s.dietTags.isNotEmpty()) append(" | ").append(s.dietTags.joinToString(",") { it.label })
    }

    fun recipeBrief(r: Recipe, maxIngredients: Int = 25, maxSteps: Int = 15): String = buildString {
        append("Title: ").append(r.title).append('\n')
        r.servings?.let { append("Serves: ").append(it).append('\n') }
        r.effectiveMinutes?.let { append("Time: ").append(it).append(" min\n") }
        append("Ingredients:\n")
        r.ingredients.take(maxIngredients).forEach { append("- ").append(it.raw).append('\n') }
        if (r.ingredients.size > maxIngredients) append("- (").append(r.ingredients.size - maxIngredients).append(" more)\n")
        append("Method:\n")
        r.steps.take(maxSteps).forEachIndexed { i, s -> append(i + 1).append(". ").append(s.text.take(300)).append('\n') }
    }

    fun recommend(prefs: UserPrefs, candidates: List<RecipeSummary>, recentTitles: List<String>, slotHint: String?): LlmRequest = LlmRequest(
        systemPrompt = "$PERSONA You pick recipes for a personalised feed.",
        userPrompt = buildString {
            append("User: ").append(describePrefs(prefs)).append('\n')
            if (recentTitles.isNotEmpty()) append("Recently cooked or viewed: ").append(recentTitles.take(8).joinToString("; ")).append('\n')
            slotHint?.let { append("Context: ").append(it).append('\n') }
            append("Candidates (id | title | cuisine | category | time | kcal | tags):\n")
            candidates.forEach { append(summaryLine(it)).append('\n') }
            append("Choose the 8 best for this user. Prefer variety across cuisines and avoid repeats of recent meals. ")
            append("Return JSON: {\"picks\":[{\"id\":\"<id>\",\"reason\":\"<max 12 words, second person>\"}]}")
        },
        maxOutputTokens = 400,
        temperature = 0.4f,
        jsonMode = true,
    )

    fun adapt(recipe: Recipe, instruction: String, targetServings: Int?): LlmRequest = LlmRequest(
        systemPrompt = "$PERSONA You rewrite recipes precisely. Keep quantities realistic and UK units (g, ml, tbsp, tsp).",
        userPrompt = buildString {
            append(recipeBrief(recipe)).append('\n')
            append("Adaptation requested: ").append(instruction).append('\n')
            targetServings?.let { append("Target servings: ").append(it).append('\n') }
            append("Rewrite the whole recipe applying the adaptation. Return JSON: ")
            append("{\"title\":\"...\",\"servings\":n,\"ingredients\":[\"2 tbsp olive oil\", ...],\"steps\":[\"...\", ...],\"notes\":\"what changed and why\"}")
        },
        maxOutputTokens = 1200,
        temperature = 0.3f,
        jsonMode = true,
    )

    fun estimateNutrition(recipe: Recipe): LlmRequest = LlmRequest(
        systemPrompt = "$PERSONA You are a careful nutritionist estimating per-serving macros from an ingredient list.",
        userPrompt = buildString {
            append(recipeBrief(recipe, maxSteps = 0)).append('\n')
            append("Estimate nutrition PER SERVING (assume ").append(recipe.servings ?: 4).append(" servings). ")
            append("Return JSON: {\"calories\":n,\"protein_g\":n,\"carbs_g\":n,\"fat_g\":n,\"fibre_g\":n,\"sugar_g\":n}")
        },
        maxOutputTokens = 150,
        temperature = 0.1f,
        jsonMode = true,
    )

    fun planWeek(prefs: UserPrefs, candidates: List<RecipeSummary>, days: Int, slots: List<String>): LlmRequest = LlmRequest(
        systemPrompt = "$PERSONA You build realistic weekly meal plans with variety and sensible effort on weeknights.",
        userPrompt = buildString {
            append("User: ").append(describePrefs(prefs)).append('\n')
            append("Plan ").append(days).append(" days, slots: ").append(slots.joinToString()).append(".\n")
            append("Candidates (id | title | cuisine | category | time | kcal | tags):\n")
            candidates.forEach { append(summaryLine(it)).append('\n') }
            append("Use each recipe at most once, keep weeknight dinners under 45 minutes where possible, and vary cuisines. ")
            append("Return JSON: {\"title\":\"<fun plan name>\",\"days\":[{\"day\":0,\"slot\":\"DINNER\",\"id\":\"<id>\"}, ...]}")
        },
        maxOutputTokens = 700,
        temperature = 0.5f,
        jsonMode = true,
    )

    fun agentSystem(toolsBlock: String, prefs: UserPrefs): String = buildString {
        append(PERSONA).append('\n')
        append("User profile: ").append(describePrefs(prefs)).append('\n')
        append("You can call tools. To call one, reply with ONLY this JSON: {\"tool\":\"<name>\",\"args\":{...}}\n")
        append("When you have enough information, reply with ONLY: {\"final\":\"<answer in plain text, max 120 words>\",\"recipe_ids\":[\"<ids you mention>\"]}\n")
        append("Tools:\n").append(toolsBlock)
    }
}
