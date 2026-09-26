package com.mobuk.app.ai.agent

import com.mobuk.app.ai.JsonExtract.int
import com.mobuk.app.ai.JsonExtract.string
import com.mobuk.app.ai.JsonExtract.strings
import com.mobuk.app.ai.features.Recommender
import com.mobuk.app.ai.features.WeekPlanner
import com.mobuk.app.data.local.PrefsStore
import com.mobuk.app.data.mcp.McpManager
import com.mobuk.app.data.mcp.McpTool
import com.mobuk.app.data.repository.CollectionRepository
import com.mobuk.app.data.repository.PlannerRepository
import com.mobuk.app.data.repository.RecipeRepository
import com.mobuk.app.data.repository.ShoppingRepository
import com.mobuk.app.domain.logic.QuantityFormatter
import com.mobuk.app.domain.logic.SearchFilters
import com.mobuk.app.domain.model.DietTag
import com.mobuk.app.domain.model.MealSlot
import com.mobuk.app.domain.model.PlannerEntry
import com.mobuk.app.domain.model.Recipe
import com.mobuk.app.domain.model.RecipeSummary
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Wires the app's repositories and any connected MCP servers into tools the on-device agent can call. */
class BuiltInTools(
    private val recipes: RecipeRepository,
    private val planner: PlannerRepository,
    private val shopping: ShoppingRepository,
    private val collections: CollectionRepository,
    private val prefs: PrefsStore,
    private val recommender: Recommender,
    private val weekPlanner: WeekPlanner,
    private val mcp: McpManager,
) {
    fun registry(): ToolRegistry = ToolRegistry().apply { registerAll(builtIn()) }

    /** Refreshes MCP tools (network) and registers them; safe to call repeatedly. */
    suspend fun registerMcpTools(registry: ToolRegistry) {
        val tools = runCatching { mcp.allTools() }.getOrDefault(emptyList())
        for (t in tools) registry.register(fromMcp(t))
    }

    private fun fromMcp(t: McpTool) = AgentTool(
        name = t.qualifiedName,
        description = "[${t.serverName}] ${t.description.take(160)}",
        parameters = McpManager.describeSchema(t.inputSchema, t.required),
        invoke = { args ->
            val result = mcp.callTool(t, McpManager.jsonToArguments(args))
            ToolOutput(result.text.take(4000), isError = result.isError)
        },
    )

    private fun renderList(list: List<Recipe>): ToolOutput {
        if (list.isEmpty()) return ToolOutput("No recipes found.")
        val text = list.take(8).joinToString("\n") { r ->
            buildString {
                append(r.id).append(" | ").append(r.title)
                r.cuisine?.let { append(" | ").append(it) }
                r.effectiveMinutes?.let { append(" | ").append(it).append(" min") }
                r.nutrition?.calories?.let { append(" | ").append(it.toInt()).append(" kcal") }
                if (r.dietTags.isNotEmpty()) append(" | ").append(r.dietTags.take(4).joinToString(",") { it.label })
            }
        }
        return ToolOutput(text, recipeIds = list.take(8).map { it.id })
    }

    private fun dietFromText(text: String?): Set<DietTag> = when (text?.lowercase()?.trim()) {
        null, "" -> emptySet()
        "vegan" -> setOf(DietTag.VEGAN)
        "vegetarian", "veggie" -> setOf(DietTag.VEGETARIAN)
        "pescatarian" -> setOf(DietTag.PESCATARIAN)
        "gluten free", "gluten-free", "gf" -> setOf(DietTag.GLUTEN_FREE)
        "dairy free", "dairy-free" -> setOf(DietTag.DAIRY_FREE)
        "high protein", "high-protein" -> setOf(DietTag.HIGH_PROTEIN)
        "low calorie", "lighter", "light" -> setOf(DietTag.LOW_CALORIE)
        else -> DietTag.entries.filter { text.contains(it.label, true) || text.contains(it.name.replace('_', ' '), true) }.toSet()
    }

    private fun builtIn(): List<AgentTool> = listOf(
        AgentTool(
            name = "search_recipes",
            description = "Search real recipes by keywords with optional diet, cuisine and max cooking time.",
            parameters = "query: string, diet: string (vegan|vegetarian|pescatarian|gluten free|dairy free|high protein), cuisine: string, max_minutes: int",
            invoke = { args ->
                val filters = SearchFilters(
                    query = args.string("query").orEmpty(),
                    diets = dietFromText(args.string("diet")),
                    cuisines = setOfNotNull(args.string("cuisine")),
                    maxMinutes = args.int("max_minutes"),
                )
                val userPrefs = prefs.prefs.first()
                val results = recipes.search(if (filters.isEmpty) filters.copy(query = "dinner") else filters, 20)
                    .filter { recommender.passesHardConstraints(it, userPrefs) }
                renderList(results)
            },
        ),
        AgentTool(
            name = "fridge_search",
            description = "Find recipes that use ingredients the user already has.",
            parameters = "ingredients: comma separated string (required)",
            invoke = { args ->
                val ings = (args.strings("ingredients").flatMap { it.split(',') }).map { it.trim() }.filter { it.isNotBlank() }
                if (ings.isEmpty()) ToolOutput("ingredients is required", isError = true)
                else renderList(recipes.search(SearchFilters(ingredients = ings), 20))
            },
        ),
        AgentTool(
            name = "get_recipe",
            description = "Get a recipe's ingredients, method and nutrition by id.",
            parameters = "id: string (required)",
            invoke = { args ->
                val id = args.string("id") ?: return@AgentTool ToolOutput("id is required", isError = true)
                val r = recipes.get(id) ?: return@AgentTool ToolOutput("Recipe not found", isError = true)
                ToolOutput(com.mobuk.app.ai.Prompts.recipeBrief(r, maxIngredients = 20, maxSteps = 10), recipeIds = listOf(r.id))
            },
        ),
        AgentTool(
            name = "recommend",
            description = "Personalised recipe picks for this user right now.",
            parameters = "context: string (e.g. 'quick dinner tonight')",
            invoke = { args ->
                val userPrefs = prefs.prefs.first()
                val (cuisines, categories) = recipes.interactionStats()
                val history = Recommender.History(recentlyCookedTitles = recipes.recentlyCookedTitles(), savedCuisines = cuisines, savedCategories = categories)
                val recs = recommender.recommend(recipes.candidatePool(), userPrefs, history, args.string("context"), 6)
                if (recs.isEmpty()) ToolOutput("No recommendations available yet.")
                else ToolOutput(recs.joinToString("\n") { "${it.recipe.id} | ${it.recipe.title} | ${it.reason}" }, recs.map { it.recipe.id })
            },
        ),
        AgentTool(
            name = "save_recipe",
            description = "Save a recipe to the user's Saved list.",
            parameters = "id: string (required)",
            invoke = { args ->
                val id = args.string("id") ?: return@AgentTool ToolOutput("id is required", isError = true)
                recipes.setSaved(id, true)
                ToolOutput("Saved ${recipes.cached(id)?.title ?: id}.", listOf(id))
            },
        ),
        AgentTool(
            name = "add_to_planner",
            description = "Add a recipe to the meal planner.",
            parameters = "id: string (required), day: string (today|tomorrow|monday..sunday|YYYY-MM-DD), slot: string (breakfast|lunch|dinner|snack), servings: int",
            invoke = { args ->
                val id = args.string("id") ?: return@AgentTool ToolOutput("id is required", isError = true)
                val recipe = recipes.get(id) ?: return@AgentTool ToolOutput("Recipe not found", isError = true)
                val date = parseDay(args.string("day"))
                val slot = args.string("slot")?.let { s -> MealSlot.entries.firstOrNull { it.name.equals(s, true) } } ?: MealSlot.DINNER
                val servings = args.int("servings") ?: prefs.prefs.first().householdSize
                planner.add(date, slot, recipe.id, servings)
                ToolOutput("Added ${recipe.title} to ${slot.label.lowercase()} on ${date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.UK)} $date.", listOf(recipe.id))
            },
        ),
        AgentTool(
            name = "plan_week",
            description = "Auto-plan the coming week's meals from real recipes and add them to the planner.",
            parameters = "days: int (default 7), slots: string (comma separated, default dinner)",
            invoke = { args ->
                val userPrefs = prefs.prefs.first()
                val days = (args.int("days") ?: 7).coerceIn(1, 14)
                val slots = args.string("slots")?.split(',')?.mapNotNull { s -> MealSlot.entries.firstOrNull { it.name.equals(s.trim(), true) } }?.ifEmpty { null } ?: listOf(MealSlot.DINNER)
                val (cuisines, categories) = recipes.interactionStats()
                val history = Recommender.History(recentlyCookedTitles = recipes.recentlyCookedTitles(), savedCuisines = cuisines, savedCategories = categories)
                val pool = recipes.candidatePool(minimum = days * slots.size * 3)
                val plan = weekPlanner.plan(pool, userPrefs, days, slots, history)
                val start = LocalDate.now()
                planner.addMany(plan.slots.map { PlannerEntry(epochDay = start.plusDays(it.dayOffset.toLong()).toEpochDay(), slot = it.slot, recipeId = it.recipe.id, servings = userPrefs.householdSize) })
                ToolOutput(
                    "${plan.title}: " + plan.slots.joinToString("; ") { "${start.plusDays(it.dayOffset.toLong()).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.UK)} ${it.slot.label}: ${it.recipe.title}" },
                    plan.slots.map { it.recipe.id },
                )
            },
        ),
        AgentTool(
            name = "get_planner",
            description = "List what is planned for the next 7 days.",
            parameters = "",
            invoke = {
                val start = LocalDate.now()
                val entries = planner.allEntries().filter { it.epochDay in start.toEpochDay()..(start.toEpochDay() + 6) }
                if (entries.isEmpty()) ToolOutput("Nothing planned for the next 7 days.")
                else {
                    val rs = recipes.cachedMany(entries.map { it.recipeId }.distinct()).associateBy { it.id }
                    ToolOutput(
                        entries.sortedWith(compareBy({ it.epochDay }, { it.slot.order })).joinToString("\n") { e ->
                            "${LocalDate.ofEpochDay(e.epochDay).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.UK)} ${e.slot.label}: ${rs[e.recipeId]?.title ?: e.recipeId} (${e.servings} servings)"
                        },
                        entries.map { it.recipeId }.distinct(),
                    )
                }
            },
        ),
        AgentTool(
            name = "add_to_shopping_list",
            description = "Add items (or a whole recipe's ingredients) to the shopping list.",
            parameters = "items: comma separated string, recipe_id: string, servings: int",
            invoke = { args ->
                val added = mutableListOf<String>()
                args.string("recipe_id")?.let { id ->
                    recipes.get(id)?.let { r -> shopping.addRecipe(r, args.int("servings")); added += "ingredients for ${r.title}" }
                }
                args.strings("items").flatMap { it.split(',') }.map { it.trim() }.filter { it.isNotBlank() }.forEach { shopping.addCustom(it); added += it }
                if (added.isEmpty()) ToolOutput("Nothing to add: pass items or recipe_id", isError = true) else ToolOutput("Added ${added.joinToString()} to your shopping list.")
            },
        ),
        AgentTool(
            name = "get_shopping_list",
            description = "Show the current shopping list grouped by aisle.",
            parameters = "",
            invoke = {
                val items = shopping.items.first()
                if (items.isEmpty()) ToolOutput("Your shopping list is empty.")
                else ToolOutput(shopping.grouped(items).entries.joinToString("\n") { (aisle, list) ->
                    aisle.label + ": " + list.joinToString(", ") { i -> listOf(QuantityFormatter.formatWithUnit(i.quantity, i.unit), i.name).filter { it.isNotBlank() }.joinToString(" ") + (if (i.checked) " ✓" else "") }
                })
            },
        ),
        AgentTool(
            name = "list_saved",
            description = "List the user's saved recipes and collections.",
            parameters = "",
            invoke = {
                val saved: List<RecipeSummary> = recipes.savedRecipes.first()
                val cols = collections.collections.first()
                val text = buildString {
                    if (saved.isEmpty()) append("No saved recipes yet.") else append("Saved: ").append(saved.take(15).joinToString("; ") { "${it.id} | ${it.title}" })
                    if (cols.isNotEmpty()) append("\nCollections: ").append(cols.joinToString { "${it.name} (${it.recipeCount})" })
                }
                ToolOutput(text, saved.take(15).map { it.id })
            },
        ),
        AgentTool(
            name = "get_preferences",
            description = "Read the user's dietary preferences, allergies and goals.",
            parameters = "",
            invoke = { ToolOutput(com.mobuk.app.ai.Prompts.describePrefs(prefs.prefs.first())) },
        ),
    )

    private fun parseDay(text: String?): LocalDate {
        val today = LocalDate.now()
        val t = text?.trim()?.lowercase() ?: return today
        if (t.isBlank() || t == "today" || t == "tonight") return today
        if (t == "tomorrow") return today.plusDays(1)
        runCatching { LocalDate.parse(t) }.getOrNull()?.let { return it }
        val dow = java.time.DayOfWeek.entries.firstOrNull { it.getDisplayName(TextStyle.FULL, Locale.UK).lowercase().startsWith(t.take(3)) }
        if (dow != null) {
            var d = today
            while (d.dayOfWeek != dow) d = d.plusDays(1)
            return d
        }
        return today
    }
}
