package com.mobuk.app

import com.mobuk.app.ai.JsonExtract
import com.mobuk.app.ai.JsonExtract.string
import com.mobuk.app.ai.JsonExtract.strings
import com.mobuk.app.data.remote.webimport.RecipeHtmlParser
import com.mobuk.app.domain.logic.SearchFilters
import com.mobuk.app.domain.model.DietTag
import com.mobuk.app.domain.model.Ingredient
import com.mobuk.app.domain.model.Recipe
import com.mobuk.app.domain.model.RecipeSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentAndImportTest {

    @Test
    fun extractsJsonFromChattyModelOutput() {
        val text = "Sure! Here is the call:\n```json\n{\"tool\": \"search_recipes\", \"args\": {\"query\": \"veggie curry\", \"max_minutes\": 30}}\n```\nLet me know."
        val obj = JsonExtract.firstObject(text)
        assertNotNull(obj)
        assertEquals("search_recipes", obj!!.string("tool"))
        val nested = JsonExtract.firstObject("{\"final\":\"Try the dal\",\"recipe_ids\":[\"themealdb:1\",\"myplate:2\"]}")!!
        assertEquals(listOf("themealdb:1", "myplate:2"), nested.strings("recipe_ids"))
    }

    @Test
    fun handlesBracesInsideStrings() {
        val obj = JsonExtract.firstObject("Answer: {\"final\": \"Use {curly} braces sparingly\"}")
        assertEquals("Use {curly} braces sparingly", obj!!.string("final"))
    }

    @Test
    fun parsesSchemaOrgRecipe() {
        val html = """
            <html><head><title>Best Chilli | Example</title>
            <script type="application/ld+json">
            {"@context":"https://schema.org","@graph":[{"@type":"WebPage"},{"@type":"Recipe","name":"Best Chilli",
             "image":["https://example.com/chilli.jpg"],"author":{"@type":"Person","name":"Jo Cook"},
             "prepTime":"PT15M","cookTime":"PT1H","totalTime":"PT1H15M","recipeYield":"4 servings",
             "recipeCuisine":"Mexican","recipeCategory":["Dinner"],"keywords":"chilli, beef",
             "recipeIngredient":["500g beef mince","1 onion, chopped","400g tin kidney beans","1 tsp cumin"],
             "recipeInstructions":[{"@type":"HowToStep","text":"Brown the mince."},{"@type":"HowToSection","name":"Simmer","itemListElement":[{"@type":"HowToStep","text":"Simmer for 45 minutes."}]}],
             "nutrition":{"@type":"NutritionInformation","calories":"480 kcal","proteinContent":"35 g"}}]}
            </script></head><body></body></html>
        """.trimIndent()
        val recipe = RecipeHtmlParser.parse("https://example.com/chilli", html)
        assertNotNull(recipe)
        recipe!!
        assertEquals("Best Chilli", recipe.title)
        assertEquals(RecipeSource.WEB_IMPORT, recipe.source)
        assertEquals("Jo Cook", recipe.author)
        assertEquals(15, recipe.prepMinutes)
        assertEquals(60, recipe.cookMinutes)
        assertEquals(75, recipe.totalMinutes)
        assertEquals(4, recipe.servings)
        assertEquals("Mexican", recipe.cuisine)
        assertEquals(4, recipe.ingredients.size)
        assertEquals(2, recipe.steps.size)
        assertEquals(45 * 60, recipe.steps[1].timerSeconds)
        assertEquals(480.0, recipe.nutrition!!.calories!!, 0.01)
        assertEquals(35.0, recipe.nutrition!!.proteinG!!, 0.01)
        assertFalse(DietTag.VEGETARIAN in recipe.dietTags)
        assertTrue(DietTag.HIGH_PROTEIN in recipe.dietTags)
    }

    @Test
    fun searchFiltersMatchLocally() {
        val recipe = Recipe(
            id = "user:1", source = RecipeSource.USER, sourceId = "1", title = "Thai green curry", cuisine = "Thai", category = "Curry",
            totalMinutes = 25, dietTags = setOf(DietTag.VEGAN, DietTag.VEGETARIAN, DietTag.QUICK),
            ingredients = listOf(Ingredient("tofu", raw = "200g tofu"), Ingredient("coconut milk", raw = "400ml coconut milk")),
        )
        assertTrue(SearchFilters(query = "thai curry").matches(recipe))
        assertTrue(SearchFilters(diets = setOf(DietTag.VEGAN), maxMinutes = 30).matches(recipe))
        assertFalse(SearchFilters(maxMinutes = 20).matches(recipe))
        assertTrue(SearchFilters(ingredients = listOf("tofu", "chicken")).matches(recipe))
        assertFalse(SearchFilters(excludeIngredients = setOf("coconut")).matches(recipe))
        assertFalse(SearchFilters(cuisines = setOf("Indian")).matches(recipe))
    }
}
