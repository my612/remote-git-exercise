package com.mobuk.app.di

import android.content.Context
import com.mobuk.app.ai.LlmRouter
import com.mobuk.app.ai.agent.BuiltInTools
import com.mobuk.app.ai.agent.RecipeAgent
import com.mobuk.app.ai.agent.ToolRegistry
import com.mobuk.app.ai.download.ModelDownloader
import com.mobuk.app.ai.engines.GeminiNanoEngine
import com.mobuk.app.ai.engines.LiteRtLmEngine
import com.mobuk.app.ai.features.NutritionEstimator
import com.mobuk.app.ai.features.RecipeAdapter
import com.mobuk.app.ai.features.Recommender
import com.mobuk.app.ai.features.WeekPlanner
import com.mobuk.app.data.local.MobDatabase
import com.mobuk.app.data.local.PrefsStore
import com.mobuk.app.data.mcp.McpManager
import com.mobuk.app.data.remote.HttpClientFactory
import com.mobuk.app.data.remote.RecipeProvider
import com.mobuk.app.data.remote.myplate.MyPlateProvider
import com.mobuk.app.data.remote.spoonacular.SpoonacularProvider
import com.mobuk.app.data.remote.themealdb.TheMealDbProvider
import com.mobuk.app.data.remote.webimport.RecipeUrlImporter
import com.mobuk.app.data.repository.ChatRepository
import com.mobuk.app.data.repository.CollectionRepository
import com.mobuk.app.data.repository.MealPlanRepository
import com.mobuk.app.data.repository.PlannerRepository
import com.mobuk.app.data.repository.RecipeRepository
import com.mobuk.app.data.repository.ShoppingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first

/**
 * Hand-rolled dependency graph. Everything is a lazy singleton, created on first use from the Application.
 * No DI framework keeps the build simple and avoids annotation processing.
 */
class AppGraph(private val context: Context) {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: MobDatabase by lazy { MobDatabase.build(context) }
    val prefs: PrefsStore by lazy { PrefsStore(context) }
    val httpClient by lazy { HttpClientFactory.create() }

    val providers: List<RecipeProvider> by lazy {
        listOf(
            TheMealDbProvider(httpClient),
            MyPlateProvider(httpClient),
            SpoonacularProvider(httpClient) { prefs.prefs.first().spoonacularApiKey },
        )
    }
    val importer by lazy { RecipeUrlImporter(httpClient) }

    val mcp: McpManager by lazy { McpManager(database.mcpServerDao()) }

    val recipes: RecipeRepository by lazy {
        RecipeRepository(database.recipeDao(), database.queryCacheDao(), database.searchHistoryDao(), providers, importer)
    }
    val shopping: ShoppingRepository by lazy { ShoppingRepository(database.shoppingDao(), database.plannerDao(), database.recipeDao()) }
    val planner: PlannerRepository by lazy { PlannerRepository(database.plannerDao(), database.recipeDao(), shopping) }
    val collections: CollectionRepository by lazy { CollectionRepository(database.collectionDao()) }
    val mealPlans: MealPlanRepository by lazy { MealPlanRepository(database.mealPlanDao(), recipes) }

    val modelDownloader: ModelDownloader by lazy { ModelDownloader(context, httpClient, prefs) }
    val llmRouter: LlmRouter by lazy {
        LlmRouter(
            engines = listOf(GeminiNanoEngine(), LiteRtLmEngine(context, modelDownloader)),
            prefs = prefs,
        )
    }
    val recommender by lazy { Recommender(llmRouter) }
    val recipeAdapter by lazy { RecipeAdapter(llmRouter) }
    val weekPlanner by lazy { WeekPlanner(llmRouter, recommender) }
    val nutritionEstimator by lazy { NutritionEstimator(llmRouter) }

    val builtInTools by lazy { BuiltInTools(recipes, planner, shopping, collections, prefs, recommender, weekPlanner, mcp) }
    val toolRegistry: ToolRegistry by lazy { builtInTools.registry() }
    val agent by lazy { RecipeAgent(llmRouter, toolRegistry) }
    val chat: ChatRepository by lazy { ChatRepository(database.chatDao(), prefs, agent, builtInTools, toolRegistry) }
}
