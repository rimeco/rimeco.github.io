package be.rimeco.weekmenu.ui

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import be.rimeco.weekmenu.data.AppState
import be.rimeco.weekmenu.data.HistoryItem
import be.rimeco.weekmenu.data.MenuEntry
import be.rimeco.weekmenu.data.Persistence
import be.rimeco.weekmenu.data.Prefs
import be.rimeco.weekmenu.data.Recipe
import be.rimeco.weekmenu.data.defaultPersons
import be.rimeco.weekmenu.data.entryFor
import be.rimeco.weekmenu.logic.IngredientParser
import be.rimeco.weekmenu.logic.MenuPlanner
import be.rimeco.weekmenu.logic.RecipeImporter
import be.rimeco.weekmenu.logic.ShoppingBuilder
import be.rimeco.weekmenu.sync.SyncEngine
import be.rimeco.weekmenu.sync.SyncInfo
import be.rimeco.weekmenu.sync.SyncModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.time.LocalDate
import java.util.UUID

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Persistence(File(app.filesDir, "weekmenu.json"))
    private val _state = MutableStateFlow(store.load())
    val state: StateFlow<AppState> = _state.asStateFlow()

    /** Schermen bovenop de tabbladen. */
    val stack = mutableStateListOf<Route>()

    private fun now() = System.currentTimeMillis()

    private val _syncInfo = MutableStateFlow(SyncInfo())
    val syncInfo: StateFlow<SyncInfo> = _syncInfo.asStateFlow()

    /** Raakt Firebase pas aan als er een gezin ingesteld is. */
    private val sync = SyncEngine(
        getState = { _state.value },
        applyRemote = { s -> _state.value = s; store.save { _state.value } },
        onInfo = { _syncInfo.value = it },
    )

    init {
        val code = _state.value.prefs.householdId
        if (code.isNotBlank()) viewModelScope.launch { sync.resume(code) }
    }

    private fun update(f: (AppState) -> AppState) {
        _state.update(f)
        store.save { _state.value }
        sync.onLocalChange(_state.value)
    }

    // ---------- gezin / synchronisatie ----------
    /** Maakt een nieuw gezin en uploadt de lokale gegevens. */
    fun createHousehold(onResult: (Result<String>) -> Unit) {
        viewModelScope.launch {
            val r = runCatching { sync.create() }.fold(
                onSuccess = { code -> update { it.copy(prefs = it.prefs.copy(householdId = code)) }; Result.success(code) },
                onFailure = { e -> Result.failure(Exception(SyncEngine.friendly(e))) },
            )
            onResult(r)
        }
    }

    /** Sluit aan bij een bestaand gezin; lokale recepten/menu/lijst worden vervangen door die van het gezin. */
    fun joinHousehold(code: String, onResult: (Result<String>) -> Unit) {
        viewModelScope.launch {
            val r = runCatching { sync.join(code) }.fold(
                onSuccess = { c -> update { it.copy(prefs = it.prefs.copy(householdId = c)) }; Result.success(c) },
                onFailure = { e -> Result.failure(Exception(SyncEngine.friendly(e))) },
            )
            onResult(r)
        }
    }

    /** Stopt met synchroniseren; de gegevens blijven als lokale kopie op dit toestel. */
    fun leaveHousehold() {
        sync.stop()
        update { it.copy(prefs = it.prefs.copy(householdId = "")) }
    }

    fun formatCode(code: String) = SyncModel.formatCode(code)

    // ---------- navigatie ----------
    fun push(r: Route) { stack.add(r) }
    fun pop() { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }
    fun replaceTop(r: Route) { if (stack.isNotEmpty()) stack[stack.lastIndex] = r else stack.add(r) }

    /** Tekst gedeeld vanuit een browser/app ("Delen" -> Weekmenu). */
    fun handleShared(text: String) {
        val url = RecipeImporter.extractUrl(text) ?: return
        stack.clear()
        stack.add(Route.Import(url))
    }

    // ---------- menu ----------
    fun setPersons(date: LocalDate, persons: Int) = update { s ->
        val e = s.entryFor(date).copy(persons = persons.coerceIn(1, 20))
        s.copy(menu = s.menu + (e.date to e))
    }

    fun setMeal(date: LocalDate, recipeId: String?, customName: String? = null) = update { s ->
        val e = s.entryFor(date).copy(recipeId = recipeId, customName = customName?.trim()?.takeIf { it.isNotEmpty() })
        s.copy(menu = s.menu + (e.date to e))
    }

    fun clearMeal(date: LocalDate) = update { s ->
        val e = s.entryFor(date).copy(recipeId = null, customName = null)
        if (e.persons == s.prefs.defaultPersons(date)) s.copy(menu = s.menu - e.date)
        else s.copy(menu = s.menu + (e.date to e))
    }

    /** Geeft het aantal ingevulde dagen terug plus het vorige menu (voor "ongedaan maken"). */
    fun suggest(dates: List<LocalDate>, replaceExisting: Boolean): Pair<Int, Map<String, MenuEntry>> {
        val before = _state.value
        val result = MenuPlanner.suggest(before, dates, replaceExisting)
        if (result.picks.isEmpty()) return 0 to before.menu
        update { s ->
            var menu = s.menu
            for ((date, recipeId) in result.picks) {
                val d = LocalDate.parse(date)
                val e = s.entryFor(d).copy(recipeId = recipeId, customName = null)
                menu = menu + (date to e)
            }
            s.copy(menu = menu)
        }
        return result.picks.size to before.menu
    }

    fun restoreMenu(menu: Map<String, MenuEntry>) = update { it.copy(menu = menu) }

    // ---------- recepten ----------
    suspend fun fetchDraft(url: String): Recipe {
        val d = RecipeImporter.fetchDraft(url)
        return Recipe(
            id = UUID.randomUUID().toString(),
            title = d.title,
            url = RecipeImporter.normalizeUrl(url),
            imageUrl = d.imageUrl,
            servings = d.servings,
            minutes = d.minutes,
            ingredients = d.ingredientLines.map { IngredientParser.parse(it) },
            tags = d.tags,
            createdAt = now(),
        )
    }

    fun blankRecipe() = Recipe(id = UUID.randomUUID().toString(), title = "", createdAt = now())

    fun upsertRecipe(r: Recipe) = update { s ->
        val exists = s.recipes.any { it.id == r.id }
        s.copy(recipes = if (exists) s.recipes.map { if (it.id == r.id) r else it } else s.recipes + r)
    }

    fun deleteRecipe(id: String) = update { s ->
        s.copy(
            recipes = s.recipes.filter { it.id != id },
            menu = s.menu.mapValues { (_, e) -> if (e.recipeId == id) e.copy(recipeId = null) else e },
        )
    }

    fun setRating(id: String, rating: Int) = update { s ->
        s.copy(recipes = s.recipes.map { if (it.id == id) it.copy(rating = rating.coerceIn(0, 5)) else it })
    }

    // ---------- boodschappen ----------
    fun addManual(text: String) = update { ShoppingBuilder.addManual(it, text, now()) }
    fun addFromHistory(item: HistoryItem) = update { ShoppingBuilder.addFromHistory(it, item, now()) }
    fun toggleItem(id: String) = update { ShoppingBuilder.toggle(it, id, now()) }
    fun removeItem(id: String) = update { s -> s.copy(shopping = s.shopping.filter { it.id != id }) }
    fun clearChecked() = update { ShoppingBuilder.clearChecked(it, now()) }
    fun forgetHistory(name: String) = update { s -> s.copy(history = s.history.filter { it.name != name }) }

    /** Geeft het aantal maaltijden terug waarvan de ingrediënten toegevoegd werden. */
    fun addWeekToList(dates: List<LocalDate>): Int {
        var added = 0
        update { s ->
            val (next, count) = ShoppingBuilder.addMenu(s, dates, now())
            added = count
            next
        }
        return added
    }

    fun addRecipeToList(recipe: Recipe, persons: Int) = update { ShoppingBuilder.addRecipe(it, recipe, persons, now()) }

    // ---------- voorkeuren ----------
    fun updatePrefs(f: (Prefs) -> Prefs) = update { it.copy(prefs = f(it.prefs)) }
}
