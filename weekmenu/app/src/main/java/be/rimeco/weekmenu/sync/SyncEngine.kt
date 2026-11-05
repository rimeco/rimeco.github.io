package be.rimeco.weekmenu.sync

import be.rimeco.weekmenu.data.AppState
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await

enum class SyncStatus { OFF, CONNECTING, ONLINE, SYNCING, OFFLINE, ERROR }

data class SyncInfo(val status: SyncStatus = SyncStatus.OFF, val message: String = "")

/**
 * Houdt de lokale gegevens in sync met Firestore: `households/{code}/{verzameling}/{id}`.
 * Alles gebeurt op de main thread (Firestore levert zijn callbacks daar af), dus er zijn geen race conditions
 * tussen lokale wijzigingen en binnenkomende wijzigingen.
 *
 *  - lokale wijziging  -> [onLocalChange]: vergelijkt met wat we laatst kenden en schrijft enkel de verschillen
 *  - externe wijziging -> snapshot-listener: vervangt de lokale verzameling door de externe (incl. eigen,
 *    nog niet bevestigde schrijfacties, dus offline gemaakte wijzigingen gaan niet verloren)
 */
class SyncEngine(
    private val getState: () -> AppState,
    /** Past een extern ontvangen staat toe (bewaart lokaal, maar verstuurt niets terug). */
    private val applyRemote: (AppState) -> Unit,
    private val onInfo: (SyncInfo) -> Unit,
) {
    private val db get() = FirebaseFirestore.getInstance()
    private var householdId: String? = null
    private val last = HashMap<String, Map<String, String>>()
    private val seen = HashSet<String>()                           // verzamelingen waarvan we al een eerste snapshot kregen
    private val flags = HashMap<String, Pair<Boolean, Boolean>>()   // verzameling -> (fromCache, hasPendingWrites)
    private val listeners = mutableListOf<ListenerRegistration>()
    private var error: String? = null

    val isActive get() = householdId != null

    private fun household(code: String): DocumentReference = db.collection("households").document(code)

    // ---------- publieke acties ----------

    /** Nieuw gezin: uploadt alle lokale gegevens. Geeft de code terug. */
    suspend fun create(): String {
        val code = SyncModel.generateCode()
        ensureAuth()
        household(code).set(mapOf("created" to FieldValue.serverTimestamp())).await()   // faalt meteen als de regels ontbreken
        begin(code, pushLocal = true)
        return code
    }

    /** Aansluiten bij een bestaand gezin: lokale gegevens worden vervangen door die van het gezin. */
    suspend fun join(codeInput: String): String {
        val code = SyncModel.normalizeCode(codeInput) ?: throw IllegalArgumentException("Dit is geen geldige gezinscode.")
        ensureAuth()
        val doc = household(code).get(Source.SERVER).await()
        if (!doc.exists()) throw IllegalStateException("Deze gezinscode bestaat niet.")
        applyRemote(
            getState().copy(recipes = emptyList(), menu = emptyMap(), shopping = emptyList(), history = emptyList(), addedToList = emptySet()),
        )
        begin(code, pushLocal = false)
        return code
    }

    /** Bij het opstarten van de app, als er al een gezin ingesteld is. */
    suspend fun resume(code: String) {
        try {
            ensureAuth()
            begin(code, pushLocal = false)
        } catch (e: Exception) {
            fail(e)
        }
    }

    fun stop() {
        listeners.forEach { it.remove() }
        listeners.clear()
        last.clear(); flags.clear(); seen.clear()
        householdId = null
        error = null
        onInfo(SyncInfo(SyncStatus.OFF))
    }

    /** Aanroepen na élke lokale wijziging. */
    fun onLocalChange(state: AppState) {
        if (householdId == null) return
        push(state)
    }

    // ---------- intern ----------

    private suspend fun ensureAuth() {
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) auth.signInAnonymously().await()
    }

    private fun begin(code: String, pushLocal: Boolean) {
        stop()
        householdId = code
        val state = getState()
        for (c in SyncModel.collections) last[c.name] = if (pushLocal) emptyMap() else c.encodeAll(state)
        // Eerst schrijven, dan luisteren: zo bevat de eerste snapshot meteen onze eigen (nog lopende) schrijfacties.
        if (pushLocal) push(state)
        onInfo(SyncInfo(SyncStatus.CONNECTING))
        for (c in SyncModel.collections) {
            val reg = household(code).collection(c.name).addSnapshotListener(MetadataChanges.INCLUDE) { snap, err ->
                if (householdId != code) return@addSnapshotListener
                if (err != null) { fail(err); return@addSnapshotListener }
                if (snap == null) return@addSnapshotListener
                flags[c.name] = snap.metadata.isFromCache to snap.metadata.hasPendingWrites()
                val first = seen.add(c.name)
                if (snap.documentChanges.isNotEmpty() || first) {
                    val remote = snap.documents.mapNotNull { d -> d.getString("d")?.let { d.id to it } }.toMap()
                    val cur = getState()
                    val next = c.applyRemote(cur, remote)
                    last[c.name] = c.encodeAll(next)
                    if (next != cur) applyRemote(next)
                }
                publishInfo()
            }
            listeners += reg
        }
    }

    private fun push(state: AppState) {
        val code = householdId ?: return
        for (c in SyncModel.collections) {
            val now = c.encodeAll(state)
            val changes = SyncModel.diff(last[c.name] ?: emptyMap(), now)
            if (changes.isEmpty) continue
            last[c.name] = now
            val col = household(code).collection(c.name)
            val ops = changes.upserts.map { (id, json) -> { b: com.google.firebase.firestore.WriteBatch -> b.set(col.document(id), mapOf("d" to json)) } } +
                changes.deletes.map { id -> { b: com.google.firebase.firestore.WriteBatch -> b.delete(col.document(id)) } }
            ops.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { it(batch) }
                batch.commit().addOnFailureListener { fail(it) }
            }
        }
    }

    private fun publishInfo() {
        if (error != null) return
        if (flags.size < SyncModel.collections.size) return onInfo(SyncInfo(SyncStatus.CONNECTING))
        val anyCache = flags.values.any { it.first }
        val anyPending = flags.values.any { it.second }
        onInfo(
            when {
                anyCache -> SyncInfo(SyncStatus.OFFLINE, "Offline: wijzigingen worden verstuurd zodra je weer verbinding hebt.")
                anyPending -> SyncInfo(SyncStatus.SYNCING, "Wijzigingen worden verstuurd…")
                else -> SyncInfo(SyncStatus.ONLINE, "Gesynchroniseerd")
            },
        )
    }

    private fun fail(e: Throwable) {
        val msg = friendly(e)
        error = msg
        onInfo(SyncInfo(SyncStatus.ERROR, msg))
    }

    companion object {
        fun friendly(e: Throwable): String = when {
            e is IllegalArgumentException || e is IllegalStateException -> e.message ?: "Onbekende fout"
            e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                "Geen toegang tot de database. Controleer in Firebase of de beveiligingsregels uit firestore.rules gepubliceerd zijn en of anonieme aanmelding aanstaat."
            e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.UNAVAILABLE ->
                "Geen verbinding met de server."
            e is FirebaseNetworkException -> "Geen internetverbinding."
            e is FirebaseAuthException && (e.errorCode == "ERROR_OPERATION_NOT_ALLOWED" || e.errorCode == "ERROR_ADMIN_RESTRICTED_OPERATION") ->
                "Anonieme aanmelding staat niet aan: zet ze aan in Firebase onder Authentication → Aanmeldmethode."
            else -> e.message?.takeIf { it.isNotBlank() } ?: "Onbekende fout"
        }
    }
}
