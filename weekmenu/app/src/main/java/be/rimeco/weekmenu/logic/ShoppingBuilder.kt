package be.rimeco.weekmenu.logic

import be.rimeco.weekmenu.data.AppState
import be.rimeco.weekmenu.data.HistoryItem
import be.rimeco.weekmenu.data.Recipe
import be.rimeco.weekmenu.data.ShoppingItem
import be.rimeco.weekmenu.data.recipe
import java.time.LocalDate
import java.util.UUID

/** Bouwt en onderhoudt de boodschappenlijst en de historiek van eerder gekochte items. */
object ShoppingBuilder {
    /** Een samengevoegde regel in basiseenheden (g/ml/...). */
    data class Line(
        val name: String,
        val dimKey: String,
        val quantity: Double?,
        val baseUnit: String,
        val notes: LinkedHashSet<String>,
    )

    private fun newId() = UUID.randomUUID().toString()

    // ---------- notities (receptnamen bij een item) ----------
    const val NOTE_SEP = " · "

    fun splitNote(note: String): List<String> =
        if (note.contains(NOTE_SEP)) note.split(NOTE_SEP).filter { it.isNotBlank() } else listOfNotNull(note.takeIf { it.isNotBlank() })

    fun joinNote(titles: Collection<String>): String = titles.joinToString(NOTE_SEP)

    /** Korte versie voor in de lijst: hoogstens [maxTitles] receptnamen, elk ingekort, plus "+N" voor de rest. */
    fun shortNote(note: String, maxTitles: Int = 2, maxChars: Int = 22): String {
        val titles = splitNote(note)
        if (titles.isEmpty()) return ""
        fun cut(t: String): String {
            if (t.length <= maxChars) return t
            val head = t.take(maxChars - 1)
            val space = head.lastIndexOf(' ')
            // liefst op een woordgrens afkappen ("Gevulde paprika met…" i.p.v. "Gevulde paprika met c…")
            return (if (space >= 8) head.take(space) else head).trimEnd(' ', ',', '-') + "…"
        }
        val shown = titles.take(maxTitles).joinToString(NOTE_SEP) { cut(it) }
        return if (titles.size > maxTitles) "$shown +${titles.size - maxTitles}" else shown
    }

    private fun scaledLines(recipe: Recipe, persons: Int, skipStaples: Boolean): List<Line> {
        val factor = persons.toDouble() / recipe.servings.coerceAtLeast(1)
        return recipe.ingredients
            .filter { !(skipStaples && IngredientParser.isStaple(it.name)) }
            .map { ing ->
                val base = ing.quantity?.let { Units.toBase(it * factor, ing.unit) }
                val dim = Units.dimOf(ing.unit)
                Line(ing.name, dim.key, base?.first, base?.second ?: dim.baseUnit, linkedSetOf(recipe.title))
            }
    }

    private fun mergeLines(lines: List<Line>): List<Line> {
        val out = LinkedHashMap<String, Line>()
        for (l in lines) {
            val key = l.name + "|" + l.dimKey
            val ex = out[key]
            out[key] = if (ex == null) l else ex.copy(
                quantity = if (ex.quantity != null && l.quantity != null) ex.quantity + l.quantity else ex.quantity ?: l.quantity,
                notes = LinkedHashSet(ex.notes + l.notes),
            )
        }
        return out.values.toList()
    }

    /** Zet alle nog niet toegevoegde maaltijden uit [dates] op de lijst. Geeft de nieuwe staat + aantal maaltijden terug. */
    fun addMenu(state: AppState, dates: List<LocalDate>, now: Long, idGen: () -> String = ::newId): Pair<AppState, Int> {
        val keys = mutableSetOf<String>()
        val lines = mutableListOf<Line>()
        for (d in dates) {
            val entry = state.menu[d.toString()] ?: continue
            val recipe = state.recipe(entry.recipeId) ?: continue
            val key = "${d}:${recipe.id}"
            if (key in state.addedToList) continue
            keys += key
            lines += scaledLines(recipe, entry.persons, state.prefs.skipStaples)
        }
        if (keys.isEmpty()) return state to 0
        val items = mergeInto(state.shopping, mergeLines(lines), now, idGen)
        return state.copy(shopping = items, addedToList = state.addedToList + keys) to keys.size
    }

    fun addRecipe(state: AppState, recipe: Recipe, persons: Int, now: Long, idGen: () -> String = ::newId): AppState {
        val lines = mergeLines(scaledLines(recipe, persons, state.prefs.skipStaples))
        return state.copy(shopping = mergeInto(state.shopping, lines, now, idGen))
    }

    fun mergeInto(existing: List<ShoppingItem>, lines: List<Line>, now: Long, idGen: () -> String = ::newId): List<ShoppingItem> {
        val items = existing.toMutableList()
        for (l in lines) {
            val idx = items.indexOfFirst { !it.checked && it.name.lowercase() == l.name && Units.dimOf(it.unit).key == l.dimKey }
            val note = joinNote(l.notes)
            if (idx >= 0) {
                val ex = items[idx]
                val exBase = ex.quantity?.let { Units.toBase(it, ex.unit) }
                val total = when {
                    exBase != null && l.quantity != null -> exBase.first + l.quantity
                    exBase != null -> exBase.first
                    else -> l.quantity
                }
                val pretty = total?.let { Units.pretty(it, l.baseUnit) }
                items[idx] = ex.copy(
                    quantity = pretty?.first?.let { Units.clean(it) },
                    unit = pretty?.second ?: ex.unit,
                    note = joinNote((splitNote(ex.note) + l.notes).distinct()),
                    manual = false,
                )
            } else {
                val pretty = l.quantity?.let { Units.pretty(it, l.baseUnit) }
                items += ShoppingItem(
                    id = idGen(),
                    name = l.name,
                    quantity = pretty?.first?.let { Units.clean(it) },
                    unit = pretty?.second ?: l.baseUnit,
                    note = note,
                    addedAt = now,
                )
            }
        }
        return items
    }

    /** Handmatig toevoegen; "2 kg appels" wordt als hoeveelheid + naam herkend. */
    fun addManual(state: AppState, text: String, now: Long, idGen: () -> String = ::newId): AppState {
        if (text.isBlank()) return state
        val ing = IngredientParser.parse(text)
        val lines = listOf(
            Line(ing.name, Units.dimOf(ing.unit).key, ing.quantity?.let { Units.toBase(it, ing.unit).first }, Units.dimOf(ing.unit).baseUnit, linkedSetOf()),
        )
        return state.copy(shopping = mergeInto(state.shopping, lines, now, idGen))
    }

    /** Voegt een item uit de historiek (opnieuw) toe, met de laatst gebruikte hoeveelheid. */
    fun addFromHistory(state: AppState, item: HistoryItem, now: Long, idGen: () -> String = ::newId): AppState {
        val base = item.quantity?.let { Units.toBase(it, item.unit) }
        val line = Line(item.name.lowercase(), Units.dimOf(item.unit).key, base?.first, base?.second ?: Units.dimOf(item.unit).baseUnit, linkedSetOf())
        return state.copy(shopping = mergeInto(state.shopping, listOf(line), now, idGen))
    }

    // ---------- aanvullen tijdens het typen ----------
    data class Suggestion(val name: String, val history: HistoryItem?, val label: String)

    /**
     * Suggesties tijdens het typen: eerder gekochte items én ingrediënten uit je recepten, beginnend-met eerst.
     * Items die al op de (niet-afgevinkte) lijst staan worden weggelaten. Een getypte hoeveelheid ("2 kg kno") telt niet mee.
     */
    fun autocomplete(state: AppState, text: String, limit: Int = 6): List<Suggestion> {
        val typed = IngredientParser.parse(text)
        val q = (if (typed.quantity != null) typed.name else text.trim().lowercase()).trim()
        if (q.length < 2) return emptyList()
        val onList = state.shopping.filter { !it.checked }.map { it.name.lowercase() }.toSet()
        val history = state.history.associateBy { it.name }
        val recipeCounts = HashMap<String, Int>()
        state.recipes.forEach { r -> r.ingredients.map { it.name.lowercase() }.filter { it.isNotBlank() }.distinct().forEach { recipeCounts.merge(it, 1, Int::plus) } }
        fun score(name: String) = when {
            name.startsWith(q) -> 3
            name.split(' ', '-').any { it.startsWith(q) } -> 2
            name.contains(q) -> 1
            else -> 0
        }
        return (history.keys + recipeCounts.keys).distinct()
            .filter { it !in onList && score(it) > 0 && !IngredientParser.isStaple(it) }
            .sortedWith(
                compareByDescending<String> { score(it) }
                    .thenByDescending { it in history }
                    .thenByDescending { (history[it]?.count ?: 0) + (recipeCounts[it] ?: 0) }
                    .thenBy { it.length },
            )
            .take(limit)
            .map { n ->
                val h = history[n]
                Suggestion(n, h, if (h != null) "eerder gekocht (${h.count}×)" else "uit je recepten")
            }
    }

    fun toggle(state: AppState, id: String, now: Long): AppState {
        val item = state.shopping.firstOrNull { it.id == id } ?: return state
        val checked = !item.checked
        val shopping = state.shopping.map { if (it.id == id) it.copy(checked = checked) else it }
        val history = if (checked) recordHistory(state.history, item, now) else state.history
        return state.copy(shopping = shopping, history = history)
    }

    fun recordHistory(history: List<HistoryItem>, item: ShoppingItem, now: Long): List<HistoryItem> {
        val key = item.name.lowercase()
        val ex = history.firstOrNull { it.name == key }
        val updated = HistoryItem(
            name = key,
            count = (ex?.count ?: 0) + 1,
            lastUsed = now,
            quantity = item.quantity ?: ex?.quantity,
            unit = if (item.quantity != null) item.unit else ex?.unit ?: "",
        )
        return history.filter { it.name != key } + updated
    }

    /** Alle afgevinkte items van de lijst halen; ze blijven in de historiek. */
    fun clearChecked(state: AppState, now: Long): AppState {
        var history = state.history
        // Items die afgevinkt werden vóór de historiek bestond (of via import) alsnog onthouden.
        for (i in state.shopping.filter { it.checked }) {
            if (history.none { it.name == i.name.lowercase() }) history = recordHistory(history, i, now)
        }
        return state.copy(shopping = state.shopping.filter { !it.checked }, history = history)
    }

    /** Historiek, meest gebruikte eerst; items die al op de (niet-afgevinkte) lijst staan worden weggelaten. */
    fun suggestions(state: AppState, query: String, limit: Int = 12): List<HistoryItem> {
        val q = query.trim().lowercase()
        val onList = state.shopping.filter { !it.checked }.map { it.name.lowercase() }.toSet()
        return state.history
            .filter { it.name !in onList }
            .filter { q.isEmpty() || it.name.contains(q) }
            .sortedWith(
                compareByDescending<HistoryItem> { q.isNotEmpty() && it.name.startsWith(q) }
                    .thenByDescending { it.count }
                    .thenByDescending { it.lastUsed },
            )
            .take(limit)
    }
}
