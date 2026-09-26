package com.mobuk.app.domain.logic

import com.mobuk.app.domain.model.Aisle
import com.mobuk.app.domain.model.Ingredient
import com.mobuk.app.domain.model.MealType
import com.mobuk.app.domain.model.ShoppingItem

object IngredientNormalizer {

    private val prepWords = listOf(
        "chopped", "finely chopped", "roughly chopped", "diced", "sliced", "thinly sliced", "minced", "crushed",
        "grated", "peeled", "deseeded", "seeded", "halved", "quartered", "cubed", "shredded", "torn", "trimmed",
        "softened", "melted", "beaten", "toasted", "cooked", "drained", "rinsed", "washed", "zested", "juiced",
        "to serve", "for serving", "to garnish", "for garnish", "optional", "plus extra", "plus more", "or to taste",
        "at room temperature", "room temperature", "freshly ground", "freshly grated", "lightly beaten", "finely grated",
        "finely diced", "finely sliced", "roughly torn", "cut into chunks", "cut into wedges", "cut into pieces",
        "cut into cubes", "cut into strips", "skinless", "boneless", "bone-in", "skin-on",
    )

    /** Builds an [Ingredient] from a free text line like "2 tbsp olive oil, plus extra for drizzling". */
    fun fromLine(line: String): Ingredient {
        val raw = line.trim()
        val parsed = QuantityParser.parse(raw)
        val (name, note) = splitNote(parsed.rest.ifBlank { raw })
        val cleanName = cleanName(name)
        return Ingredient(
            name = cleanName.ifBlank { raw },
            quantity = parsed.amount,
            unit = parsed.unit,
            raw = raw,
            note = note,
            aisle = AisleClassifier.classify(cleanName.ifBlank { raw }),
        )
    }

    /** TheMealDB gives the name and the measure separately. */
    fun fromNameAndMeasure(name: String, measure: String): Ingredient {
        val parsed = QuantityParser.parse(measure.trim())
        val cleanName = cleanName(name)
        val raw = listOf(measure.trim(), name.trim()).filter { it.isNotBlank() }.joinToString(" ")
        val note = parsed.rest.takeIf { it.isNotBlank() && !it.equals(name, true) }
        return Ingredient(
            name = cleanName.ifBlank { name.trim() },
            quantity = parsed.amount,
            unit = parsed.unit,
            raw = raw,
            note = note,
            aisle = AisleClassifier.classify(cleanName.ifBlank { name }),
        )
    }

    fun splitNote(text: String): Pair<String, String?> {
        val paren = Regex("""\(([^)]*)\)""")
        val notes = mutableListOf<String>()
        var name = paren.replace(text) { m -> notes += m.groupValues[1].trim(); "" }
        val commaIndex = name.indexOf(',')
        if (commaIndex > 0) {
            notes += name.substring(commaIndex + 1).trim()
            name = name.substring(0, commaIndex)
        }
        return name.trim() to notes.filter { it.isNotBlank() }.joinToString(", ").ifBlank { null }
    }

    fun cleanName(name: String): String {
        var n = name.trim().lowercase()
        n = n.removePrefix("of ").trim()
        for (w in prepWords.sortedByDescending { it.length }) {
            n = n.replace(Regex("""\b${Regex.escape(w)}\b"""), " ")
        }
        n = n.replace(Regex("""\s*[,;:]\s*$"""), "")
        n = n.replace(Regex("\\s+"), " ").trim().trim(',', '-', '.')
        return n
    }

    fun normalizedKey(name: String): String {
        val n = cleanName(name)
        // Cheap singularisation so "tomatoes" and "tomato" merge.
        return when {
            n.endsWith("ies") -> n.dropLast(3) + "y"
            n.endsWith("oes") -> n.dropLast(2)
            n.endsWith("ches") || n.endsWith("shes") || n.endsWith("sses") -> n.dropLast(2)
            n.endsWith("s") && !n.endsWith("ss") && n.length > 3 -> n.dropLast(1)
            else -> n
        }
    }

    /** Merges ingredient lines into shopping items, summing quantities that share a name and unit. */
    fun toShoppingItems(recipeId: String, ingredients: List<Ingredient>, scale: Double, existing: List<ShoppingItem>): List<ShoppingItem> {
        val result = existing.toMutableList()
        for (ing in ingredients) {
            val key = normalizedKey(ing.name)
            val unit = ing.unit
            val qty = ing.quantity?.let { it * scale }
            val match = result.indexOfFirst {
                !it.isCustom && !it.checked && normalizedKey(it.name) == key && (it.unit == unit || (it.quantity == null && qty == null))
            }
            if (match >= 0) {
                val item = result[match]
                val merged = when {
                    item.quantity == null && qty == null -> null
                    else -> (item.quantity ?: 0.0) + (qty ?: 0.0)
                }
                result[match] = item.copy(
                    quantity = merged,
                    recipeIds = (item.recipeIds + recipeId).distinct(),
                )
            } else {
                result += ShoppingItem(
                    name = ing.name,
                    quantity = qty,
                    unit = unit,
                    aisle = if (ing.aisle == Aisle.OTHER) AisleClassifier.classify(ing.name) else ing.aisle,
                    recipeIds = listOf(recipeId),
                    note = ing.note,
                )
            }
        }
        return result
    }

    /** Guesses meal types from a category / title when the source has none. */
    fun guessMealTypes(category: String?, title: String, tags: List<String>): List<MealType> {
        val text = (listOfNotNull(category, title) + tags).joinToString(" ").lowercase()
        val types = mutableListOf<MealType>()
        if (listOf("breakfast", "brunch", "pancake", "porridge", "granola", "omelette", "omelet", "waffle", "oats").any { text.contains(it) }) types += MealType.BREAKFAST
        if (listOf("dessert", "cake", "pudding", "cookie", "brownie", "tart", "pie", "ice cream", "sweet", "cheesecake", "mousse", "muffin").any { text.contains(it) }) types += MealType.DESSERT
        if (listOf("side", "salad", "slaw", "dip", "sauce", "bread").any { text.contains(it) }) types += MealType.SIDE
        if (listOf("snack", "starter", "appetizer", "appetiser", "bites", "nibbles").any { text.contains(it) }) types += MealType.SNACK
        if (listOf("drink", "smoothie", "cocktail", "juice", "lassi", "beverage").any { text.contains(it) }) types += MealType.DRINK
        if (listOf("lunch", "sandwich", "wrap", "soup").any { text.contains(it) }) types += MealType.LUNCH
        if (types.isEmpty() || listOf("dinner", "main", "beef", "chicken", "lamb", "pork", "pasta", "curry", "seafood", "vegetarian", "vegan", "goat", "stew", "roast", "bake", "casserole").any { text.contains(it) }) {
            if (!types.contains(MealType.DESSERT) && !types.contains(MealType.DRINK)) types += MealType.DINNER
        }
        return types.distinct()
    }
}
