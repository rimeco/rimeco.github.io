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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import be.rimeco.weekmenu.data.AppState
import be.rimeco.weekmenu.data.Recipe
import be.rimeco.weekmenu.logic.IngredientParser

@Composable
fun RecipeEditScreen(vm: AppViewModel, state: AppState, initial: Recipe) {
    val isNew = state.recipes.none { it.id == initial.id }
    var title by remember { mutableStateOf(initial.title) }
    var url by remember { mutableStateOf(initial.url) }
    var imageUrl by remember { mutableStateOf(initial.imageUrl) }
    var servings by remember { mutableIntStateOf(initial.servings) }
    var minutes by remember { mutableStateOf(if (initial.minutes > 0) initial.minutes.toString() else "") }
    var ingredients by remember { mutableStateOf(initial.ingredients.joinToString("\n") { it.raw.ifBlank { it.name } }) }
    var tags by remember { mutableStateOf(initial.tags) }
    var newTag by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf(initial.notes) }
    var rating by remember { mutableIntStateOf(initial.rating) }

    fun save() {
        val parsed = ingredients.lines().filter { it.isNotBlank() }.map { IngredientParser.parse(it) }
        val r = initial.copy(
            title = title.trim(), url = url.trim(), imageUrl = imageUrl.trim(), servings = servings,
            minutes = minutes.toIntOrNull() ?: 0, ingredients = parsed, tags = tags, notes = notes.trim(), rating = rating,
        )
        vm.upsertRecipe(r)
        if (isNew) vm.replaceTop(Route.RecipeDetail(r.id)) else vm.pop()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "Nieuw recept" else "Recept bewerken") },
                navigationIcon = { BackButton { vm.pop() } },
                actions = { IconButton({ save() }, enabled = title.isNotBlank()) { Icon(Icons.Filled.Check, "Opslaan") } },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (isNew && initial.ingredients.isEmpty() && initial.title.isBlank()) {
                Text(
                    "Er werden geen receptgegevens gevonden op deze pagina. Vul de ingrediënten zelf aan.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(title, { title = it }, label = { Text("Naam") }, modifier = Modifier.fillMaxWidth())
            Text("Beoordeling", style = MaterialTheme.typography.labelLarge)
            StarRating(rating, size = 28.dp) { rating = it }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Ingrediënten voor")
                Stepper(servings, { servings = it }, 1..99, label = { "$it pers." })
            }
            OutlinedTextField(
                minutes, { minutes = it.filter(Char::isDigit).take(4) }, label = { Text("Bereidingstijd (minuten)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                ingredients, { ingredients = it }, label = { Text("Ingrediënten (één per regel)") },
                supportingText = { Text("Bv. “200 g gerookte zalm”, “2 el olijfolie”, “1 ui”") },
                minLines = 6, modifier = Modifier.fillMaxWidth(),
            )
            Text("Tags", style = MaterialTheme.typography.labelLarge)
            val known = (state.allTagsFor(tags)).distinct()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                known.forEach { t ->
                    FilterChip(selected = t in tags, onClick = { tags = if (t in tags) tags - t else tags + t }, label = { Text(t) })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(newTag, { newTag = it }, label = { Text("Eigen tag") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedButton({
                    val t = newTag.trim().replaceFirstChar { it.uppercase() }
                    if (t.isNotEmpty() && t !in tags) tags = tags + t
                    newTag = ""
                }, enabled = newTag.isNotBlank()) { Text("Voeg toe") }
            }
            OutlinedTextField(url, { url = it }, label = { Text("Link naar recept") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(imageUrl, { imageUrl = it }, label = { Text("Link naar foto (optioneel)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(notes, { notes = it }, label = { Text("Notities") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            Text("Tip: beoordeel het recept na het koken, dan kiest het automatische menu vaker je favorieten.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Normal)
        }
    }
}

private fun AppState.allTagsFor(selected: List<String>): List<String> = (be.rimeco.weekmenu.data.Tags.defaults + recipes.flatMap { it.tags } + selected)
