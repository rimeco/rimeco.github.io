package be.rimeco.weekmenu

import be.rimeco.weekmenu.data.AppState
import be.rimeco.weekmenu.data.HistoryItem
import be.rimeco.weekmenu.data.MenuEntry
import be.rimeco.weekmenu.data.Recipe
import be.rimeco.weekmenu.data.ShoppingItem
import be.rimeco.weekmenu.sync.SyncModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncModelTest {
    private val state = AppState(
        recipes = listOf(Recipe("r2", "B", createdAt = 2), Recipe("r1", "A", createdAt = 1)),
        menu = mapOf("2026-11-07" to MenuEntry("2026-11-07", "r1", persons = 4)),
        shopping = listOf(ShoppingItem("s2", "melk", addedAt = 5), ShoppingItem("s1", "brood", addedAt = 3, checked = true)),
        history = listOf(HistoryItem("rijst/pasta", 2, 10L), HistoryItem(".", 1, 1L)),
        addedToList = setOf("2026-11-07:r1"),
    )

    @Test fun everyCollectionSurvivesEncodeAndApply() {
        var rebuilt = AppState()
        for (c in SyncModel.collections) rebuilt = c.applyRemote(rebuilt, c.encodeAll(state))
        assertEquals(state.recipes.sortedBy { it.createdAt }, rebuilt.recipes)
        assertEquals(state.menu, rebuilt.menu)
        assertEquals(state.shopping.sortedBy { it.addedAt }, rebuilt.shopping)   // zelfde volgorde op elk toestel
        assertEquals(state.history.toSet(), rebuilt.history.toSet())
        assertEquals(state.addedToList, rebuilt.addedToList)
    }

    @Test fun documentIdsAreValidForFirestore() {
        for (c in SyncModel.collections) for (id in c.encodeAll(state).keys) {
            assertFalse("'/' in $id", id.contains('/'))
            assertTrue(id != "." && id != ".." && id.isNotEmpty())
        }
    }

    @Test fun diffFindsOnlyWhatChanged() {
        val old = mapOf("a" to "1", "b" to "2", "c" to "3")
        val new = mapOf("a" to "1", "b" to "2x", "d" to "4")
        val d = SyncModel.diff(old, new)
        assertEquals(mapOf("b" to "2x", "d" to "4"), d.upserts)
        assertEquals(setOf("c"), d.deletes)
        assertTrue(SyncModel.diff(old, old).isEmpty)
    }

    @Test fun remoteApplyIsStableSoItIsNeverPushedBack() {
        // Na het toepassen van externe gegevens moet opnieuw coderen exact dezelfde JSON geven (anders ping-pong tussen toestellen).
        for (c in SyncModel.collections) {
            val enc = c.encodeAll(state)
            val next = c.applyRemote(AppState(), enc)
            assertEquals(enc, c.encodeAll(next))
        }
    }

    @Test fun unknownFieldsAndBrokenDocsAreIgnored() {
        val c = SyncModel.collections.first { it.name == "recipes" }
        val s = c.applyRemote(AppState(), mapOf("x" to """{"id":"x","title":"T","nieuwVeld":1}""", "y" to "kapot{"))
        assertEquals(listOf("T"), s.recipes.map { it.title })
    }

    @Test fun householdCodes() {
        val code = SyncModel.generateCode()
        assertEquals(16, code.length)
        assertEquals(code, SyncModel.normalizeCode(SyncModel.formatCode(code).lowercase()))
        assertEquals("ABCD-EFGH-JKMN-PQRS", SyncModel.formatCode("ABCDEFGHJKMNPQRS"))
        assertNull(SyncModel.normalizeCode("te-kort"))
        assertNull(SyncModel.normalizeCode("ABCD-EFGH-JKMN-PQR0"))   // 0 komt niet voor in de codes
        assertEquals(20, (1..20).map { SyncModel.generateCode() }.toSet().size)
    }
}
