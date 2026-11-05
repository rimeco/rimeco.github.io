@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)

package be.rimeco.weekmenu.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import be.rimeco.weekmenu.data.AppState
import be.rimeco.weekmenu.data.HistoryItem
import be.rimeco.weekmenu.data.ShoppingItem
import be.rimeco.weekmenu.logic.IngredientParser
import be.rimeco.weekmenu.logic.ShoppingBuilder
import be.rimeco.weekmenu.logic.Text as TextUtil
import be.rimeco.weekmenu.logic.Units
import kotlinx.coroutines.launch

@Composable
fun ShoppingScreen(vm: AppViewModel, state: AppState, snackbar: SnackbarHostState) {
    var text by remember { mutableStateOf("") }
    var showChecked by rememberSaveable { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ShoppingItem?>(null) }
    val scope = rememberCoroutineScope()
    val open = state.shopping.filter { !it.checked }
    val done = state.shopping.filter { it.checked }
    val typing = text.isNotBlank()
    val autocomplete = if (typing) ShoppingBuilder.autocomplete(state, text, 6) else emptyList()
    val quick = if (typing) emptyList() else ShoppingBuilder.suggestions(state, "", 10)

    /** Voert een wijziging uit en toont een melding met "Ongedaan maken". */
    fun withUndo(message: String, action: () -> Unit) {
        val before = vm.shoppingSnapshot()
        action()
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val r = snackbar.showSnackbar(message, actionLabel = "Ongedaan maken", duration = SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) vm.restoreShopping(before)
        }
    }

    fun add() {
        if (text.isNotBlank()) { vm.addManual(text); text = "" }
    }

    fun pick(s: ShoppingBuilder.Suggestion) {
        val typed = IngredientParser.parse(text)
        when {
            typed.quantity != null -> vm.addManual("${Units.format(typed.quantity, typed.unit)} ${s.name}")   // je typte zelf een hoeveelheid
            s.history != null -> vm.addFromHistory(s.history)                                                // laatst gebruikte hoeveelheid
            else -> vm.addManual(s.name)
        }
        text = ""
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            OutlinedTextField(
                value = text, onValueChange = { text = it }, singleLine = true,
                label = { Text("Item toevoegen (bv. 2 kg appels)") },
                trailingIcon = { IconButton({ add() }, enabled = typing) { Icon(Icons.Filled.Add, "Toevoegen") } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }),
                modifier = Modifier.fillMaxWidth().padding(16.dp, 8.dp, 16.dp, 4.dp),
            )
        }
        if (autocomplete.isNotEmpty()) {
            item {
                Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), shape = RoundedCornerShape(12.dp), tonalElevation = 3.dp) {
                    Column {
                        autocomplete.forEachIndexed { i, s ->
                            Row(
                                Modifier.fillMaxWidth().clickable { pick(s) }.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(TextUtil.capitalize(s.name), fontWeight = FontWeight.SemiBold)
                                    Text(s.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(Icons.Filled.Add, "Toevoegen")
                            }
                            if (i < autocomplete.lastIndex) HorizontalDivider()
                        }
                    }
                }
            }
        }
        if (!typing && (quick.isNotEmpty() || state.history.isNotEmpty())) {
            item {
                FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    quick.forEach { h -> AssistChip(onClick = { vm.addFromHistory(h) }, label = { Text(TextUtil.capitalize(h.name)) }) }
                    if (state.history.isNotEmpty()) AssistChip(onClick = { showHistory = true }, label = { Text("Alle eerder gekochte…") })
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
        items(open, key = { it.id }) { item ->
            ItemRow(
                item,
                onToggle = { withUndo("‘${TextUtil.capitalize(item.name)}’ afgevinkt") { vm.toggleItem(item.id) } },
                onRemove = { withUndo("‘${TextUtil.capitalize(item.name)}’ verwijderd") { vm.removeItem(item.id) } },
                onEdit = { editing = item },
            )
        }
        if (done.isNotEmpty()) {
            item {
                Row(
                    Modifier.fillMaxWidth().clickable { showChecked = !showChecked }.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (showChecked) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null)
                    Text("  ${done.size} afgevinkt", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton({ withUndo("${done.size} afgevinkte items van de lijst gehaald") { vm.clearChecked() } }) { Text("Lijst opruimen") }
                }
            }
            if (showChecked) items(done, key = { it.id }) { item ->
                ItemRow(
                    item,
                    onToggle = { withUndo("‘${TextUtil.capitalize(item.name)}’ teruggezet") { vm.toggleItem(item.id) } },
                    onRemove = { withUndo("‘${TextUtil.capitalize(item.name)}’ verwijderd") { vm.removeItem(item.id) } },
                    onEdit = { editing = item },
                )
            }
        }
    }

    if (showHistory) HistorySheet(vm, state) { showHistory = false }

    editing?.let { item ->
        EditItemDialog(
            item,
            onSave = { name, qty, unit ->
                editing = null
                withUndo("‘${TextUtil.capitalize(name.ifBlank { item.name })}’ aangepast") { vm.updateItem(item.id, name, qty, unit) }
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun ItemRow(item: ShoppingItem, onToggle: () -> Unit, onRemove: () -> Unit, onEdit: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    // Handmatig aangepast of zelf getypt: precies tonen. Uit een recept: koopbare stuks naar boven afronden.
    val qty = if (item.manual || item.note.isBlank()) Units.format(item.quantity, item.unit) else Units.formatShopping(item.quantity, item.unit)
    val note = ShoppingBuilder.shortNote(item.note)
    val titleColor = if (item.checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier.fillMaxWidth()
            .combinedClickable(onClick = onToggle, onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onEdit() })
            .padding(start = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(item.checked, { onToggle() })
        Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
            Text(
                buildAnnotatedString {
                    if (qty.isNotBlank()) withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = if (item.checked) titleColor else MaterialTheme.colorScheme.primary)) { append(qty); append("  ") }
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(TextUtil.capitalize(item.name)) }
                },
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                color = titleColor,
            )
            if (note.isNotBlank()) {
                Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        IconButton(onRemove) { Icon(Icons.Filled.Close, "Verwijderen", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun EditItemDialog(item: ShoppingItem, onSave: (name: String, quantity: Double?, unit: String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(TextUtil.capitalize(item.name)) }
    var quantity by remember { mutableStateOf(item.quantity?.let { Units.formatNumber(it, fractions = false) } ?: "") }
    var unit by remember { mutableStateOf(item.unit) }
    val parsed = if (quantity.isBlank()) null else IngredientParser.parseNumber(quantity)
    val invalid = quantity.isNotBlank() && parsed == null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hoeveelheid aanpassen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Naam") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    quantity, { quantity = it }, label = { Text("Hoeveelheid") }, singleLine = true, isError = invalid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(unit, { unit = it }, label = { Text("Eenheid (g, kg, l, el, blik, …)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text(
                    if (invalid) "Dit is geen geldig getal." else "Laat de hoeveelheid leeg als je geen hoeveelheid wil.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (invalid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton({ onSave(name, parsed, IngredientParser.normalizeUnit(unit)) }, enabled = !invalid && name.isNotBlank()) { Text("Opslaan") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Annuleren") } },
    )
}

@Composable
private fun HistorySheet(vm: AppViewModel, state: AppState, onClose: () -> Unit) {
    var q by remember { mutableStateOf("") }
    val onList = state.shopping.filter { !it.checked }.map { it.name.lowercase() }.toSet()
    // Items die je nog kan terugzetten staan bovenaan; wat al op de lijst staat zakt naar onder.
    val items: List<HistoryItem> = state.history
        .filter { q.isBlank() || it.name.contains(q.trim().lowercase()) }
        .sortedWith(compareBy<HistoryItem> { it.name in onList }.thenByDescending { it.count }.thenByDescending { it.lastUsed })
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxHeight(0.85f).navigationBarsPadding().padding(horizontal = 16.dp)) {
            Text("Eerder gekocht", style = MaterialTheme.typography.titleLarge)
            Text(
                "Tik om op de lijst te zetten (nieuwe items komen onderaan de lijst). Afgevinkte items worden hier automatisch onthouden.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(q, { q = it }, singleLine = true, label = { Text("Zoeken") }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
            LazyColumn(Modifier.weight(1f)) {
                items(items, key = { it.name }) { h ->
                    val already = h.name in onList
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = !already) { vm.addFromHistory(h) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                            Text(TextUtil.capitalize(h.name), fontWeight = FontWeight.SemiBold)
                            Text(
                                if (already) "Staat al op de lijst" else "${h.count}× gekocht",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (already) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
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
