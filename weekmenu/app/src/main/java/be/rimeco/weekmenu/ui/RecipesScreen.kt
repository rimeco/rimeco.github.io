@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package be.rimeco.weekmenu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import be.rimeco.weekmenu.data.AppState
import be.rimeco.weekmenu.logic.RecipeImporter

private enum class Sort(val label: String) { RATING("Beste eerst"), NAME("A–Z"), NEWEST("Nieuwste") }

@Composable
fun RecipesScreen(vm: AppViewModel, state: AppState) {
    var query by rememberSaveable { mutableStateOf("") }
    var sortName by rememberSaveable { mutableStateOf(Sort.RATING.name) }
    var tagFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    val sort = Sort.valueOf(sortName)

    val shown = state.recipes
        .filter { query.isBlank() || it.title.contains(query, true) || it.ingredients.any { i -> i.name.contains(query, true) } }
        .filter { tagFilter == null || tagFilter in it.tags }
        .let { list ->
            when (sort) {
                Sort.RATING -> list.sortedWith(compareByDescending<be.rimeco.weekmenu.data.Recipe> { it.rating }.thenBy { it.title.lowercase() })
                Sort.NAME -> list.sortedBy { it.title.lowercase() }
                Sort.NEWEST -> list.sortedByDescending { it.createdAt }
            }
        }
    val usedTags = state.recipes.flatMap { it.tags }.distinct().sorted()

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                label = { Text("Zoek op naam of ingrediënt") },
                modifier = Modifier.fillMaxWidth().padding(16.dp, 8.dp, 16.dp, 0.dp),
            )
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Sort.entries) { s ->
                    FilterChip(selected = sort == s, onClick = { sortName = s.name }, label = { Text(s.label) })
                }
            }
            if (usedTags.isNotEmpty()) {
                LazyRow(contentPadding = PaddingValues(16.dp, 0.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(usedTags) { t ->
                        FilterChip(selected = tagFilter == t, onClick = { tagFilter = if (tagFilter == t) null else t }, label = { Text(t) })
                    }
                }
            }
            if (state.recipes.isEmpty()) {
                Text(
                    "Nog geen recepten.\nTik op “Recept” om er een toe te voegen via een link, of deel een recept vanuit je browser met deze app.",
                    Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 88.dp)) {
                items(shown, key = { it.id }) { r -> RecipeRow(r, onClick = { vm.push(Route.RecipeDetail(r.id)) }) }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { showAdd = true },
            icon = { Icon(Icons.Filled.Add, null) },
            text = { Text("Recept") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    if (showAdd) AddRecipeDialog(vm, onDismiss = { showAdd = false })
}

@Composable
private fun AddRecipeDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    var url by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    LaunchedEffect(Unit) {
        val clip = clipboard.getText()?.text
        RecipeImporter.extractUrl(clip ?: "")?.let { url = it }
    }
    fun go() {
        if (url.isBlank()) return
        onDismiss()
        vm.push(Route.Import(url.trim()))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Recept toevoegen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Plak de link van een recept. Titel, foto, personen en ingrediënten worden automatisch ingelezen.")
                OutlinedTextField(
                    value = url, onValueChange = { url = it }, singleLine = true,
                    label = { Text("Link naar recept") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { go() }),
                )
            }
        },
        confirmButton = { Button({ go() }, enabled = url.isNotBlank()) { Text("Ophalen") } },
        dismissButton = {
            Row {
                TextButton({ onDismiss(); vm.push(Route.RecipeEdit(vm.blankRecipe())) }) { Text("Handmatig") }
                TextButton(onDismiss) { Text("Annuleren") }
            }
        },
    )
}
