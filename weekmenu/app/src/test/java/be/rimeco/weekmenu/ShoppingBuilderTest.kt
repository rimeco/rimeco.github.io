package be.rimeco.weekmenu

import be.rimeco.weekmenu.data.AppState
import be.rimeco.weekmenu.data.MenuEntry
import be.rimeco.weekmenu.data.Recipe
import be.rimeco.weekmenu.logic.IngredientParser
import be.rimeco.weekmenu.logic.ShoppingBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ShoppingBuilderTest {
    private var n = 0
    private val ids = { "id${n++}" }

    private fun recipe(id: String, title: String, servings: Int, vararg lines: String) =
        Recipe(id, title, servings = servings, ingredients = lines.map { IngredientParser.parse(it) })

    private val mon = LocalDate.of(2026, 11, 2)

    private fun state(): AppState {
        val a = recipe("a", "Pasta zalm", 4, "400 g penne", "200 g gerookte zalm", "1 ui", "zout en peper", "500 g spinazie")
        val b = recipe("b", "Spinaziesoep", 2, "250 g spinazie", "1 ui", "1 l bouillon")
        return AppState(
            recipes = listOf(a, b),
            menu = mapOf(
                mon.toString() to MenuEntry(mon.toString(), "a", persons = 3),
                mon.plusDays(1).toString() to MenuEntry(mon.plusDays(1).toString(), "b", persons = 4),
            ),
        )
    }

    @Test fun scalesAndMergesIngredients() {
        val (s, count) = ShoppingBuilder.addMenu(state(), listOf(mon, mon.plusDays(1)), 1L, ids)
        assertEquals(2, count)
        val byName = s.shopping.associateBy { it.name }
        assertEquals(300.0, byName["penne"]!!.quantity!!, 0.001)       // 400 g × 3/4
        assertEquals(2.75, byName["ui"]!!.quantity!!, 0.001)           // 1 × 3/4  +  1 × 4/2
        // spinazie: 500 × 0.75 = 375 g  +  250 × 2 = 500 g  = 875 g
        assertEquals(875.0, byName["spinazie"]!!.quantity!!, 0.001)
        assertEquals("g", byName["spinazie"]!!.unit)
        assertEquals("Pasta zalm, Spinaziesoep", byName["spinazie"]!!.note)
        assertEquals(2.0, byName["bouillon"]!!.quantity!!, 0.001)      // 1 l × 2
        assertTrue("zout en peper wordt overgeslagen", byName["zout en peper"] == null)
    }

    @Test fun doesNotAddSameMealTwice() {
        val (s1, _) = ShoppingBuilder.addMenu(state(), listOf(mon), 1L, ids)
        val (s2, c2) = ShoppingBuilder.addMenu(s1, listOf(mon), 2L, ids)
        assertEquals(0, c2)
        assertEquals(s1.shopping, s2.shopping)
    }

    @Test fun convertsUnitsWhenMerging() {
        val st = AppState(shopping = emptyList())
        val s1 = ShoppingBuilder.addManual(st, "800 g aardappelen", 1L, ids)
        val s2 = ShoppingBuilder.addManual(s1, "1 kg aardappelen", 2L, ids)
        assertEquals(1, s2.shopping.size)
        assertEquals(1.8, s2.shopping[0].quantity!!, 0.001)
        assertEquals("kg", s2.shopping[0].unit)
    }

    @Test fun manualAddParsesQuantity() {
        val s = ShoppingBuilder.addManual(AppState(), "2 kg appels", 1L, ids)
        assertEquals("appels", s.shopping[0].name); assertEquals(2.0, s.shopping[0].quantity!!, 0.001); assertEquals("kg", s.shopping[0].unit)
    }

    @Test fun checkingRemembersItemInHistoryAndCanBeReAdded() {
        var s = ShoppingBuilder.addManual(AppState(), "Kerstomaatjes", 1L, ids)
        val id = s.shopping[0].id
        s = ShoppingBuilder.toggle(s, id, 10L)
        assertTrue(s.shopping[0].checked)
        assertEquals(1, s.history.size); assertEquals("kerstomaatjes", s.history[0].name); assertEquals(1, s.history[0].count)

        s = ShoppingBuilder.clearChecked(s, 11L)
        assertTrue(s.shopping.isEmpty()); assertEquals(1, s.history.size)

        val sug = ShoppingBuilder.suggestions(s, "kers")
        assertEquals(1, sug.size)
        s = ShoppingBuilder.addFromHistory(s, sug[0], 12L, ids)
        assertEquals(1, s.shopping.size); assertTrue(!s.shopping[0].checked)
        // staat nu op de lijst -> geen suggestie meer
        assertTrue(ShoppingBuilder.suggestions(s, "").isEmpty())

        s = ShoppingBuilder.toggle(s, s.shopping[0].id, 13L)
        assertEquals(2, s.history[0].count)
    }

    @Test fun uncheckingDoesNotCountAgain() {
        var s = ShoppingBuilder.addManual(AppState(), "melk", 1L, ids)
        val id = s.shopping[0].id
        s = ShoppingBuilder.toggle(s, id, 2L)
        s = ShoppingBuilder.toggle(s, id, 3L)
        assertEquals(1, s.history[0].count)
        assertNotNull(s.shopping.firstOrNull { !it.checked })
    }
}
