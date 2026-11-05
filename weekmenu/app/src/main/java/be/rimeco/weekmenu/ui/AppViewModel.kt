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
import be.rimeco.weekmenu.data.Tags
import be.rimeco.weekmenu.data.defaultPersons
import be.rimeco.weekmenu.data.entryFor
import be.rimeco.weekmenu.logic.BulkImporter
import be.rimeco.weekmenu.logic.IngredientParser
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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

data class ShoppingSnapshot(val shopping: List<be.rimeco.weekmenu.data.ShoppingItem>, val history: List<HistoryItem>)

data class BulkItem(val url: String, val draft: RecipeImporter.Draft, val selected: Boolean = true)

/** Toestand van het scherm "Recepten in bulk toevoegen" (blijft behouden bij draaien van het scherm). */
class BulkState {
    var started by mutableStateOf(false)
    var running by mutableStateOf(false)
    var page by mutableIntStateOf(0)
    var pages by mutableIntStateOf(0)
    var checked by mutableIntStateOf(0)
    var error by mutableStateOf<String?>(null)
    /** Aantal toegevoegde recepten na bevestigen; -1 = nog niet toegevoegd. */
    var added by mutableIntStateOf(-1)
    /** Ingevuld als een site een login vraagt (adres waarvoor je moet inloggen). */
    var loginUrl by mutableStateOf<String?>(null)
    /** Invoer blijft bewaard, ook als je even naar het inlogscherm gaat. */
    var input by mutableStateOf("")
    var pagesPerLink by mutableIntStateOf(3)
    val results = androidx.compose.runtime.mutableStateListOf<BulkItem>()

    fun reset() {
        started = false; running = false; page = 0; pages = 0; checked = 0; error = null; added = -1; loginUrl = null; results.clear()
    }
}

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
        // Dezelfde browser-identiteit en cookies als de ingebouwde browser, zodat een login-sessie ook voor de import geldt.
        runCatching { RecipeImporter.userAgent = android.webkit.WebSettings.getDefaultUserAgent(app) }
        RecipeImporter.cookieJar = WebCookieJar
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
        return recipeFromDraft(d, RecipeImporter.normalizeUrl(url), now())
    }

    private fun recipeFromDraft(d: RecipeImporter.Draft, url: String, createdAt: Long) = Recipe(
        id = UUID.randomUUID().toString(),
        title = d.title,
        url = url,
        imageUrl = d.imageUrl,
        servings = d.servings,
        minutes = d.minutes,
        ingredients = d.ingredientLines.map { IngredientParser.parse(it) },
        tags = d.tags,
        createdAt = createdAt,
    )

    // ---------- recepten in bulk ----------
    val bulk = BulkState()
    private var bulkJob: Job? = null

    fun startBulk(text: String, pagesPerLink: Int) {
        val urls = text.lines().mapNotNull { line ->
            RecipeImporter.extractUrl(line) ?: line.trim().takeIf { it.contains('.') && !it.contains(' ') }
        }.map { RecipeImporter.normalizeUrl(it) }.distinct()
        if (urls.isEmpty()) { bulk.error = "Plak minstens één link."; return }
        stopBulk()
        bulk.reset()
        bulk.input = text
        bulk.pagesPerLink = pagesPerLink
        bulk.started = true
        bulk.running = true
        val st = _state.value
        bulkJob = viewModelScope.launch {
            try {
                BulkImporter(fetch = { RecipeImporter.fetchHtml(it) }).run(
                    listingUrls = urls,
                    pagesPerListing = pagesPerLink,
                    knownUrls = st.recipes.map { it.url }.filter { it.isNotBlank() }.toSet(),
                    knownTitles = st.recipes.map { it.title }.toSet(),
                    onProgress = { p -> bulk.page = p.page; bulk.pages = p.pages; bulk.checked = p.checked; bulk.loginUrl = p.loginUrl; if (bulk.results.isEmpty()) bulk.error = p.lastError },
                    onFound = { f -> bulk.results.add(BulkItem(f.url, f.draft)) },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                bulk.error = e.message ?: "Onbekende fout"
            } finally {
                bulk.running = false
            }
        }
    }

    private var bulkAwaitingLogin = false

    fun loginForBulk(url: String) { bulkAwaitingLogin = true; push(Route.SiteLogin(url)) }

    /** Na terugkeren uit het inlogscherm de zoekopdracht automatisch opnieuw starten. */
    fun resumeBulkAfterLogin() {
        if (bulkAwaitingLogin) { bulkAwaitingLogin = false; startBulk(bulk.input, bulk.pagesPerLink) }
    }

    fun stopBulk() { bulkJob?.cancel(); bulkJob = null; bulk.running = false }

    fun toggleBulk(index: Int) {
        if (index in bulk.results.indices) bulk.results[index] = bulk.results[index].let { it.copy(selected = !it.selected) }
    }

    fun selectAllBulk(selected: Boolean) {
        for (i in bulk.results.indices) bulk.results[i] = bulk.results[i].copy(selected = selected)
    }

    /** Voegt de aangevinkte recepten toe aan de bibliotheek. */
    fun addBulkSelected() {
        stopBulk()
        val chosen = bulk.results.filter { it.selected }
        val base = now()
        val recipes = chosen.mapIndexed { i, it -> recipeFromDraft(it.draft, it.url, base + i) }
        if (recipes.isNotEmpty()) update { it.copy(recipes = it.recipes + recipes) }
        bulk.added = recipes.size
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

    /** Vult bij bestaande recepten de tags Dessert/Divers aan (op basis van titel en ingrediënten). Geeft het aantal aangepaste recepten terug. */
    fun suggestMissingTags(): Int {
        var changed = 0
        update { s ->
            s.copy(recipes = s.recipes.map { r ->
                if (Tags.DESSERT in r.tags || Tags.MISC in r.tags) return@map r
                val sug = Tags.suggest(r.title, r.ingredients.map { it.name }, "", r.minutes)
                val add = sug.filter { it == Tags.DESSERT || it == Tags.MISC }
                if (add.isEmpty()) return@map r
                changed++
                var tags = r.tags + add
                if (Tags.VEGETARIAN !in sug) tags = tags - Tags.VEGETARIAN
                r.copy(tags = tags)
            })
        }
        return changed
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
    /** Bewaart de lijst + historiek vóór een wijziging, zodat die "ongedaan gemaakt" kan worden. */
    fun shoppingSnapshot() = _state.value.let { ShoppingSnapshot(it.shopping, it.history) }
    fun restoreShopping(s: ShoppingSnapshot) = update { it.copy(shopping = s.shopping, history = s.history) }

    /** Past naam/hoeveelheid/eenheid van een item aan (lang indrukken op het item). */
    fun updateItem(id: String, name: String, quantity: Double?, unit: String) = update { s ->
        s.copy(shopping = s.shopping.map {
            if (it.id != id) it else it.copy(
                name = name.trim().lowercase().ifBlank { it.name },
                quantity = quantity,
                unit = if (quantity == null) "" else unit,
                manual = true,
            )
        })
    }

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
