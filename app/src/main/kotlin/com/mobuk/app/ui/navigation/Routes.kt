package com.mobuk.app.ui.navigation

import android.net.Uri

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val SEARCH = "search"
    const val PLANNER = "planner"
    const val SHOPPING = "shopping"
    const val SAVED = "saved"
    const val ASSISTANT = "assistant"
    const val INSPIRATION = "inspiration"
    const val SETTINGS = "settings"
    const val MCP_SERVERS = "settings/mcp"
    const val AI_MODELS = "settings/ai"
    const val MEAL_PLANS = "mealplans"
    const val IMPORT = "import?url={url}"

    const val RECIPE = "recipe/{id}"
    const val COOK = "cook/{id}"
    const val COLLECTION = "collection/{id}"
    const val MEAL_PLAN = "mealplan/{id}"
    const val BROWSE = "browse/{kind}/{value}"

    fun recipe(id: String) = "recipe/${Uri.encode(id)}"
    fun cook(id: String) = "cook/${Uri.encode(id)}"
    fun collection(id: Long) = "collection/$id"
    fun mealPlan(id: String) = "mealplan/${Uri.encode(id)}"
    fun browse(kind: String, value: String) = "browse/$kind/${Uri.encode(value)}"
    fun import(url: String?) = if (url.isNullOrBlank()) "import?url=" else "import?url=${Uri.encode(url)}"
    fun searchWith(query: String) = "$SEARCH?q=${Uri.encode(query)}"

    val bottomTabs = listOf(HOME, SEARCH, PLANNER, SHOPPING, SAVED)
}
