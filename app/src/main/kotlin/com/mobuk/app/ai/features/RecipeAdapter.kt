package com.mobuk.app.ai.features

import com.mobuk.app.ai.JsonExtract
import com.mobuk.app.ai.JsonExtract.int
import com.mobuk.app.ai.JsonExtract.string
import com.mobuk.app.ai.JsonExtract.strings
import com.mobuk.app.ai.LlmRouter
import com.mobuk.app.ai.Prompts
import com.mobuk.app.domain.logic.DietTagger
import com.mobuk.app.domain.logic.IngredientNormalizer
import com.mobuk.app.domain.logic.QuantityFormatter
import com.mobuk.app.domain.logic.ServingScaler
import com.mobuk.app.domain.logic.TimerExtractor
import com.mobuk.app.domain.model.Ingredient
import com.mobuk.app.domain.model.Recipe
import com.mobuk.app.domain.model.RecipeSource
import com.mobuk.app.domain.model.RecipeStep
import java.util.UUID

enum class Adaptation(val label: String, val instruction: String) {
    VEGAN("Make it vegan", "Make this recipe fully vegan (no meat, fish, dairy, eggs or honey) with sensible swaps."),
    VEGETARIAN("Make it veggie", "Make this recipe vegetarian (no meat or fish) with sensible swaps."),
    GLUTEN_FREE("Gluten free", "Make this recipe gluten free, swapping wheat products and soy sauce."),
    DAIRY_FREE("Dairy free", "Make this recipe dairy free with plant-based swaps."),
    SPICIER("Spicier", "Make this recipe noticeably spicier."),
    MILDER("Milder", "Make this recipe mild and family friendly with no heat."),
    HIGHER_PROTEIN("More protein", "Increase the protein per serving with practical additions."),
    LOWER_CALORIE("Lighter", "Reduce calories and fat without losing flavour."),
    QUICKER("Quicker", "Reduce the cooking time with shortcuts and fewer steps."),
    BUDGET("Cheaper", "Use cheaper, supermarket-friendly ingredients."),
    SWAP("Swap an ingredient", "Swap an ingredient"),
    CUSTOM("Custom", "Custom"),
}

data class AdaptedRecipe(
    val recipe: Recipe,
    val notes: String,
    val usedModel: Boolean,
)

/** "Adapt Recipe": on-device model rewrite with a substitution-table fallback. */
class RecipeAdapter(private val router: LlmRouter) {

    suspend fun adapt(recipe: Recipe, adaptation: Adaptation, customInstruction: String? = null, targetServings: Int? = null): AdaptedRecipe {
        val instruction = when (adaptation) {
            Adaptation.SWAP, Adaptation.CUSTOM -> customInstruction?.takeIf { it.isNotBlank() } ?: adaptation.instruction
            else -> adaptation.instruction + (customInstruction?.takeIf { it.isNotBlank() }?.let { " Also: $it" } ?: "")
        }
        val response = router.generateOrNull(Prompts.adapt(recipe, instruction, targetServings))
        val json = response?.let { JsonExtract.firstObject(it) }
        if (json != null) {
            val ingredientLines = json.strings("ingredients")
            val stepLines = json.strings("steps")
            if (ingredientLines.isNotEmpty() && stepLines.isNotEmpty()) {
                val ingredients = ingredientLines.map { IngredientNormalizer.fromLine(it) }
                val steps = stepLines.mapIndexed { i, t -> RecipeStep(i, t, TimerExtractor.extractSeconds(t)) }
                val servings = json.int("servings") ?: targetServings ?: recipe.servings
                val adapted = newVariant(recipe, json.string("title") ?: "${recipe.title} (${adaptation.label.lowercase()})", ingredients, steps, servings)
                return AdaptedRecipe(adapted, json.string("notes") ?: "Adapted with on-device AI.", usedModel = true)
            }
        }
        return rulesAdapt(recipe, adaptation, customInstruction, targetServings)
    }

    fun rulesAdapt(recipe: Recipe, adaptation: Adaptation, customInstruction: String?, targetServings: Int?): AdaptedRecipe {
        val changes = mutableListOf<String>()
        var ingredients = recipe.ingredients
        var steps = recipe.steps
        val servings = targetServings ?: recipe.servings

        fun swapAll(table: List<Pair<List<String>, String>>) {
            ingredients = ingredients.map { ing ->
                val lower = (ing.name + " " + ing.raw).lowercase()
                val hit = table.firstOrNull { (keys, _) -> keys.any { lower.contains(it) } }
                if (hit == null) ing else {
                    changes += "${ing.name} → ${hit.second}"
                    val raw = listOfNotNull(QuantityFormatter.formatWithUnit(ing.quantity, ing.unit).takeIf { it.isNotBlank() }, hit.second).joinToString(" ")
                    ing.copy(name = hit.second, raw = raw, note = "swapped from ${ing.name}")
                }
            }
        }

        when (adaptation) {
            Adaptation.VEGAN -> { swapAll(Substitutions.meatToPlant + Substitutions.fishToPlant + Substitutions.dairyToPlant + Substitutions.eggToPlant + Substitutions.honey) }
            Adaptation.VEGETARIAN -> swapAll(Substitutions.meatToPlant + Substitutions.fishToPlant)
            Adaptation.GLUTEN_FREE -> swapAll(Substitutions.glutenFree)
            Adaptation.DAIRY_FREE -> swapAll(Substitutions.dairyToPlant)
            Adaptation.SPICIER -> {
                ingredients = ingredients + Ingredient(name = "red chilli, finely sliced", quantity = 1.0, unit = null, raw = "1 red chilli, finely sliced", aisle = com.mobuk.app.domain.model.Aisle.PRODUCE) +
                    Ingredient(name = "chilli flakes", quantity = 1.0, unit = "tsp", raw = "1 tsp chilli flakes", aisle = com.mobuk.app.domain.model.Aisle.HERBS_SPICES)
                steps = steps + RecipeStep(steps.size, "Add the sliced chilli with the aromatics and finish with chilli flakes to taste.")
                changes += "Added fresh chilli and chilli flakes"
            }
            Adaptation.MILDER -> {
                val before = ingredients.size
                ingredients = ingredients.filterNot { i -> listOf("chilli", "chili", "cayenne", "jalape", "hot sauce", "sriracha", "harissa", "gochujang").any { (i.name + i.raw).lowercase().contains(it) } }
                if (ingredients.size < before) changes += "Removed ${before - ingredients.size} spicy ingredient(s)"
                ingredients = ingredients.map { i -> if (i.name.contains("curry powder") || i.name.contains("paprika")) i.copy(name = "mild " + i.name, raw = "mild " + i.raw) else i }
            }
            Adaptation.HIGHER_PROTEIN -> {
                val veggie = com.mobuk.app.domain.model.DietTag.VEGETARIAN in recipe.dietTags
                val add = if (veggie) Ingredient("tin of chickpeas, drained", 1.0, "can", "1 can chickpeas, drained", aisle = com.mobuk.app.domain.model.Aisle.TINS_JARS)
                else Ingredient("chicken breast, diced", 200.0, "g", "200g chicken breast, diced", aisle = com.mobuk.app.domain.model.Aisle.MEAT_FISH)
                ingredients = ingredients + add
                steps = steps + RecipeStep(steps.size, "Stir through the ${add.name} and cook until heated through (or cooked, for meat) before serving.")
                changes += "Added ${add.name} for extra protein"
            }
            Adaptation.LOWER_CALORIE -> {
                ingredients = ingredients.map { i ->
                    val lower = i.name.lowercase()
                    when {
                        lower.contains("double cream") || lower.contains("heavy cream") -> { changes += "Cream → half-fat crème fraîche"; i.copy(name = "half-fat crème fraîche", raw = i.raw.replace(Regex("(?i)double cream|heavy cream"), "half-fat crème fraîche")) }
                        lower.contains("oil") || lower.contains("butter") -> { changes += "Halved ${i.name}"; i.copy(quantity = i.quantity?.let { it / 2 }, raw = "half the " + i.raw) }
                        lower.contains("sugar") -> { changes += "Reduced sugar"; i.copy(quantity = i.quantity?.let { it * 0.6 }, raw = "reduced " + i.raw) }
                        else -> i
                    }
                }
            }
            Adaptation.QUICKER -> {
                steps = steps.map { s -> s.copy(text = s.text.replace(Regex("""(\d+)\s*(minutes|mins)""")) { m -> "${(m.groupValues[1].toInt() * 0.75).toInt().coerceAtLeast(1)} ${m.groupValues[2]}" }) }
                changes += "Trimmed simmer and bake times by a quarter; use pre-chopped veg and pre-cooked grains where you can"
            }
            Adaptation.BUDGET -> swapAll(Substitutions.budget)
            Adaptation.SWAP, Adaptation.CUSTOM -> {
                val text = customInstruction.orEmpty()
                Regex("""(?:swap|replace)\s+(.+?)\s+(?:with|for)\s+(.+)""", RegexOption.IGNORE_CASE).find(text)?.let { m ->
                    val from = m.groupValues[1].trim().lowercase()
                    val to = m.groupValues[2].trim().trimEnd('.')
                    ingredients = ingredients.map { i ->
                        if ((i.name + " " + i.raw).lowercase().contains(from)) { changes += "${i.name} → $to"; i.copy(name = to, raw = listOfNotNull(QuantityFormatter.formatWithUnit(i.quantity, i.unit).takeIf { it.isNotBlank() }, to).joinToString(" ")) } else i
                    }
                    steps = steps.map { s -> s.copy(text = s.text.replace(Regex("(?i)" + Regex.escape(from)), to)) }
                } ?: run { changes += "Couldn't parse the swap; write it as \"swap X with Y\"." }
            }
        }
        if (servings != null && recipe.servings != null && servings != recipe.servings) {
            ingredients = ingredients.map { it.copy(quantity = ServingScaler.scaleAmount(it.quantity, recipe.servings, servings)) }
            changes += "Scaled to $servings servings"
        }
        val title = when (adaptation) {
            Adaptation.SWAP, Adaptation.CUSTOM -> "${recipe.title} (adapted)"
            else -> "${recipe.title} (${adaptation.label.lowercase()})"
        }
        val adapted = newVariant(recipe, title, ingredients, steps, servings)
        val notes = if (changes.isEmpty()) "No changes were needed for this recipe." else "Rule-based changes: " + changes.distinct().joinToString("; ")
        return AdaptedRecipe(adapted, notes, usedModel = false)
    }

    private fun newVariant(original: Recipe, title: String, ingredients: List<Ingredient>, steps: List<RecipeStep>, servings: Int?): Recipe {
        val id = UUID.randomUUID().toString().take(12)
        return original.copy(
            id = Recipe.makeId(RecipeSource.USER, id),
            source = RecipeSource.USER,
            sourceId = id,
            title = title,
            description = "Adapted from ${original.title} (${original.source.label}).",
            author = "You (adapted from ${original.author ?: original.source.label})",
            ingredients = ingredients,
            steps = steps,
            servings = servings,
            nutrition = null,
            dietTags = DietTagger.tag(ingredients, null, original.effectiveMinutes, original.tags),
            cachedAt = System.currentTimeMillis(),
            isComplete = true,
        )
    }
}

object Substitutions {
    val meatToPlant: List<Pair<List<String>, String>> = listOf(
        listOf("chicken stock", "beef stock", "chicken broth", "beef broth", "bone broth") to "vegetable stock",
        listOf("mince", "ground beef", "ground pork", "ground lamb", "ground turkey") to "plant-based mince",
        listOf("chicken", "turkey", "duck", "quail", "pheasant") to "firm tofu, cubed",
        listOf("bacon", "pancetta", "lardons") to "smoked tempeh strips",
        listOf("chorizo", "sausage", "pepperoni", "salami") to "vegan sausage",
        listOf("beef", "steak", "brisket", "veal", "venison", "lamb", "mutton", "goat", "pork", "ham", "gammon", "prosciutto") to "king oyster mushrooms or seitan",
        listOf("gelatin", "gelatine") to "agar agar",
        listOf("lard", "suet") to "vegetable shortening",
    )
    val fishToPlant: List<Pair<List<String>, String>> = listOf(
        listOf("fish sauce", "nam pla") to "light soy sauce with a squeeze of lime",
        listOf("oyster sauce") to "vegetarian mushroom stir-fry sauce",
        listOf("worcestershire") to "vegan Worcestershire sauce",
        listOf("anchov") to "capers",
        listOf("prawn", "shrimp", "scallop", "squid", "calamari", "crab", "lobster", "mussel", "clam", "oyster") to "king oyster mushrooms",
        listOf("salmon", "tuna", "cod", "haddock", "trout", "mackerel", "sea bass", "tilapia", "hake", "monkfish", "halibut", "fish") to "banana blossom or firm tofu",
    )
    val dairyToPlant: List<Pair<List<String>, String>> = listOf(
        listOf("double cream", "heavy cream", "whipping cream", "single cream") to "oat cream",
        listOf("crème fraîche", "creme fraiche", "sour cream") to "plant-based sour cream",
        listOf("cream cheese", "mascarpone", "ricotta") to "vegan cream cheese",
        listOf("parmesan", "pecorino") to "nutritional yeast",
        listOf("feta", "halloumi", "paneer") to "firm tofu",
        listOf("mozzarella", "cheddar", "gruyere", "gruyère", "cheese") to "vegan cheese",
        listOf("yoghurt", "yogurt") to "coconut yoghurt",
        listOf("butter", "ghee") to "plant-based butter",
        listOf("condensed milk") to "coconut condensed milk",
        listOf("milk") to "oat milk",
    )
    val eggToPlant: List<Pair<List<String>, String>> = listOf(
        listOf("mayonnaise", "mayo", "aioli") to "vegan mayonnaise",
        listOf("egg") to "flax egg (1 tbsp ground flaxseed + 3 tbsp water)",
    )
    val honey: List<Pair<List<String>, String>> = listOf(listOf("honey") to "maple syrup")
    val glutenFree: List<Pair<List<String>, String>> = listOf(
        listOf("soy sauce") to "tamari",
        listOf("plain flour", "all-purpose flour", "self-raising flour", "flour") to "gluten-free flour blend",
        listOf("breadcrumb", "panko") to "gluten-free breadcrumbs",
        listOf("spaghetti", "penne", "fusilli", "tagliatelle", "linguine", "macaroni", "pasta", "orzo") to "gluten-free pasta",
        listOf("couscous", "bulgur", "pearl barley", "barley", "farro") to "quinoa",
        listOf("noodle", "udon", "ramen") to "rice noodles",
        listOf("tortilla", "wrap") to "corn tortillas",
        listOf("naan", "pitta", "pita", "bread", "bun", "baguette") to "gluten-free bread",
        listOf("beer", "lager", "ale") to "gluten-free beer",
        listOf("hoisin") to "gluten-free hoisin",
    )
    val budget: List<Pair<List<String>, String>> = listOf(
        listOf("sirloin", "ribeye", "fillet steak", "steak") to "beef skirt or diced braising steak",
        listOf("chicken breast") to "chicken thighs",
        listOf("king prawn", "prawn", "shrimp") to "frozen prawns",
        listOf("salmon") to "frozen white fish fillets",
        listOf("parmesan") to "mature cheddar",
        listOf("pine nut") to "sunflower seeds",
        listOf("saffron") to "turmeric",
        listOf("fresh herbs", "fresh basil", "fresh coriander") to "dried mixed herbs",
    )
}
