package be.rimeco.weekmenu.logic

import be.rimeco.weekmenu.data.AppState
import be.rimeco.weekmenu.data.MenuEntry
import be.rimeco.weekmenu.data.Recipe
import be.rimeco.weekmenu.data.Tags
import be.rimeco.weekmenu.data.Week
import be.rimeco.weekmenu.data.defaultPersons
import be.rimeco.weekmenu.data.recipe
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.random.Random

/**
 * Stelt een weekmenu voor op basis van de voorkeuren:
 *  - hogere rating = grotere kans (niet-beoordeeld telt als 3 sterren)
 *  - minimum aantal sterren, uitgesloten tags, voorkeurstags; desserts worden nooit voorgesteld
 *  - hetzelfde recept niet binnen X dagen opnieuw
 *  - streefaantal vegetarische dagen, max bereidingstijd op weekdagen
 *  - afwisseling: niet twee dagen na elkaar vlees / vis / pasta / soep
 */
object MenuPlanner {
    data class Result(val picks: Map<String, String>, val unfilled: List<LocalDate>)

    private fun effRating(r: Recipe) = if (r.rating == 0) 3 else r.rating
    private fun isVeg(r: Recipe) = Tags.VEGETARIAN in r.tags
    private fun isWeekday(d: LocalDate) = d.dayOfWeek != DayOfWeek.SATURDAY && d.dayOfWeek != DayOfWeek.SUNDAY

    /**
     * @param dates de dagen van de week (of 1 dag bij "opnieuw gooien")
     * @param replaceExisting true = ook dagen met een recept opnieuw invullen; false = enkel lege dagen
     */
    fun suggest(state: AppState, dates: List<LocalDate>, replaceExisting: Boolean, rnd: Random = Random.Default): Result {
        val p = state.prefs
        // Context = volledige weken (za-vr) van de gevraagde dagen, zodat "opnieuw gooien" van 1 dag de rest van de week respecteert.
        val window = dates.flatMap { d -> Week.days(Week.startOf(d)).map { it.toString() } }.toSet()

        // Werkmenu: dagen die we gaan (her)invullen zijn leeg.
        val working = state.menu.toMutableMap()
        val targets = dates.sorted().filter { d ->
            val e = state.menu[d.toString()]
            replaceExisting || e == null || (e.recipeId == null && e.customName.isNullOrBlank())
        }
        if (replaceExisting) targets.forEach { d -> state.menu[d.toString()]?.let { working[d.toString()] = it.copy(recipeId = null, customName = null) } }

        val base = state.recipes.filter { r ->
            Tags.DESSERT !in r.tags &&   // een dessert is geen avondmaal
                r.tags.none { it in p.excludedTags } && effRating(r) >= p.minRating
        }
        val picks = linkedMapOf<String, String>()
        val unfilled = mutableListOf<LocalDate>()

        // Dagen met een tijdslimiet eerst: anders kan het weekend de snelle recepten opgebruiken.
        val order = if (p.maxMinutesWeekdays > 0) targets.sortedBy { if (isWeekday(it)) 0 else 1 } else targets
        order.forEachIndexed { index, d ->
            val windowIds = window.mapNotNull { working[it]?.recipeId }.toSet()
            fun repeated(r: Recipe): Boolean {
                if (p.avoidRepeatDays <= 0) return false
                return working.values.any { e ->
                    e.recipeId == r.id && e.date != d.toString() &&
                        abs(ChronoUnit.DAYS.between(LocalDate.parse(e.date), d)) < p.avoidRepeatDays
                }
            }
            fun tooSlow(r: Recipe) = p.maxMinutesWeekdays > 0 && isWeekday(d) && r.minutes > p.maxMinutesWeekdays

            var pool = base.filter { it.id !in windowIds && !repeated(it) && !tooSlow(it) }
            if (pool.isEmpty()) pool = base.filter { it.id !in windowIds && !tooSlow(it) }
            if (pool.isEmpty()) pool = base.filter { it.id !in windowIds }
            if (pool.isEmpty()) { unfilled += d; return@forEachIndexed }

            val remaining = targets.size - index
            val vegHave = window.mapNotNull { state.recipe(working[it]?.recipeId) }.count { isVeg(it) }
            val vegNeeded = (p.vegDaysPerWeek - vegHave).coerceAtLeast(0)
            if (vegNeeded >= remaining && pool.any { isVeg(it) }) pool = pool.filter { isVeg(it) }

            val neighbours = listOfNotNull(
                state.recipe(working[d.minusDays(1).toString()]?.recipeId),
                state.recipe(working[d.plusDays(1).toString()]?.recipeId),
            )
            val weights = pool.map { r ->
                val er = effRating(r).toDouble()
                var w = er * er
                if (r.tags.any { it in p.preferredTags }) w *= 2.5
                if (vegNeeded > 0 && isVeg(r)) w *= 1.0 + 2.0 * vegNeeded / remaining
                if (neighbours.any { n -> r.tags.any { it in Tags.varietyTags && it in n.tags } }) w *= 0.3
                w
            }
            val chosen = weightedPick(pool, weights, rnd)
            picks[d.toString()] = chosen.id
            val existing = working[d.toString()]
            working[d.toString()] = (existing ?: MenuEntry(d.toString(), persons = p.defaultPersons(d)))
                .copy(recipeId = chosen.id, customName = null)
        }
        return Result(picks, unfilled)
    }

    private fun <T> weightedPick(items: List<T>, weights: List<Double>, rnd: Random): T {
        val total = weights.sum()
        var x = rnd.nextDouble() * total
        for (i in items.indices) {
            x -= weights[i]
            if (x <= 0) return items[i]
        }
        return items.last()
    }
}
