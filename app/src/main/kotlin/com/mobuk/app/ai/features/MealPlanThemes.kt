package com.mobuk.app.ai.features

import com.mobuk.app.domain.logic.SearchFilters
import com.mobuk.app.domain.model.DietTag
import com.mobuk.app.domain.model.MealType
import com.mobuk.app.domain.model.Recipe

/**
 * Curated plan themes in the spirit of Mob's "The Healthy Lunch One" etc. Each theme is a query plus a predicate,
 * so plans are always assembled from real recipes fetched live rather than authored content.
 */
data class MealPlanTheme(
    val id: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val tags: List<String>,
    val queries: List<String>,
    val filters: SearchFilters,
    val accept: (Recipe) -> Boolean,
    val size: Int = 7,
)

object MealPlanThemes {
    val all: List<MealPlanTheme> = listOf(
        MealPlanTheme(
            id = "high-protein", title = "The High-Protein One", subtitle = "7 dinners, 25g+ protein each",
            description = "Big flavour, big protein. Built for training weeks and hungry households.",
            tags = listOf("High protein", "Dinner"), queries = listOf("chicken", "beef", "salmon", "lentil", "egg", "turkey", "prawn"),
            filters = SearchFilters(diets = setOf(DietTag.HIGH_PROTEIN)),
            accept = { r -> (r.nutrition?.proteinG ?: 0.0) >= 25 || DietTag.HIGH_PROTEIN in r.dietTags || listOf("chicken", "beef", "salmon", "turkey", "prawn", "steak", "lentil").any { r.title.lowercase().contains(it) } },
        ),
        MealPlanTheme(
            id = "speedy", title = "The 30-Minute One", subtitle = "Weeknight dinners, all under half an hour",
            description = "Fast, fuss-free meals for the nights when cooking is the last thing you want to do.",
            tags = listOf("Quick", "Weeknight"), queries = listOf("stir fry", "pasta", "noodle", "wrap", "omelette", "tacos", "salad"),
            filters = SearchFilters(maxMinutes = 30),
            accept = { r -> (r.effectiveMinutes ?: 31) <= 30 || DietTag.QUICK in r.dietTags || listOf("stir fry", "stir-fry", "quick", "15-minute", "20-minute", "noodle", "wrap", "omelette").any { r.title.lowercase().contains(it) } },
        ),
        MealPlanTheme(
            id = "veggie", title = "The Veggie One", subtitle = "A week of meat-free dinners",
            description = "Seven vegetarian dinners that nobody will call a compromise.",
            tags = listOf("Vegetarian"), queries = listOf("vegetarian", "halloumi", "aubergine", "chickpea", "mushroom", "paneer", "tofu"),
            filters = SearchFilters(diets = setOf(DietTag.VEGETARIAN)),
            accept = { r -> DietTag.VEGETARIAN in r.dietTags },
        ),
        MealPlanTheme(
            id = "vegan", title = "The Plant-Based One", subtitle = "Fully vegan, fully delicious",
            description = "Dairy-free, egg-free, meat-free — and packed with flavour.",
            tags = listOf("Vegan"), queries = listOf("vegan", "tofu", "lentil", "chickpea", "jackfruit", "dal", "bean"),
            filters = SearchFilters(diets = setOf(DietTag.VEGAN)),
            accept = { r -> DietTag.VEGAN in r.dietTags },
        ),
        MealPlanTheme(
            id = "healthy-lunch", title = "The Healthy Lunch One", subtitle = "5 lunches under 500 calories",
            description = "Desk-friendly, fridge-friendly lunches that keep you going till dinner.",
            tags = listOf("Lunch", "Lighter"), queries = listOf("salad", "soup", "wrap", "bowl", "grain"),
            filters = SearchFilters(maxCalories = 500, mealTypes = setOf(MealType.LUNCH)),
            accept = { r -> (r.nutrition?.calories ?: 450.0) <= 500 && MealType.DESSERT !in r.mealTypes },
            size = 5,
        ),
        MealPlanTheme(
            id = "batch", title = "The Batch-Cook One", subtitle = "Cook Sunday, eat all week",
            description = "Stews, curries, chillis and bakes that get better with a day in the fridge.",
            tags = listOf("Batch cook", "Freezer"), queries = listOf("stew", "curry", "chilli", "lasagne", "casserole", "dal", "bolognese"),
            filters = SearchFilters(),
            accept = { r -> listOf("stew", "curry", "chilli", "chili", "lasagn", "casserole", "dal", "bolognese", "ragu", "soup", "tagine", "bake").any { r.title.lowercase().contains(it) } || (r.servings ?: 0) >= 6 },
        ),
        MealPlanTheme(
            id = "family", title = "The Family One", subtitle = "Crowd-pleasers that serve four or more",
            description = "Mild, hearty and popular with picky eaters.",
            tags = listOf("Family", "Serves 4+"), queries = listOf("pasta bake", "pie", "burger", "roast", "fish cakes", "fajitas", "meatballs"),
            filters = SearchFilters(),
            accept = { r -> (r.servings ?: 4) >= 4 && listOf("chilli", "spicy", "hot").none { r.title.lowercase().contains(it) } },
        ),
        MealPlanTheme(
            id = "budget", title = "The Budget One", subtitle = "Big flavour, small shop",
            description = "Store-cupboard dinners with short ingredient lists.",
            tags = listOf("Budget", "Store cupboard"), queries = listOf("bean", "rice", "egg", "potato", "pasta", "lentil", "soup"),
            filters = SearchFilters(),
            accept = { r -> r.ingredients.isEmpty() || r.ingredients.size <= 10 },
        ),
        MealPlanTheme(
            id = "world-tour", title = "The World Tour One", subtitle = "A different cuisine every night",
            description = "Seven nights, seven countries. Pack your appetite.",
            tags = listOf("Global", "Adventure"), queries = listOf("thai", "mexican", "italian", "indian", "japanese", "moroccan", "greek"),
            filters = SearchFilters(),
            accept = { r -> r.cuisine != null },
        ),
        MealPlanTheme(
            id = "brunch", title = "The Brunch One", subtitle = "Weekend mornings, sorted",
            description = "Pancakes, eggs, and everything worth getting out of bed for.",
            tags = listOf("Breakfast", "Weekend"), queries = listOf("pancake", "eggs", "french toast", "shakshuka", "granola"),
            filters = SearchFilters(mealTypes = setOf(MealType.BREAKFAST)),
            accept = { r -> MealType.BREAKFAST in r.mealTypes || listOf("pancake", "egg", "toast", "shakshuka", "granola", "porridge", "waffle").any { r.title.lowercase().contains(it) } },
            size = 5,
        ),
    )

    fun byId(id: String): MealPlanTheme? = all.firstOrNull { it.id == id }

    /** Picks up to [theme.size] recipes with cuisine/category variety. */
    fun assemble(theme: MealPlanTheme, candidates: List<Recipe>): List<Recipe> {
        val accepted = candidates.filter(theme.accept).distinctBy { it.id }.distinctBy { it.title.lowercase() }
        val chosen = mutableListOf<Recipe>()
        val seenCuisine = mutableMapOf<String, Int>()
        for (r in accepted.sortedByDescending { (if (it.imageUrl != null) 1 else 0) + (if (it.nutrition != null) 1 else 0) + (if (it.isComplete) 1 else 0) }) {
            val c = r.cuisine ?: "-"
            if (theme.id == "world-tour" && (seenCuisine[c] ?: 0) >= 1) continue
            if ((seenCuisine[c] ?: 0) >= 3) continue
            chosen += r
            seenCuisine[c] = (seenCuisine[c] ?: 0) + 1
            if (chosen.size >= theme.size) break
        }
        if (chosen.size < theme.size) chosen += accepted.filter { it !in chosen }.take(theme.size - chosen.size)
        return chosen
    }
}
