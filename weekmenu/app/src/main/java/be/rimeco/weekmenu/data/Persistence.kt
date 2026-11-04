package be.rimeco.weekmenu.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** Bewaart alle gegevens lokaal in één JSON-bestand (atomair weggeschreven). */
class Persistence(private val file: File) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    fun load(): AppState {
        if (!file.exists()) return AppState()
        return try {
            json.decodeFromString<AppState>(file.readText())
        } catch (e: Exception) {
            runCatching { file.copyTo(File(file.path + ".corrupt"), overwrite = true) }
            AppState()
        }
    }

    fun encode(state: AppState): String = json.encodeToString(state)

    fun decode(text: String): AppState = json.decodeFromString<AppState>(text)

    fun save(latest: () -> AppState) {
        scope.launch {
            mutex.withLock {
                val tmp = File(file.path + ".tmp")
                tmp.writeText(json.encodeToString(latest()))
                if (!tmp.renameTo(file)) {
                    file.delete()
                    tmp.renameTo(file)
                }
            }
        }
    }
}
