package be.rimeco.weekmenu

import be.rimeco.weekmenu.logic.IngredientParser
import be.rimeco.weekmenu.logic.Units
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IngredientParserTest {
    private fun p(s: String) = IngredientParser.parse(s)

    @Test fun gramsWithSpace() {
        val i = p("200 g gerookte zalm")
        assertEquals(200.0, i.quantity!!, 0.001); assertEquals("g", i.unit); assertEquals("gerookte zalm", i.name)
    }

    @Test fun gramsWithoutSpace() {
        val i = p("250g bloem")
        assertEquals(250.0, i.quantity!!, 0.001); assertEquals("g", i.unit); assertEquals("bloem", i.name)
    }

    @Test fun unitWords() {
        val i = p("2 eetlepels olijfolie")
        assertEquals(2.0, i.quantity!!, 0.001); assertEquals("el", i.unit); assertEquals("olijfolie", i.name)
        assertEquals("blik", p("1 blik tomatenblokjes").unit)
        assertEquals("teen", p("3 teentjes knoflook").unit)
    }

    @Test fun countWithoutUnit() {
        val i = p("2 uien")
        assertEquals(2.0, i.quantity!!, 0.001); assertEquals("", i.unit); assertEquals("uien", i.name)
    }

    @Test fun decimalCommaAndFractions() {
        assertEquals(1.5, p("1,5 kg aardappelen").quantity!!, 0.001)
        assertEquals(0.5, p("1/2 courgette").quantity!!, 0.001)
        assertEquals(1.5, p("1 1/2 tl komijn").quantity!!, 0.001)
        assertEquals(0.5, p("½ komkommer").quantity!!, 0.001)
        assertEquals(1.5, p("1½ el honing").quantity!!, 0.001)
    }

    @Test fun rangeTakesUpperBound() {
        assertEquals(3.0, p("2-3 uien").quantity!!, 0.001)
    }

    @Test fun noQuantity() {
        val i = p("Zout en peper")
        assertNull(i.quantity); assertEquals("zout en peper", i.name)
    }

    @Test fun cleansNameDetails() {
        assertEquals("ui", p("1 ui, fijngesnipperd").name)
        assertEquals("tomaten", p("4 tomaten (rijp)").name)
        assertEquals("tomatenblokjes", p("1 blik van tomatenblokjes").name)
    }

    @Test fun htmlEntities() {
        assertEquals("pasta & saus", p("200 g pasta &amp; saus").name)
    }

    @Test fun staples() {
        assertTrue(IngredientParser.isStaple("zout en peper"))
        assertTrue(IngredientParser.isStaple("water"))
        assertTrue(IngredientParser.isStaple("snuf peper en zout"))
        assertTrue(IngredientParser.isStaple("zout en peper naar smaak"))
        assertFalse(IngredientParser.isStaple("zoute boter"))
        assertFalse(IngredientParser.isStaple("peperoni"))
    }

    @Test fun unitFormatting() {
        assertEquals("1,5 kg", Units.pretty(1500.0, "g").let { Units.format(it.first, it.second) })
        assertEquals("200 g", Units.format(200.0, "g"))
        assertEquals("2", Units.format(2.0, ""))
        assertEquals("⅓", Units.format(0.333, ""))
        assertEquals("¾ el", Units.format(0.75, "el"))
        assertEquals("1½ tl", Units.format(1.5, "tl"))
        assertEquals("1,5 kg", Units.format(1.5, "kg"))
    }

    @Test fun shoppingFormattingRoundsUpWhatYouBuyPerPiece() {
        assertEquals("1 blik", Units.formatShopping(0.75, "blik"))
        assertEquals("2", Units.formatShopping(1.5, ""))
        assertEquals("3", Units.formatShopping(3.0, ""))
        assertEquals("190 g", Units.formatShopping(187.5, "g"))
        assertEquals("30 g", Units.formatShopping(30.0, "g"))
        assertEquals("1,8 kg", Units.formatShopping(1.8, "kg"))
        assertEquals("¾ el", Units.formatShopping(0.75, "el"))
        assertEquals("", Units.formatShopping(null, "g"))    }
}
