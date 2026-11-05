package be.rimeco.weekmenu

import be.rimeco.weekmenu.data.AppState
import be.rimeco.weekmenu.data.MenuEntry
import be.rimeco.weekmenu.data.Persistence
import be.rimeco.weekmenu.data.Prefs
import be.rimeco.weekmenu.data.Recipe
import be.rimeco.weekmenu.data.Tags
import be.rimeco.weekmenu.data.Week
import be.rimeco.weekmenu.data.defaultPersons
import be.rimeco.weekmenu.data.entryFor
import be.rimeco.weekmenu.logic.MenuPlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import kotlin.random.Random

class MenuPlannerTest {
    private val mon = LocalDate.of(2026, 11, 7) // zaterdag = eerste dag van de planningsweek
    private val week = (0L..6L).map { mon.plusDays(it) }

    private fun r(id: String, rating: Int = 0, tags: List<String> = emptyList(), minutes: Int = 0) =
        Recipe(id, "Recept $id", rating = rating, tags = tags, minutes = minutes)

    private fun state(recipes: List<Recipe>, prefs: Prefs = Prefs(), menu: Map<String, MenuEntry> = emptyMap()) =
        AppState(recipes = recipes, menu = menu, prefs = prefs)

    @Test fun defaultPersonsFollowWeekday() {
        val p = Prefs()
        assertEquals(4, p.defaultPersons(mon))               // zaterdag
        assertEquals(4, p.defaultPersons(mon.plusDays(1)))   // zondag
        assertEquals(3, p.defaultPersons(mon.plusDays(2)))   // maandag
        assertEquals(3, p.defaultPersons(mon.plusDays(6)))   // vrijdag
        assertEquals(4, state(emptyList()).entryFor(mon).persons)
    }

    @Test fun weekRunsFromSaturdayToFriday() {
        assertEquals(mon, Week.startOf(LocalDate.of(2026, 11, 7)))   // zaterdag
        assertEquals(mon, Week.startOf(LocalDate.of(2026, 11, 13)))  // vrijdag
        assertEquals(mon.plusDays(7), Week.startOf(LocalDate.of(2026, 11, 14)))
        assertEquals(java.time.DayOfWeek.FRIDAY, Week.days(mon).last().dayOfWeek)
    }

    @Test fun fillsWeekWithoutRepeats() {
        val s = state((1..10).map { r("r$it", 4) })
        val res = MenuPlanner.suggest(s, week, replaceExisting = true, rnd = Random(1))
        assertEquals(7, res.picks.size)
        assertEquals(7, res.picks.values.toSet().size)
        assertTrue(res.unfilled.isEmpty())
    }

    @Test fun reportsUnfilledWhenNotEnoughRecipes() {
        val res = MenuPlanner.suggest(state(listOf(r("a"), r("b"))), week, true, Random(2))
        assertEquals(2, res.picks.size); assertEquals(5, res.unfilled.size)
    }

    @Test fun respectsMinRatingAndExcludedTags() {
        val s = state(
            listOf(r("good", 5), r("bad", 1), r("fish", 5, listOf(Tags.FISH)), r("ok", 4)),
            Prefs(minRating = 4, excludedTags = listOf(Tags.FISH)),
        )
        repeat(20) { seed ->
            val res = MenuPlanner.suggest(s, week, true, Random(seed))
            assertTrue(res.picks.values.all { it == "good" || it == "ok" })
        }
    }

    @Test fun onlyFillsEmptyDaysAndKeepsCustomMeals() {
        val menu = mapOf(
            mon.toString() to MenuEntry(mon.toString(), "keep", persons = 3),
            mon.plusDays(1).toString() to MenuEntry(mon.plusDays(1).toString(), customName = "Restaurant", persons = 2),
        )
        val s = state((1..8).map { r("r$it") } + r("keep"), menu = menu)
        val res = MenuPlanner.suggest(s, week, replaceExisting = false, rnd = Random(3))
        assertFalse(res.picks.containsKey(mon.toString()))
        assertFalse(res.picks.containsKey(mon.plusDays(1).toString()))
        assertEquals(5, res.picks.size)
        assertFalse("keep" in res.picks.values)  // al in de week gepland
    }

    @Test fun avoidsRecentRepeatsFromPreviousWeek() {
        val prevDay = mon.minusDays(3)
        val menu = mapOf(prevDay.toString() to MenuEntry(prevDay.toString(), "recent", persons = 3))
        val s = state(listOf(r("recent", 5), r("a"), r("b"), r("c"), r("d"), r("e"), r("f"), r("g")), Prefs(avoidRepeatDays = 21), menu)
        repeat(10) { seed ->
            val res = MenuPlanner.suggest(s, week, true, Random(seed))
            assertFalse("recent" in res.picks.values)
        }
    }

    @Test fun reachesVegetarianTarget() {
        val recipes = (1..6).map { r("m$it", 3, listOf(Tags.MEAT)) } + (1..6).map { r("v$it", 3, listOf(Tags.VEGETARIAN)) }
        val s = state(recipes, Prefs(vegDaysPerWeek = 3))
        repeat(15) { seed ->
            val res = MenuPlanner.suggest(s, week, true, Random(seed))
            assertTrue(res.picks.values.count { it.startsWith("v") } >= 3)
        }
    }

    @Test fun respectsMaxMinutesOnWeekdaysOnly() {
        val recipes = listOf(r("fast1", minutes = 20), r("fast2", minutes = 25), r("fast3", minutes = 30), r("fast4", minutes = 15), r("fast5", minutes = 20),
            r("slow1", minutes = 90), r("slow2", minutes = 120))
        val s = state(recipes, Prefs(maxMinutesWeekdays = 30, avoidRepeatDays = 0))
        repeat(10) { seed ->
            val res = MenuPlanner.suggest(s, week, true, Random(seed))
            for ((date, id) in res.picks) {
                val d = LocalDate.parse(date)
                if (d.dayOfWeek.value <= 5) assertTrue("$id op $d", id.startsWith("fast"))
            }
        }
    }

    @Test fun rerollSingleDayKeepsRestOfWeekUnique() {
        val recipes = (1..9).map { r("r$it", 3) }
        val menu = week.take(6).mapIndexed { i, d -> d.toString() to MenuEntry(d.toString(), "r${i + 1}", persons = 3) }.toMap()
        val s = state(recipes, menu = menu)
        val sunday = week[6]
        repeat(10) { seed ->
            val res = MenuPlanner.suggest(s, listOf(sunday), true, Random(seed))
            val pick = res.picks[sunday.toString()]!!
            assertTrue(pick in setOf("r7", "r8", "r9"))
        }
    }

    @Test fun higherRatingsArePickedMoreOften() {
        val s = state(listOf(r("five", 5), r("one", 1)), Prefs(avoidRepeatDays = 0))
        var five = 0
        repeat(500) { seed ->
            val res = MenuPlanner.suggest(s, listOf(mon), true, Random(seed))
            if (res.picks[mon.toString()] == "five") five++
        }
        assertTrue("five=$five", five > 400)  // verwacht ~96%
    }

    @Test fun stateSurvivesJsonRoundTrip() {
        val p = Persistence(File.createTempFile("weekmenu", ".json"))
        val s = state(listOf(r("a", 3, listOf("Vis"))), Prefs(vegDaysPerWeek = 2), mapOf(mon.toString() to MenuEntry(mon.toString(), "a", persons = 5)))
        assertEquals(s, p.decode(p.encode(s)))
    }

    @Test fun neverProposesDessertsAsADinner() {
        val s = state(listOf(r("soep"), r("brownie", 5, listOf(Tags.DESSERT)), r("pasta"), r("curry")), Prefs(avoidRepeatDays = 0))
        repeat(20) { seed ->
            val res = MenuPlanner.suggest(s, week, true, Random(seed))
            assertFalse("brownie" in res.picks.values)
        }
    }
}
