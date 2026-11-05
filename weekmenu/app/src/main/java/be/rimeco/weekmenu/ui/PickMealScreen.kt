@file:OptIn(ExperimentalMaterial3Api::class)

package be.rimeco.weekmenu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import be.rimeco.weekmenu.data.AppState
import be.rimeco.weekmenu.data.entryFor
import java.time.LocalDate

@Composable
fun PickMealScreen(vm: AppViewModel, state: AppState, dateStr: String) {
    val date = LocalDate.parse(dateStr)
    val entry = state.entryFor(date)
    var query by remember { mutableStateOf("") }
    // Voor een avondmaal staat "Geen Dessert" standaard aan; tik erop om desserts toch te zien.
    var include by remember { mutableStateOf(emptySet<String>()) }
    var exclude by remember { mutableStateOf(setOf(be.rimeco.weekmenu.data.Tags.DESSERT)) }
    var custom by remember { mutableStateOf(entry.customName ?: "") }
    val recipes = state.recipes
        .filter { be.rimeco.weekmenu.data.Tags.matchesFilter(it.tags, include, exclude) }
        .filter { query.isBlank() || it.title.contains(query, ignoreCase = true) || it.tags.any { t -> t.contains(query, ignoreCase = true) } }
        .sortedWith(compareByDescending { it.rating })

    fun saveCustom() {
        if (custom.isNotBlank()) { vm.setMeal(date, null, custom); vm.pop() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(date.longLabel().replaceFirstChar { it.uppercase() }) },
                navigationIcon = { BackButton { vm.pop() } },
            )
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            item {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    label = { Text("Zoek in je recepten") },
                    modifier = Modifier.fillMaxWidth().padding(16.dp, 8.dp),
                )
            }
            item {
                TagFilterRow(
                    (state.recipes.flatMap { it.tags } + include + exclude).distinct().sorted(), include, exclude,
                    onChange = { i, e -> include = i; exclude = e },
                )
            }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = custom, onValueChange = { custom = it }, singleLine = true,
                        label = { Text("Of eigen invoer (bv. restaurant, restjes)") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { saveCustom() }),
                        modifier = Modifier.weight(1f),
                    )
                    Button({ saveCustom() }, enabled = custom.isNotBlank()) { Text("OK") }
                }
            }
            if (entry.recipeId != null || !entry.customName.isNullOrBlank()) {
                item { TextButton({ vm.clearMeal(date); vm.pop() }, Modifier.padding(horizontal = 8.dp)) { Text("Maaltijd wissen") } }
            }
            if (state.recipes.isEmpty()) {
                item {
                    Text(
                        "Je hebt nog geen recepten. Voeg er toe in het tabblad Recepten (via link of handmatig).",
                        Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(recipes, key = { it.id }) { r ->
                RecipeRow(r, onClick = { vm.setMeal(date, r.id); vm.pop() })
            }
        }
    }
}
