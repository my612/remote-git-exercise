package com.mobuk.app

import com.mobuk.app.domain.logic.QuantityFormatter
import com.mobuk.app.domain.logic.QuantityParser
import com.mobuk.app.domain.logic.ServingScaler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuantityParserTest {

    @Test
    fun parsesWholeNumbersAndUnits() {
        val p = QuantityParser.parse("2 tbsp olive oil")
        assertEquals(2.0, p.amount!!, 0.001)
        assertEquals("tbsp", p.unit)
        assertEquals("olive oil", p.rest)
    }

    @Test
    fun parsesMixedFractions() {
        val p = QuantityParser.parse("1 1/2 cups plain flour")
        assertEquals(1.5, p.amount!!, 0.001)
        assertEquals("cup", p.unit)
        assertEquals("plain flour", p.rest)
    }

    @Test
    fun parsesUnicodeFractions() {
        assertEquals(0.5, QuantityParser.parse("½ tsp salt").amount!!, 0.001)
        assertEquals(1.25, QuantityParser.parse("1¼ cups milk").amount!!, 0.001)
    }

    @Test
    fun parsesGluedMetricUnits() {
        val p = QuantityParser.parse("200g chicken thighs")
        assertEquals(200.0, p.amount!!, 0.001)
        assertEquals("g", p.unit)
        assertEquals("chicken thighs", p.rest)
        val ml = QuantityParser.parse("400ml coconut milk")
        assertEquals("ml", ml.unit)
    }

    @Test
    fun parsesRanges() {
        val p = QuantityParser.parse("2-3 cloves garlic")
        assertEquals(2.0, p.amount!!, 0.001)
        assertEquals(3.0, p.upperAmount!!, 0.001)
        assertEquals("clove", p.unit)
        assertEquals("garlic", p.rest)
    }

    @Test
    fun parsesVagueAmounts() {
        val p = QuantityParser.parse("a pinch of salt")
        assertNull(p.amount)
        assertEquals("pinch", p.unit)
        assertEquals("salt", p.rest)
        val t = QuantityParser.parse("to taste")
        assertNull(t.amount)
    }

    @Test
    fun themealdbMeasuresWithoutName() {
        val p = QuantityParser.parse("1 tsp")
        assertEquals(1.0, p.amount!!, 0.001)
        assertEquals("tsp", p.unit)
        assertEquals("", p.rest)
    }

    @Test
    fun formatsFractionsNicely() {
        assertEquals("1½", QuantityFormatter.format(1.5))
        assertEquals("⅓", QuantityFormatter.format(1.0 / 3))
        assertEquals("200", QuantityFormatter.format(200.0))
        assertEquals("2⅜", QuantityFormatter.format(2.37))
        assertEquals("2.2", QuantityFormatter.format(2.2))
        assertEquals("200g", QuantityFormatter.formatWithUnit(200.0, "g"))
        assertEquals("2 tbsp", QuantityFormatter.formatWithUnit(2.0, "tbsp"))
    }

    @Test
    fun scalesServings() {
        assertEquals(3.0, ServingScaler.scaleAmount(2.0, 4, 6)!!, 0.001)
        assertEquals(2.0, ServingScaler.scaleAmount(2.0, null, 6)!!, 0.001)
        assertNull(ServingScaler.scaleAmount(null, 4, 6))
    }
}
