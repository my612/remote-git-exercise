package com.mobuk.app.domain.logic

import com.mobuk.app.domain.model.DietTag
import com.mobuk.app.domain.model.Ingredient
import com.mobuk.app.domain.model.Nutrition

/**
 * Infers dietary tags from ingredient names. Sources such as TheMealDB do not label most recipes,
 * so the app tags them itself. Results are conservative: an unknown ingredient never removes a tag,
 * but any obvious meat, dairy or gluten does.
 */
object DietTagger {

    val meat = listOf(
        "chicken", "beef", "pork", "lamb", "turkey", "duck", "steak", "mince", "sausage", "bacon", "pancetta",
        "chorizo", "ham", "prosciutto", "salami", "meatball", "burger", "brisket", "veal", "venison", "goat",
        "kidney", "liver", "gammon", "rabbit", "quail", "pheasant", "pepperoni", "bratwurst", "kielbasa", "gelatin",
        "gelatine", "lard", "suet", "bone broth", "chicken stock", "beef stock", "oxtail", "mutton", "kebab",
        "hot dog", "frankfurter", "ribs", "pastrami", "corned beef", "spam", "bologna", "mortadella",
    )
    val fish = listOf(
        "salmon", "cod", "haddock", "prawn", "shrimp", "mussel", "clam", "squid", "calamari", "crab", "lobster",
        "trout", "mackerel", "sea bass", "tilapia", "fish", "hake", "monkfish", "halibut", "scallop", "oyster",
        "anchov", "tuna", "sardine", "octopus", "eel", "roe", "caviar", "worcestershire", "dashi", "bonito",
        "oyster sauce", "shrimp paste", "nam pla",
    )
    val dairy = listOf(
        "milk", "butter", "cheese", "cheddar", "mozzarella", "parmesan", "feta", "halloumi", "yoghurt", "yogurt",
        "cream", "crème fraîche", "creme fraiche", "mascarpone", "ricotta", "brie", "gruyere", "gruyère", "stilton",
        "gouda", "custard", "kefir", "quark", "ghee", "paneer", "whey", "condensed milk", "evaporated milk", "ice cream",
        "buttermilk", "sour cream",
    )
    private val dairyExceptions = listOf(
        "coconut milk", "almond milk", "oat milk", "soy milk", "soya milk", "rice milk", "cashew milk", "coconut cream",
        "peanut butter", "almond butter", "cocoa butter", "cream of tartar", "vegan", "dairy-free", "dairy free",
        "plant-based", "nut butter", "coconut yoghurt", "coconut yogurt", "cream crackers", "cream sherry",
    )
    val eggs = listOf("egg", "mayonnaise", "mayo", "meringue", "aioli", "hollandaise")
    private val eggExceptions = listOf("eggplant", "vegan mayo", "egg-free", "aubergine")
    val honey = listOf("honey")
    val gluten = listOf(
        "flour", "bread", "pasta", "spaghetti", "penne", "fusilli", "tagliatelle", "linguine", "macaroni", "noodle",
        "couscous", "bulgur", "barley", "wheat", "rye", "semolina", "breadcrumb", "panko", "pastry", "pitta", "pita",
        "naan", "tortilla", "wrap", "bun", "baguette", "ciabatta", "croissant", "bagel", "crumpet", "cracker", "biscuit",
        "cookie", "soy sauce", "seitan", "beer", "lager", "ale", "malt", "pearl barley", "orzo", "gnocchi", "lasagne",
        "lasagna", "ravioli", "tortellini", "dumpling", "wonton", "gyoza", "filo", "phyllo", "pizza", "cake", "brioche",
        "udon", "ramen", "farro", "spelt", "hoisin", "stuffing", "yorkshire pudding", "pie crust", "shortcrust",
    )
    private val glutenExceptions = listOf(
        "gluten-free", "gluten free", "rice noodle", "rice flour", "corn tortilla", "cornflour", "cornstarch",
        "almond flour", "coconut flour", "chickpea flour", "gram flour", "buckwheat", "tamari", "rice paper",
        "glass noodle", "soba", "corn flour", "potato flour", "tapioca", "polenta", "rice cake", "quinoa",
    )
    val nuts = listOf(
        "almond", "walnut", "cashew", "peanut", "pecan", "pistachio", "hazelnut", "macadamia", "brazil nut", "pine nut",
        "nuts", "praline", "marzipan", "frangipane", "satay", "nutella",
    )
    private val nutExceptions = listOf("nutmeg", "coconut", "butternut", "chestnut mushroom", "water chestnut", "doughnut", "donut")

    fun containsAny(name: String, keywords: List<String>, exceptions: List<String> = emptyList()): Boolean {
        val n = name.lowercase()
        if (exceptions.any { n.contains(it) }) return false
        return keywords.any { n.contains(it) }
    }

    fun tag(ingredients: List<Ingredient>, nutrition: Nutrition?, totalMinutes: Int?, sourceTags: Collection<String> = emptyList()): Set<DietTag> {
        val names = ingredients.map { it.name.ifBlank { it.raw } }
        val src = sourceTags.map { it.lowercase() }
        val hasMeat = names.any { containsAny(it, meat) }
        val hasFish = names.any { containsAny(it, fish) }
        val hasDairy = names.any { containsAny(it, dairy, dairyExceptions) }
        val hasEggs = names.any { containsAny(it, eggs, eggExceptions) }
        val hasHoney = names.any { containsAny(it, honey) }
        val hasGluten = names.any { containsAny(it, gluten, glutenExceptions) }
        val hasNuts = names.any { containsAny(it, nuts, nutExceptions) }

        val tags = mutableSetOf<DietTag>()
        val srcVegan = src.any { it.contains("vegan") }
        val srcVeg = src.any { it.contains("vegetarian") || it == "veggie" }
        if (names.isNotEmpty() || srcVegan || srcVeg) {
            if (srcVegan || (!hasMeat && !hasFish && !hasDairy && !hasEggs && !hasHoney && names.isNotEmpty())) tags += DietTag.VEGAN
            if (srcVegan || srcVeg || (!hasMeat && !hasFish && names.isNotEmpty())) tags += DietTag.VEGETARIAN
            if (!hasMeat && (hasFish || srcVeg || srcVegan) && names.isNotEmpty()) tags += DietTag.PESCATARIAN
            if (tags.contains(DietTag.VEGETARIAN)) tags += DietTag.PESCATARIAN
        }
        if (names.isNotEmpty() && !hasGluten) tags += DietTag.GLUTEN_FREE
        if (src.any { it.contains("gluten free") || it.contains("gluten-free") }) tags += DietTag.GLUTEN_FREE
        if (names.isNotEmpty() && !hasDairy) tags += DietTag.DAIRY_FREE
        if (names.isNotEmpty() && !hasNuts) tags += DietTag.NUT_FREE
        val protein = nutrition?.proteinG
        val calories = nutrition?.calories
        if (protein != null && protein >= 25) tags += DietTag.HIGH_PROTEIN
        if (calories != null && calories in 1.0..500.0) tags += DietTag.LOW_CALORIE
        if (totalMinutes != null && totalMinutes in 1..30) tags += DietTag.QUICK
        return tags
    }
}
