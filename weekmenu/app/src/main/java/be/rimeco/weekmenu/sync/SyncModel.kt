package be.rimeco.weekmenu.sync

import be.rimeco.weekmenu.data.AppState
import be.rimeco.weekmenu.data.HistoryItem
import be.rimeco.weekmenu.data.MenuEntry
import be.rimeco.weekmenu.data.Recipe
import be.rimeco.weekmenu.data.ShoppingItem
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.net.URLEncoder
import java.security.SecureRandom

/** Zuivere (Firebase-vrije) logica van de synchronisatie, zodat ze volledig getest kan worden. */
object SyncModel {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** Eén gesynchroniseerde verzameling (recepten, menu, ...): elk item is een document met één veld "d" (JSON). */
    class Collection<T>(
        val name: String,
        private val serializer: KSerializer<T>,
        private val idOf: (T) -> String,
        private val read: (AppState) -> kotlin.collections.Collection<T>,
        private val write: (AppState, List<T>) -> AppState,
    ) {
        /** id -> JSON van alle lokale items. */
        fun encodeAll(state: AppState): Map<String, String> =
            read(state).associate { idOf(it) to json.encodeToString(serializer, it) }

        /** Vervangt deze verzameling in [state] door de externe documenten (id -> JSON). */
        fun applyRemote(state: AppState, docs: Map<String, String>): AppState =
            write(state, docs.values.mapNotNull { runCatching { json.decodeFromString(serializer, it) }.getOrNull() })
    }

    /** Document-id voor een naam: mag geen "/" bevatten en niet leeg of "." zijn. */
    fun safeId(prefix: String, raw: String): String = prefix + URLEncoder.encode(raw, "UTF-8").replace(".", "%2E")

    val collections: List<Collection<*>> = listOf(
        Collection("recipes", Recipe.serializer(), { it.id }, { it.recipes }) { s, l -> s.copy(recipes = l.sortedWith(compareBy({ it.createdAt }, { it.id }))) },
        Collection("menu", MenuEntry.serializer(), { it.date }, { it.menu.values }) { s, l -> s.copy(menu = l.associateBy { it.date }) },
        Collection("shopping", ShoppingItem.serializer(), { it.id }, { it.shopping }) { s, l -> s.copy(shopping = l.sortedWith(compareBy({ it.addedAt }, { it.id }))) },
        Collection("history", HistoryItem.serializer(), { safeId("h_", it.name) }, { it.history }) { s, l -> s.copy(history = l) },
        Collection("added", String.serializer(), { safeId("a_", it) }, { it.addedToList }) { s, l -> s.copy(addedToList = l.toSet()) },
    )

    data class Changes(val upserts: Map<String, String>, val deletes: Set<String>) {
        val isEmpty get() = upserts.isEmpty() && deletes.isEmpty()
    }

    fun diff(old: Map<String, String>, new: Map<String, String>): Changes =
        Changes(new.filter { old[it.key] != it.value }, old.keys - new.keys)

    // ---------- gezinscode ----------
    private const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"  // zonder I, L, O, 0, 1
    const val CODE_LENGTH = 16

    fun generateCode(random: java.util.Random = SecureRandom()): String =
        buildString { repeat(CODE_LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }

    /** "abcd-efgh ..." -> "ABCDEFGH..."; null als het geen geldige code is. */
    fun normalizeCode(input: String): String? {
        val c = input.uppercase().filter { it.isLetterOrDigit() }
        return c.takeIf { it.length == CODE_LENGTH && it.all { ch -> ch in ALPHABET } }
    }

    fun formatCode(code: String): String = code.chunked(4).joinToString("-")
}
