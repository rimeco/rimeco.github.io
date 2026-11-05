@file:OptIn(ExperimentalMaterial3Api::class)

package be.rimeco.weekmenu.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import be.rimeco.weekmenu.data.Recipe
import be.rimeco.weekmenu.logic.BulkImporter

@Composable
fun BulkImportScreen(vm: AppViewModel) {
    val b = vm.bulk
    DisposableEffect(Unit) { onDispose { vm.stopBulk() } }
    LaunchedEffect(Unit) { vm.resumeBulkAfterLogin() }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Recepten in bulk") }, navigationIcon = { BackButton { vm.pop() } }) },
        bottomBar = {
            if (b.started && b.added < 0) {
                val n = b.results.count { it.selected }
                Button(
                    onClick = { vm.addBulkSelected() }, enabled = n > 0,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) { Text(if (n == 0) "Niets geselecteerd" else if (b.running) "Stop en voeg $n ${if (n == 1) "recept" else "recepten"} toe" else "Voeg $n ${if (n == 1) "recept" else "recepten"} toe") }
            }
        },
    ) { pad ->
        when {
            b.added >= 0 -> Column(
                Modifier.fillMaxSize().padding(pad).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("✓ ${b.added} ${if (b.added == 1) "recept" else "recepten"} toegevoegd", style = MaterialTheme.typography.titleLarge)
                Text("Je vindt ze terug in het tabblad Recepten. Beoordeel ze met sterren om ze vaker of minder vaak in je menu te zien.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button({ b.reset(); vm.pop() }) { Text("Klaar") }
            }

            !b.started -> Column(
                Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Plak de link van een overzichtspagina met recepten (bv. “alle recepten” of een weekmenu). " +
                        "De app opent de recepten op die pagina, haalt ze in en laat jou kiezen welke je wil bewaren.",
                )
                OutlinedTextField(
                    b.input, { b.input = it }, label = { Text("Links (één per regel)") }, minLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Pagina's per link", Modifier.weight(1f))
                    Stepper(b.pagesPerLink, { b.pagesPerLink = it }, 1..15, label = { "$it" })
                }
                Text(
                    "Volgende pagina's (…/page/2/ of ?page=2) worden vanzelf herkend; bij een datum in de link (…/weekmenu/2026-11-09) ga je een week per pagina terug in de tijd. Er worden hoogstens ${BulkImporter.DEFAULT_MAX_CHECKS} pagina's per keer gecontroleerd, " +
                        "rustig na elkaar om de sites niet te belasten. Recepten die je al hebt worden overgeslagen. " +
                        "Gebruik dit voor eigen gebruik en respecteer de voorwaarden van de sites.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                b.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button({ vm.startBulk(b.input, b.pagesPerLink) }, enabled = b.input.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Recepten zoeken") }
            }

            else -> Column(Modifier.padding(pad)) {
                Row(Modifier.fillMaxWidth().padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (b.running) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${b.results.size} ${if (b.results.size == 1) "recept" else "recepten"} gevonden",
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            (if (b.running) "Bezig… " else "Klaar. ") + "${b.checked} pagina's gecontroleerd" + if (b.pages > 1) " · lijstpagina ${b.page}/${b.pages}" else "",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (b.running) OutlinedButton({ vm.stopBulk() }) { Text("Stop") }
                }
                b.loginUrl?.let { login ->
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Deze site vraagt een account. Log in met je eigen account; daarna zoekt de app automatisch verder.", color = MaterialTheme.colorScheme.error)
                        Button({ vm.loginForBulk(login) }) { Text("Inloggen") }
                    }
                }
                if (b.results.isEmpty() && !b.running) {
                    Text(
                        "Geen recepten gevonden." + (b.error?.let { " Laatste melding: $it" } ?: " Controleer de link, of voeg recepten één voor één toe."),
                        Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button({ vm.startBulk(b.input, b.pagesPerLink) }) { Text("Opnieuw zoeken") }
                        OutlinedButton({ b.reset() }) { Text("Links aanpassen") }
                    }
                }
                if (b.results.isNotEmpty()) {
                    Row(Modifier.padding(horizontal = 8.dp)) {
                        TextButton({ vm.selectAllBulk(true) }) { Text("Alles") }
                        TextButton({ vm.selectAllBulk(false) }) { Text("Niets") }
                    }
                }
                LazyColumn(Modifier.weight(1f)) {
                    itemsIndexed(b.results, key = { _, it -> it.url }) { i, item ->
                        val d = item.draft
                        Row(
                            Modifier.fillMaxWidth().clickable { vm.toggleBulk(i) }.padding(start = 8.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Checkbox(item.selected, { vm.toggleBulk(i) })
                            RecipeThumb(Recipe(id = item.url, title = d.title, imageUrl = d.imageUrl), size = 48.dp)
                            Column(Modifier.weight(1f)) {
                                Text(d.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                                val sub = listOfNotNull(
                                    "${d.ingredientLines.size} ingrediënten",
                                    d.minutes.takeIf { it > 0 }?.let { "$it min" },
                                    d.tags.take(2).joinToString(", ").takeIf { it.isNotBlank() },
                                ).joinToString(" · ")
                                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}
