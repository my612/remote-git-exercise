package com.mobuk.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.mobuk.app.ui.assistant.AssistantScreen
import com.mobuk.app.ui.common.LocalAppGraph
import com.mobuk.app.ui.cook.CookModeScreen
import com.mobuk.app.ui.home.HomeScreen
import com.mobuk.app.ui.importer.ImportScreen
import com.mobuk.app.ui.inspiration.InspirationScreen
import com.mobuk.app.ui.mealplans.MealPlanDetailScreen
import com.mobuk.app.ui.mealplans.MealPlansScreen
import com.mobuk.app.ui.onboarding.OnboardingScreen
import com.mobuk.app.ui.planner.PlannerScreen
import com.mobuk.app.ui.recipe.RecipeDetailScreen
import com.mobuk.app.ui.saved.CollectionDetailScreen
import com.mobuk.app.ui.saved.SavedScreen
import com.mobuk.app.ui.search.BrowseScreen
import com.mobuk.app.ui.search.SearchScreen
import com.mobuk.app.ui.settings.AiModelsScreen
import com.mobuk.app.ui.settings.McpServersScreen
import com.mobuk.app.ui.settings.SettingsScreen
import com.mobuk.app.ui.shopping.ShoppingListScreen

private data class Tab(val route: String, val label: String, val selected: ImageVector, val unselected: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "Home", Icons.Filled.Home, Icons.Outlined.Home),
    Tab(Routes.SEARCH, "Search", Icons.Filled.Search, Icons.Outlined.Search),
    Tab(Routes.PLANNER, "Planner", Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth),
    Tab(Routes.SHOPPING, "Shopping", Icons.Filled.ShoppingCart, Icons.Outlined.ShoppingCart),
    Tab(Routes.SAVED, "Saved", Icons.Filled.Bookmark, Icons.Outlined.BookmarkBorder),
)

@Composable
fun MobNavHost(navController: NavHostController, startDestination: String, pendingImportUrl: String?) {
    val graph = LocalAppGraph.current
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route?.substringBefore('?')
    val showBar = currentRoute in Routes.bottomTabs
    val shoppingCount by graph.shopping.uncheckedCount.collectAsStateWithLifecycle(initialValue = 0)

    Scaffold(
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        val selected = currentRoute == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                if (tab.route == Routes.SHOPPING && shoppingCount > 0) {
                                    BadgedBox(badge = { Badge { Text(shoppingCount.coerceAtMost(99).toString()) } }) {
                                        Icon(if (selected) tab.selected else tab.unselected, contentDescription = tab.label)
                                    }
                                } else {
                                    Icon(if (selected) tab.selected else tab.unselected, contentDescription = tab.label)
                                }
                            },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(onDone = {
                    navController.navigate(Routes.HOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
                })
            }
            composable(Routes.HOME) {
                HomeScreen(
                    onOpenRecipe = { navController.navigate(Routes.recipe(it)) },
                    onOpenSearch = { navController.navigate(Routes.SEARCH) },
                    onBrowse = { kind, value -> navController.navigate(Routes.browse(kind, value)) },
                    onOpenPlanner = { navController.navigate(Routes.PLANNER) },
                    onOpenAssistant = { navController.navigate(Routes.ASSISTANT) },
                    onOpenInspiration = { navController.navigate(Routes.INSPIRATION) },
                    onOpenMealPlans = { navController.navigate(Routes.MEAL_PLANS) },
                    onOpenMealPlan = { navController.navigate(Routes.mealPlan(it)) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenImport = { navController.navigate(Routes.import(null)) },
                )
            }
            composable(
                route = "${Routes.SEARCH}?q={q}",
                arguments = listOf(navArgument("q") { type = NavType.StringType; defaultValue = "" }),
            ) { entry ->
                SearchScreen(
                    initialQuery = entry.arguments?.getString("q").orEmpty(),
                    onOpenRecipe = { navController.navigate(Routes.recipe(it)) },
                    onOpenImport = { navController.navigate(Routes.import(null)) },
                )
            }
            composable(Routes.SEARCH) {
                SearchScreen(initialQuery = "", onOpenRecipe = { navController.navigate(Routes.recipe(it)) }, onOpenImport = { navController.navigate(Routes.import(null)) })
            }
            composable(Routes.PLANNER) {
                PlannerScreen(
                    onOpenRecipe = { navController.navigate(Routes.recipe(it)) },
                    onOpenShopping = { navController.navigate(Routes.SHOPPING) },
                    onOpenSearch = { navController.navigate(Routes.SEARCH) },
                )
            }
            composable(Routes.SHOPPING) {
                ShoppingListScreen(onOpenRecipe = { navController.navigate(Routes.recipe(it)) }, onOpenPlanner = { navController.navigate(Routes.PLANNER) })
            }
            composable(Routes.SAVED) {
                SavedScreen(
                    onOpenRecipe = { navController.navigate(Routes.recipe(it)) },
                    onOpenCollection = { navController.navigate(Routes.collection(it)) },
                    onOpenSearch = { navController.navigate(Routes.SEARCH) },
                    onOpenImport = { navController.navigate(Routes.import(null)) },
                )
            }
            composable(Routes.ASSISTANT) {
                AssistantScreen(onBack = { navController.popBackStack() }, onOpenRecipe = { navController.navigate(Routes.recipe(it)) }, onOpenAiSettings = { navController.navigate(Routes.AI_MODELS) })
            }
            composable(Routes.INSPIRATION) {
                InspirationScreen(onBack = { navController.popBackStack() }, onOpenRecipe = { navController.navigate(Routes.recipe(it)) })
            }
            composable(Routes.MEAL_PLANS) {
                MealPlansScreen(onBack = { navController.popBackStack() }, onOpenPlan = { navController.navigate(Routes.mealPlan(it)) })
            }
            composable(Routes.MEAL_PLAN, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                MealPlanDetailScreen(
                    planId = entry.arguments?.getString("id").orEmpty(),
                    onBack = { navController.popBackStack() },
                    onOpenRecipe = { navController.navigate(Routes.recipe(it)) },
                    onOpenPlanner = { navController.navigate(Routes.PLANNER) },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenMcp = { navController.navigate(Routes.MCP_SERVERS) },
                    onOpenAi = { navController.navigate(Routes.AI_MODELS) },
                    onRestartOnboarding = { navController.navigate(Routes.ONBOARDING) },
                )
            }
            composable(Routes.MCP_SERVERS) { McpServersScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.AI_MODELS) { AiModelsScreen(onBack = { navController.popBackStack() }) }
            composable(
                route = Routes.IMPORT,
                arguments = listOf(navArgument("url") { type = NavType.StringType; defaultValue = "" }),
            ) { entry ->
                val fromArgs = entry.arguments?.getString("url").orEmpty()
                ImportScreen(
                    initialUrl = fromArgs.ifBlank { pendingImportUrl.orEmpty() },
                    onBack = { navController.popBackStack() },
                    onOpenRecipe = { navController.navigate(Routes.recipe(it)) { popUpTo(Routes.IMPORT) { inclusive = true } } },
                )
            }
            composable(Routes.RECIPE, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                RecipeDetailScreen(
                    recipeId = entry.arguments?.getString("id").orEmpty(),
                    onBack = { navController.popBackStack() },
                    onStartCookMode = { navController.navigate(Routes.cook(it)) },
                    onOpenRecipe = { navController.navigate(Routes.recipe(it)) },
                    onOpenPlanner = { navController.navigate(Routes.PLANNER) },
                    onOpenShopping = { navController.navigate(Routes.SHOPPING) },
                    onBrowse = { kind, value -> navController.navigate(Routes.browse(kind, value)) },
                )
            }
            composable(Routes.COOK, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                CookModeScreen(recipeId = entry.arguments?.getString("id").orEmpty(), onExit = { navController.popBackStack() })
            }
            composable(Routes.COLLECTION, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                CollectionDetailScreen(
                    collectionId = entry.arguments?.getLong("id") ?: 0L,
                    onBack = { navController.popBackStack() },
                    onOpenRecipe = { navController.navigate(Routes.recipe(it)) },
                )
            }
            composable(
                Routes.BROWSE,
                arguments = listOf(navArgument("kind") { type = NavType.StringType }, navArgument("value") { type = NavType.StringType }),
            ) { entry ->
                BrowseScreen(
                    kind = entry.arguments?.getString("kind").orEmpty(),
                    value = entry.arguments?.getString("value").orEmpty(),
                    onBack = { navController.popBackStack() },
                    onOpenRecipe = { navController.navigate(Routes.recipe(it)) },
                )
            }
        }
    }
}
