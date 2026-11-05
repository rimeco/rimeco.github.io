package be.rimeco.weekmenu.data

import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalDate

@Serializable
data class Ingredient(
    /** Naam in kleine letters, zonder hoeveelheid/eenheid ("ui", "gerookte zalm"). */
    val name: String,
    val quantity: Double? = null,
    /** Genormaliseerde eenheid: g, kg, ml, dl, cl, l, el, tl, blik, ... of leeg. */
    val unit: String = "",
    /** Originele tekst zoals op de receptenpagina. */
    val raw: String = "",
)

@Serializable
data class Recipe(
    val id: String,
    val title: String,
    val url: String = "",
    val imageUrl: String = "",
    /** Voor hoeveel personen de ingrediëntenlijst geldt. */
    val servings: Int = 4,
    val minutes: Int = 0,
    val ingredients: List<Ingredient> = emptyList(),
    /** 0 = nog niet beoordeeld, 1..5 sterren. */
    val rating: Int = 0,
    val tags: List<String> = emptyList(),
    val notes: String = "",
    val createdAt: Long = 0L,
)

@Serializable
data class MenuEntry(
    /** ISO-datum, bv. 2026-11-04. */
    val date: String,
    val recipeId: String? = null,
    /** Vrije tekst als er geen recept is ("Restaurant", "Restjes", ...). */
    val customName: String? = null,
    val persons: Int,
)

@Serializable
data class ShoppingItem(
    val id: String,
    val name: String,
    val quantity: Double? = null,
    val unit: String = "",
    val checked: Boolean = false,
    /** Bv. de receptnamen waar dit item voor nodig is. */
    val note: String = "",
    val addedAt: Long = 0L,
    /** Hoeveelheid handmatig aangepast: dan tonen we precies wat je typte (zonder afronden). */
    val manual: Boolean = false,
)

/** Eerder afgevinkt/gekocht item, bewaard om snel opnieuw toe te voegen. */
@Serializable
data class HistoryItem(
    val name: String,
    val count: Int = 1,
    val lastUsed: Long = 0L,
    val quantity: Double? = null,
    val unit: String = "",
)

@Serializable
data class Prefs(
    val weekdayPersons: Int = 3,
    val weekendPersons: Int = 4,
    /** Minimum aantal sterren voor automatische voorstellen (niet-beoordeeld telt als 3). */
    val minRating: Int = 0,
    /** Zelfde recept niet binnen dit aantal dagen opnieuw voorstellen. */
    val avoidRepeatDays: Int = 21,
    /** Streef naar dit aantal vegetarische dagen per week. */
    val vegDaysPerWeek: Int = 0,
    /** Max bereidingstijd op weekdagen (0 = geen limiet). */
    val maxMinutesWeekdays: Int = 0,
    val preferredTags: List<String> = emptyList(),
    val excludedTags: List<String> = emptyList(),
    /** Zout, peper en water niet op de boodschappenlijst zetten. */
    val skipStaples: Boolean = true,
    /** Gezinscode van de gedeelde Firestore-omgeving; leeg = enkel lokaal. Wordt zelf niet gesynchroniseerd. */
    val householdId: String = "",
)

@Serializable
data class AppState(
    val recipes: List<Recipe> = emptyList(),
    val menu: Map<String, MenuEntry> = emptyMap(),
    val shopping: List<ShoppingItem> = emptyList(),
    val history: List<HistoryItem> = emptyList(),
    /** Sleutels "datum:receptId" van menu-items waarvan de ingrediënten al op de lijst staan. */
    val addedToList: Set<String> = emptySet(),
    val prefs: Prefs = Prefs(),
)

fun Prefs.defaultPersons(date: LocalDate): Int =
    if (date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY) weekendPersons else weekdayPersons

fun AppState.entryFor(date: LocalDate): MenuEntry =
    menu[date.toString()] ?: MenuEntry(date.toString(), persons = prefs.defaultPersons(date))

fun AppState.recipe(id: String?): Recipe? = id?.let { rid -> recipes.firstOrNull { it.id == rid } }

fun AppState.allTags(): List<String> =
    (Tags.defaults + recipes.flatMap { it.tags } + prefs.preferredTags + prefs.excludedTags).distinct()

/** De planningsweek loopt van zaterdag (winkeldag) tot en met vrijdag. */
object Week {
    val START: DayOfWeek = DayOfWeek.SATURDAY

    fun startOf(date: LocalDate): LocalDate = date.with(java.time.temporal.TemporalAdjusters.previousOrSame(START))

    fun days(start: LocalDate): List<LocalDate> = (0L..6L).map { start.plusDays(it) }
}
