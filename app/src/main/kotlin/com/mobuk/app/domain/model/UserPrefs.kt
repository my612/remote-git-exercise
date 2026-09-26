package com.mobuk.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class UnitSystem { METRIC, IMPERIAL }

@Serializable
enum class CookingGoal(val label: String) {
    EAT_HEALTHIER("Eat healthier"),
    SAVE_MONEY("Save money"),
    SAVE_TIME("Save time"),
    LEARN_TO_COOK("Learn to cook"),
    HIGH_PROTEIN("Hit protein goals"),
    COOK_FOR_FAMILY("Feed the family"),
}

/** Which on-device model the user prefers. AUTO tries Gemini Nano first, then Gemma, then rules. */
@Serializable
enum class EnginePreference(val label: String) {
    AUTO("Automatic"),
    GEMINI_NANO("Gemini Nano (AICore)"),
    GEMMA_LITERT("Gemma (LiteRT-LM)"),
    RULES_ONLY("No AI, rules only"),
}

@Serializable
data class UserPrefs(
    val onboardingDone: Boolean = false,
    val displayName: String = "",
    val diets: Set<DietTag> = emptySet(),
    val allergies: Set<String> = emptySet(),
    val dislikedIngredients: Set<String> = emptySet(),
    val favouriteCuisines: Set<String> = emptySet(),
    val goals: Set<CookingGoal> = emptySet(),
    val householdSize: Int = 2,
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    val spoonacularApiKey: String = "",
    val enginePreference: EnginePreference = EnginePreference.AUTO,
    /** Direct download URL of a .litertlm Gemma model file, see Settings. */
    val gemmaModelUrl: String = DEFAULT_GEMMA_URL,
    /** Optional Hugging Face token used as a bearer token for gated model downloads. */
    val huggingFaceToken: String = "",
    val keepScreenOnInCookMode: Boolean = true,
    val weekStartsOnMonday: Boolean = true,
) {
    companion object {
        const val DEFAULT_GEMMA_URL =
            "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.litertlm"
    }
}
