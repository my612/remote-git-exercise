package com.mobuk.app

import com.mobuk.app.domain.logic.AisleClassifier
import com.mobuk.app.domain.logic.DietTagger
import com.mobuk.app.domain.logic.IngredientNormalizer
import com.mobuk.app.domain.logic.TimerExtractor
import com.mobuk.app.domain.model.Aisle
import com.mobuk.app.domain.model.DietTag
import com.mobuk.app.domain.model.Nutrition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AisleAndDietTest {

    @Test
    fun classifiesCommonIngredients() {
        assertEquals(Aisle.PRODUCE, AisleClassifier.classify("red onion"))
        assertEquals(Aisle.MEAT_FISH, AisleClassifier.classify("chicken thighs"))
        assertEquals(Aisle.DAIRY_EGGS, AisleClassifier.classify("mature cheddar"))
        assertEquals(Aisle.TINS_JARS, AisleClassifier.classify("coconut milk"))
        assertEquals(Aisle.HERBS_SPICES, AisleClassifier.classify("ground cumin"))
        assertEquals(Aisle.PRODUCE, AisleClassifier.classify("fresh coriander"))
        assertEquals(Aisle.HERBS_SPICES, AisleClassifier.classify("dried oregano"))
        assertEquals(Aisle.OILS_SAUCES, AisleClassifier.classify("soy sauce"))
        assertEquals(Aisle.PANTRY, AisleClassifier.classify("basmati rice"))
        assertEquals(Aisle.FROZEN, AisleClassifier.classify("frozen peas"))
        assertEquals(Aisle.HOUSEHOLD, AisleClassifier.classify("toothpaste"))
    }

    @Test
    fun tagsVegetarianAndVegan() {
        val veg = listOf("chickpeas", "spinach", "coconut milk", "onion", "garam masala").map { IngredientNormalizer.fromLine(it) }
        val tags = DietTagger.tag(veg, null, 25)
        assertTrue(DietTag.VEGAN in tags)
        assertTrue(DietTag.VEGETARIAN in tags)
        assertTrue(DietTag.GLUTEN_FREE in tags)
        assertTrue(DietTag.DAIRY_FREE in tags)
        assertTrue(DietTag.QUICK in tags)

        val withCheese = veg + IngredientNormalizer.fromLine("100g feta")
        val t2 = DietTagger.tag(withCheese, null, 40)
        assertTrue(DietTag.VEGETARIAN in t2)
        assertFalse(DietTag.VEGAN in t2)
        assertFalse(DietTag.DAIRY_FREE in t2)

        val meat = veg + IngredientNormalizer.fromLine("500g chicken breast")
        val t3 = DietTagger.tag(meat, Nutrition(calories = 420.0, proteinG = 38.0), 40)
        assertFalse(DietTag.VEGETARIAN in t3)
        assertTrue(DietTag.HIGH_PROTEIN in t3)
        assertTrue(DietTag.LOW_CALORIE in t3)
    }

    @Test
    fun dairyExceptionsDoNotBreakVegan() {
        val ings = listOf("400ml coconut milk", "2 tbsp peanut butter", "1 tsp cream of tartar").map { IngredientNormalizer.fromLine(it) }
        assertTrue(DietTag.VEGAN in DietTagger.tag(ings, null, null))
    }

    @Test
    fun normalisesIngredientLines() {
        val ing = IngredientNormalizer.fromLine("2 large onions, finely chopped")
        assertEquals("onions", ing.name)
        assertEquals(2.0, ing.quantity!!, 0.001)
        assertEquals("large", ing.unit)
        assertEquals("finely chopped", ing.note)
        assertEquals(Aisle.PRODUCE, ing.aisle)
        assertEquals("onion", IngredientNormalizer.normalizedKey("onions"))
        assertEquals("tomato", IngredientNormalizer.normalizedKey("tomatoes"))
    }

    @Test
    fun mergesShoppingItems() {
        val a = listOf(IngredientNormalizer.fromLine("2 onions"), IngredientNormalizer.fromLine("200g rice"))
        val b = listOf(IngredientNormalizer.fromLine("1 onion"), IngredientNormalizer.fromLine("300g rice"), IngredientNormalizer.fromLine("1 lemon"))
        val merged = IngredientNormalizer.toShoppingItems("r2", b, 1.0, IngredientNormalizer.toShoppingItems("r1", a, 1.0, emptyList()))
        assertEquals(3, merged.size)
        val onion = merged.first { IngredientNormalizer.normalizedKey(it.name) == "onion" }
        assertEquals(3.0, onion.quantity!!, 0.001)
        assertEquals(listOf("r1", "r2"), onion.recipeIds)
        val rice = merged.first { it.name == "rice" }
        assertEquals(500.0, rice.quantity!!, 0.001)
    }

    @Test
    fun extractsTimers() {
        assertEquals(20 * 60, TimerExtractor.extractSeconds("Simmer for 20 minutes, stirring occasionally."))
        assertEquals(75 * 60, TimerExtractor.extractSeconds("Bake for 1 hour 15 minutes until golden."))
        assertEquals(3 * 60, TimerExtractor.extractSeconds("Fry for 2-3 mins each side"))
        assertNull(TimerExtractor.extractSeconds("Season well and serve."))
        assertEquals("1:15:00", TimerExtractor.format(75 * 60))
        assertEquals("3:00", TimerExtractor.format(180))
    }
}
