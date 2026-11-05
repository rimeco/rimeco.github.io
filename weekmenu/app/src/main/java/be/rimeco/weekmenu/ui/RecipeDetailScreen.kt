@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package be.rimeco.weekmenu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import be.rimeco.weekmenu.data.AppState
import be.rimeco.weekmenu.data.recipe
import be.rimeco.weekmenu.logic.Text as TextUtil
import be.rimeco.weekmenu.logic.Units
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

@Composable
fun RecipeDetailScreen(vm: AppViewModel, state: AppState, id: String, initialPersons: Int? = null) {
    val recipe = state.recipe(id)
    if (recipe == null) { LaunchedEffect(Unit) { vm.pop() }; return }
    var persons by remember(id) { mutableIntStateOf(initialPersons ?: recipe.servings) }
    var confirmDelete by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    val factor = persons.toDouble() / recipe.servings.coerceAtLeast(1)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(recipe.title, maxLines = 1) },
                navigationIcon = { BackButton { vm.pop() } },
                actions = {
                    IconButton({ vm.push(Route.RecipeEdit(recipe)) }) { Icon(Icons.Filled.Edit, "Bewerken") }
                    IconButton({ confirmDelete = true }) { Icon(Icons.Filled.Delete, "Verwijderen") }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            if (recipe.imageUrl.isNotBlank()) {
                AsyncImage(recipe.imageUrl, null, Modifier.fillMaxWidth().height(200.dp), contentScale = ContentScale.Crop)
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(recipe.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                StarRating(recipe.rating, size = 28.dp) { vm.setRating(recipe.id, it) }
                if (recipe.minutes > 0) Text("${recipe.minutes} minuten", color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (recipe.tags.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        recipe.tags.forEach { AssistChip(onClick = {}, label = { Text(it) }) }
                    }
                }
                if (recipe.url.isNotBlank()) {
                    OutlinedButton({ runCatching { uri.openUri(recipe.url) } }, Modifier.fillMaxWidth()) { Text("Open recept op de website") }
                }
                Text("Ingrediënten", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("Voor", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Stepper(persons, { persons = it }, 1..20, label = { "$it pers." })
                }
                if (recipe.ingredients.isEmpty()) {
                    Text("Geen ingrediënten ingevuld. Tik op het potlood om ze toe te voegen.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                recipe.ingredients.forEach { ing ->
                    val qty = ing.quantity?.let { Units.format(it * factor, ing.unit) } ?: ""
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(qty, Modifier.width(88.dp), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        Text(TextUtil.capitalize(ing.name), Modifier.weight(1f))
                    }
                }
                if (recipe.ingredients.isNotEmpty()) {
                    Button(
                        onClick = {
                            vm.addRecipeToList(recipe, persons)
                            scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar("Op de boodschappenlijst gezet (voor $persons pers.)") }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Zet op boodschappenlijst") }
                }
                if (recipe.notes.isNotBlank()) {
                    Text("Notities", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(recipe.notes)
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Recept verwijderen?") },
            text = { Text("“${recipe.title}” wordt ook uit je weekmenu gehaald.") },
            confirmButton = { TextButton({ confirmDelete = false; vm.pop(); vm.deleteRecipe(recipe.id) }) { Text("Verwijderen") } },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("Annuleren") } },
        )
    }
}
