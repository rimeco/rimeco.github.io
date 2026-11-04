@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package be.rimeco.weekmenu.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import be.rimeco.weekmenu.data.AppState
import be.rimeco.weekmenu.data.HistoryItem
import be.rimeco.weekmenu.data.ShoppingItem
import be.rimeco.weekmenu.logic.ShoppingBuilder
import be.rimeco.weekmenu.logic.Text as TextUtil
import be.rimeco.weekmenu.logic.Units

@Composable
fun ShoppingScreen(vm: AppViewModel, state: AppState) {
    var text by remember { mutableStateOf("") }
    var showChecked by rememberSaveable { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    val open = state.shopping.filter { !it.checked }
    val done = state.shopping.filter { it.checked }
    val suggestions = ShoppingBuilder.suggestions(state, text, 10)

    fun add() {
        if (text.isNotBlank()) { vm.addManual(text); text = "" }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            OutlinedTextField(
                value = text, onValueChange = { text = it }, singleLine = true,
                label = { Text("Item toevoegen (bv. 2 kg appels)") },
                trailingIcon = { IconButton({ add() }, enabled = text.isNotBlank()) { Icon(Icons.Filled.Add, "Toevoegen") } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }),
                modifier = Modifier.fillMaxWidth().padding(16.dp, 8.dp, 16.dp, 4.dp),
            )
        }
        if (suggestions.isNotEmpty() || state.history.isNotEmpty()) {
            item {
                androidx.compose.foundation.layout.FlowRow(
                    Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    suggestions.forEach { h ->
                        AssistChip(onClick = { vm.addFromHistory(h); text = "" }, label = { Text(TextUtil.capitalize(h.name)) })
                    }
                    if (state.history.isNotEmpty()) {
                        AssistChip(onClick = { showHistory = true }, label = { Text("Alle eerder gekochte…") })
                    }
                }
            }
        }
        if (open.isEmpty()) {
            item {
                Text(
                    "Je lijst is leeg. Voeg items toe, kies er uit je eerder gekochte items, of zet de ingrediënten van je weekmenu op de lijst.",
                    Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(open, key = { it.id }) { ItemRow(it, vm) }
        if (done.isNotEmpty()) {
            item {
                Row(
                    Modifier.fillMaxWidth().clickable { showChecked = !showChecked }.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (showChecked) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null)
                    Text("  ${done.size} afgevinkt", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton({ vm.clearChecked() }) { Text("Lijst opruimen") }
                }
            }
            if (showChecked) items(done, key = { it.id }) { ItemRow(it, vm) }
        }
    }

    if (showHistory) HistorySheet(vm, state) { showHistory = false }
}

@Composable
private fun ItemRow(item: ShoppingItem, vm: AppViewModel) {
    val sub = listOf(Units.formatShopping(item.quantity, item.unit), item.note).filter { it.isNotBlank() }.joinToString(" · ")
    Row(
        Modifier.fillMaxWidth().clickable { vm.toggleItem(item.id) }.padding(start = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(item.checked, { vm.toggleItem(item.id) })
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(
                TextUtil.capitalize(item.name),
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                color = if (item.checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton({ vm.removeItem(item.id) }) { Icon(Icons.Filled.Close, "Verwijderen", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun HistorySheet(vm: AppViewModel, state: AppState, onClose: () -> Unit) {
    var q by remember { mutableStateOf("") }
    val onList = state.shopping.filter { !it.checked }.map { it.name.lowercase() }.toSet()
    val items: List<HistoryItem> = state.history
        .filter { q.isBlank() || it.name.contains(q.trim().lowercase()) }
        .sortedWith(compareByDescending<HistoryItem> { it.count }.thenByDescending { it.lastUsed })
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text("Eerder gekocht", style = MaterialTheme.typography.titleLarge)
            Text("Tik om opnieuw op de lijst te zetten. Afgevinkte items worden hier automatisch onthouden.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(q, { q = it }, singleLine = true, label = { Text("Zoeken") }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
            LazyColumn(Modifier.weight(1f, fill = false)) {
                items(items, key = { it.name }) { h ->
                    val already = h.name in onList
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = !already) { vm.addFromHistory(h) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                            Text(TextUtil.capitalize(h.name))
                            Text("${h.count}× gekocht", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (already) Icon(Icons.Filled.Check, "Staat op de lijst", tint = MaterialTheme.colorScheme.tertiary)
                        else Icon(Icons.Filled.Add, "Toevoegen")
                        IconButton({ vm.forgetHistory(h.name) }) { Icon(Icons.Filled.Close, "Vergeten", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
    }
}
