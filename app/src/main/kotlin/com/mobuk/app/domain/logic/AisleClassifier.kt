package com.mobuk.app.domain.logic

import com.mobuk.app.domain.model.Aisle

/** Keyword based aisle classifier. Longer keywords win so "coconut milk" beats "milk". */
object AisleClassifier {

    private val rules: List<Pair<Aisle, List<String>>> = listOf(
        Aisle.HOUSEHOLD to listOf(
            "toothpaste", "kitchen roll", "foil", "cling film", "bin bag", "washing up", "dishwasher", "sponge",
            "toilet roll", "baking paper", "parchment", "greaseproof", "candle", "batteries", "soap",
        ),
        Aisle.HERBS_SPICES to listOf(
            "cumin", "coriander seed", "ground coriander", "paprika", "turmeric", "cinnamon", "nutmeg", "clove",
            "cardamom", "chilli powder", "chili powder", "chilli flakes", "chili flakes", "cayenne", "oregano",
            "thyme", "rosemary", "bay leaf", "bay leaves", "garam masala", "curry powder", "five spice", "allspice",
            "star anise", "fennel seed", "mustard seed", "sumac", "za'atar", "zaatar", "peppercorn", "black pepper",
            "white pepper", "salt", "dried herbs", "mixed herbs", "italian seasoning", "smoked paprika", "saffron",
            "vanilla", "ginger powder", "ground ginger", "garlic powder", "onion powder", "dried oregano", "dried basil",
            "dried thyme", "dried parsley", "dried dill", "chilli", "chili", "seasoning", "stock cube", "bouillon",
        ),
        Aisle.OILS_SAUCES to listOf(
            "olive oil", "vegetable oil", "sunflower oil", "rapeseed oil", "sesame oil", "coconut oil", "oil",
            "vinegar", "soy sauce", "fish sauce", "oyster sauce", "hoisin", "worcestershire", "ketchup", "mayonnaise",
            "mayo", "mustard", "hot sauce", "sriracha", "tabasco", "bbq sauce", "barbecue sauce", "tahini", "pesto",
            "honey", "maple syrup", "golden syrup", "treacle", "mirin", "teriyaki", "sweet chilli", "sweet chili",
            "harissa", "miso", "gochujang", "chipotle", "salsa", "relish", "chutney", "pickle", "capers", "olives",
            "peanut butter", "almond butter", "nutella", "jam", "marmalade", "stock", "broth", "curry paste",
        ),
        Aisle.TINS_JARS to listOf(
            "tinned", "canned", "can of", "tin of", "chopped tomatoes", "plum tomatoes", "passata", "tomato puree",
            "tomato paste", "tomato purée", "coconut milk", "coconut cream", "chickpeas", "kidney beans", "black beans",
            "cannellini", "butter beans", "baked beans", "lentils", "sweetcorn", "tuna", "sardines", "anchov",
            "pineapple chunks", "condensed milk", "evaporated milk", "artichoke", "jackfruit", "pumpkin puree",
        ),
        Aisle.PANTRY to listOf(
            "flour", "sugar", "rice", "pasta", "spaghetti", "penne", "fusilli", "tagliatelle", "linguine", "macaroni",
            "noodle", "couscous", "quinoa", "bulgur", "oats", "porridge", "cereal", "granola", "breadcrumbs", "panko",
            "baking powder", "bicarbonate", "baking soda", "yeast", "cornflour", "cornstarch", "cocoa", "chocolate",
            "nuts", "almond", "walnut", "cashew", "peanut", "pecan", "pistachio", "hazelnut", "seeds", "raisin", "sultana",
            "dried fruit", "dates", "apricot", "crackers", "tortilla", "wrap", "taco shell", "polenta", "semolina",
            "gelatin", "gelatine", "custard powder", "icing sugar", "caster sugar", "brown sugar", "demerara",
            "crisps", "popcorn", "biscuit", "cookie", "stock pot", "gravy", "lasagne sheet", "lasagna sheet", "gnocchi",
            "orzo", "vermicelli", "rice paper", "risotto", "arborio", "basmati", "jasmine rice",
        ),
        Aisle.WORLD to listOf(
            "tofu", "tempeh", "kimchi", "nori", "seaweed", "wasabi", "dashi", "panko", "udon", "soba", "ramen",
            "tamarind", "plantain", "paneer", "ghee", "naan", "poppadom", "rice vinegar", "rice wine", "shaoxing",
        ),
        Aisle.FROZEN to listOf(
            "frozen", "ice cream", "puff pastry", "filo", "phyllo", "shortcrust", "peas", "edamame", "ice",
        ),
        Aisle.BAKERY to listOf(
            "bread", "baguette", "ciabatta", "sourdough", "bun", "roll", "pitta", "pita", "brioche", "croissant",
            "bagel", "crumpet", "muffin", "cake", "pastry", "focaccia",
        ),
        Aisle.DAIRY_EGGS to listOf(
            "milk", "butter", "cheese", "cheddar", "mozzarella", "parmesan", "feta", "halloumi", "cream cheese",
            "yoghurt", "yogurt", "cream", "crème fraîche", "creme fraiche", "sour cream", "egg", "eggs", "margarine",
            "mascarpone", "ricotta", "brie", "goat cheese", "gruyere", "gruyère", "blue cheese", "stilton", "gouda",
            "cottage cheese", "custard", "kefir", "quark", "double cream", "single cream", "whipping cream",
        ),
        Aisle.CHILLED to listOf(
            "hummus", "houmous", "dip", "fresh pasta", "tortellini", "ravioli", "juice", "smoothie", "sausage roll",
            "quiche", "coleslaw", "dumplings", "wonton", "gyoza", "pizza",
        ),
        Aisle.MEAT_FISH to listOf(
            "chicken", "beef", "pork", "lamb", "turkey", "duck", "steak", "mince", "ground beef", "sausage",
            "bacon", "pancetta", "chorizo", "ham", "prosciutto", "salami", "salmon", "cod", "haddock", "prawn",
            "shrimp", "mussel", "clam", "squid", "calamari", "crab", "lobster", "trout", "mackerel", "sea bass",
            "tilapia", "fish", "meatball", "burger", "ribs", "brisket", "veal", "venison", "kidney", "liver", "goat",
            "hake", "monkfish", "halibut", "scallop", "oyster", "anchovy", "tuna steak", "bratwurst", "kielbasa",
            "pepperoni", "gammon", "rabbit", "quail", "pheasant", "kebab",
        ),
        Aisle.DRINKS to listOf(
            "wine", "beer", "cider", "lager", "rum", "vodka", "whisky", "whiskey", "gin", "brandy", "sherry",
            "vermouth", "tequila", "bourbon", "prosecco", "champagne", "cola", "lemonade", "soda", "sparkling water",
            "tonic", "coffee", "tea", "espresso", "kombucha", "port", "marsala", "liqueur", "cointreau", "amaretto",
            "kahlua", "baileys",
        ),
        Aisle.PRODUCE to listOf(
            "onion", "garlic", "ginger", "tomato", "potato", "carrot", "celery", "pepper", "capsicum", "chilli",
            "chili", "lemon", "lime", "orange", "apple", "banana", "berry", "berries", "strawberr", "raspberr",
            "blueberr", "grape", "mango", "pineapple", "avocado", "lettuce", "spinach", "kale", "rocket", "arugula",
            "cabbage", "broccoli", "cauliflower", "courgette", "zucchini", "aubergine", "eggplant", "cucumber",
            "mushroom", "leek", "spring onion", "scallion", "shallot", "sweet potato", "squash", "pumpkin", "beetroot",
            "beet", "radish", "fennel", "asparagus", "green bean", "runner bean", "pea", "sweetcorn cob", "corn",
            "parsley", "basil", "mint", "dill", "chive", "tarragon", "sage", "herbs", "coriander", "cilantro",
            "lemongrass", "pear", "peach", "plum", "cherry", "melon", "watermelon", "kiwi", "pomegranate", "fig",
            "date", "apricot", "nectarine", "grapefruit", "clementine", "satsuma", "salad", "cress", "watercress",
            "bok choy", "pak choi", "chard", "turnip", "parsnip", "swede", "artichoke", "okra", "bean sprout",
            "sprouts", "brussels", "mangetout", "sugar snap", "edamame", "jalape", "greens", "microgreens", "lime leaves",
        ),
    )

    fun classify(ingredientName: String): Aisle {
        val name = ingredientName.lowercase()
        var best: Aisle? = null
        var bestLength = 0
        for ((aisle, keywords) in rules) {
            for (keyword in keywords) {
                if (name.contains(keyword) && keyword.length > bestLength) {
                    best = aisle
                    bestLength = keyword.length
                }
            }
        }
        // Fresh herbs are produce even though dried ones are spices.
        if (name.startsWith("fresh ")) {
            if (best == Aisle.HERBS_SPICES) return Aisle.PRODUCE
        }
        if (name.startsWith("dried ") && best == Aisle.PRODUCE) return Aisle.HERBS_SPICES
        if (name.contains("frozen")) return Aisle.FROZEN
        if (name.contains("tinned") || name.contains("canned") || name.startsWith("can of") || name.startsWith("tin of")) return Aisle.TINS_JARS
        return best ?: Aisle.OTHER
    }
}
