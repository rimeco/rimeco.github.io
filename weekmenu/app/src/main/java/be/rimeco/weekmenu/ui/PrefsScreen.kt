@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package be.rimeco.weekmenu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import android.content.Intent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import be.rimeco.weekmenu.sync.SyncStatus
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import be.rimeco.weekmenu.data.AppState
import be.rimeco.weekmenu.data.allTags

@Composable
fun PrefsScreen(vm: AppViewModel, state: AppState) {
    val p = state.prefs
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        FamilySection(vm, state)

        Section("Aantal personen")
        PrefRow("Weekdagen (ma–vr)") { Stepper(p.weekdayPersons, { v -> vm.updatePrefs { it.copy(weekdayPersons = v) } }, 1..20, label = { "$it pers." }) }
        PrefRow("Weekend (za–zo)") { Stepper(p.weekendPersons, { v -> vm.updatePrefs { it.copy(weekendPersons = v) } }, 1..20, label = { "$it pers." }) }
        Hint("Geldt voor dagen die je nog niet aanpaste. Per dag kan je het aantal altijd wijzigen in het weekmenu.")

        Section("Automatisch menu")
        Column {
            Text("Minimum beoordeling")
            StarRating(p.minRating, size = 28.dp) { v -> vm.updatePrefs { it.copy(minRating = v) } }
            Hint(if (p.minRating == 0) "Geen minimum. Meer sterren = grotere kans om gekozen te worden." else "Enkel recepten met ${p.minRating}+ sterren. Niet-beoordeelde recepten tellen als 3 sterren.")
        }
        PrefRow("Zelfde recept niet binnen") {
            Stepper(p.avoidRepeatDays, { v -> vm.updatePrefs { it.copy(avoidRepeatDays = v) } }, 0..90, step = 7, label = { if (it == 0) "uit" else "$it dagen" })
        }
        PrefRow("Vegetarische dagen / week") {
            Stepper(p.vegDaysPerWeek, { v -> vm.updatePrefs { it.copy(vegDaysPerWeek = v) } }, 0..7, label = { "$it" })
        }
        PrefRow("Max. tijd op weekdagen") {
            Stepper(p.maxMinutesWeekdays, { v -> vm.updatePrefs { it.copy(maxMinutesWeekdays = v) } }, 0..120, step = 5, label = { if (it == 0) "geen" else "$it min" })
        }
        val tags = state.allTags()
        Column {
            Text("Liefst vaker")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tags.forEach { t ->
                    FilterChip(selected = t in p.preferredTags, onClick = {
                        vm.updatePrefs { pr ->
                            if (t in pr.preferredTags) pr.copy(preferredTags = pr.preferredTags - t)
                            else pr.copy(preferredTags = pr.preferredTags + t, excludedTags = pr.excludedTags - t)
                        }
                    }, label = { Text(t) })
                }
            }
        }
        Column {
            Text("Nooit voorstellen")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tags.forEach { t ->
                    FilterChip(selected = t in p.excludedTags, onClick = {
                        vm.updatePrefs { pr ->
                            if (t in pr.excludedTags) pr.copy(excludedTags = pr.excludedTags - t)
                            else pr.copy(excludedTags = pr.excludedTags + t, preferredTags = pr.preferredTags - t)
                        }
                    }, label = { Text(t) })
                }
            }
        }
        Hint("Het voorstel wisselt vlees, vis, pasta en soep af en kiest geen recept dat net op het menu stond.")

        Section("Boodschappenlijst")
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Zout, peper en water overslaan")
                Hint("Basisproducten komen niet op de lijst als je ingrediënten van het menu toevoegt.")
            }
            Switch(p.skipStaples, { v -> vm.updatePrefs { it.copy(skipStaples = v) } })
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun PrefRow(label: String, control: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        control()
    }
}

@Composable
private fun FamilySection(vm: AppViewModel, state: AppState) {
    val info by vm.syncInfo.collectAsStateWithLifecycle()
    val code = state.prefs.householdId
    val context = LocalContext.current
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var joinCode by remember { mutableStateOf("") }
    var confirmJoin by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }

    Section("Gezin & synchronisatie")
    if (code.isBlank()) {
        Hint("Deel je recepten, weekmenu en boodschappenlijst live met je gezin. Eén persoon maakt het gezin aan, de anderen sluiten aan met de gezinscode.")
        Button(
            onClick = { busy = true; error = null; vm.createHousehold { r -> busy = false; error = r.exceptionOrNull()?.message } },
            enabled = !busy, modifier = Modifier.fillMaxWidth(),
        ) { Text(if (busy) "Bezig…" else "Gezin aanmaken") }
        OutlinedTextField(
            joinCode, { joinCode = it; error = null }, singleLine = true, label = { Text("Gezinscode van iemand anders") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton({ confirmJoin = true }, enabled = !busy && joinCode.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Deelnemen") }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Gezinscode")
            Text(vm.formatCode(code), style = MaterialTheme.typography.headlineSmall, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
            val label = when (info.status) {
                SyncStatus.OFF -> ""
                SyncStatus.CONNECTING -> "Verbinden…"
                SyncStatus.ONLINE -> "✓ ${info.message}"
                else -> info.message
            }
            if (label.isNotBlank()) {
                Text(
                    label, style = MaterialTheme.typography.bodySmall,
                    color = if (info.status == SyncStatus.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(
                        Intent.EXTRA_TEXT,
                        "Doe mee met ons Weekmenu! Installeer de app, ga naar Voorkeuren → Gezin & synchronisatie → Deelnemen en vul deze code in: ${vm.formatCode(code)}",
                    )
                }
                context.startActivity(Intent.createChooser(send, "Gezinscode delen"))
            }, Modifier.weight(1f)) { Text("Code delen") }
            OutlinedButton({ confirmLeave = true }, Modifier.weight(1f)) { Text("Gezin verlaten") }
        }
    }

    if (confirmJoin) {
        AlertDialog(
            onDismissRequest = { confirmJoin = false },
            title = { Text("Deelnemen aan het gezin?") },
            text = { Text("Je recepten, weekmenu en boodschappenlijst op dit toestel worden vervangen door die van het gezin.") },
            confirmButton = {
                TextButton({
                    confirmJoin = false; busy = true; error = null
                    vm.joinHousehold(joinCode) { r -> busy = false; error = r.exceptionOrNull()?.message; if (r.isSuccess) joinCode = "" }
                }) { Text("Deelnemen") }
            },
            dismissButton = { TextButton({ confirmJoin = false }) { Text("Annuleren") } },
        )
    }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Gezin verlaten?") },
            text = { Text("Dit toestel stopt met synchroniseren. Je houdt een lokale kopie van alles; de anderen merken niets.") },
            confirmButton = { TextButton({ confirmLeave = false; vm.leaveHousehold() }) { Text("Verlaten") } },
            dismissButton = { TextButton({ confirmLeave = false }) { Text("Annuleren") } },
        )
    }
}
