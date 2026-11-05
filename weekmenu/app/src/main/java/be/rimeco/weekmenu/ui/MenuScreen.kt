@file:OptIn(ExperimentalMaterial3Api::class)

package be.rimeco.weekmenu.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import be.rimeco.weekmenu.data.AppState
import be.rimeco.weekmenu.data.MenuEntry
import be.rimeco.weekmenu.data.Week
import be.rimeco.weekmenu.data.entryFor
import be.rimeco.weekmenu.data.recipe
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.IsoFields
import java.util.Locale

private val NL: Locale = Locale.forLanguageTag("nl-BE")

fun currentWeekStart(): LocalDate = Week.startOf(LocalDate.now())

fun LocalDate.dayShort(): String = dayOfWeek.getDisplayName(TextStyle.SHORT, NL).replaceFirstChar { it.uppercase() }
fun LocalDate.monthShort(): String = month.getDisplayName(TextStyle.SHORT, NL)
fun LocalDate.longLabel(): String = "${dayOfWeek.getDisplayName(TextStyle.FULL, NL)} $dayOfMonth ${month.getDisplayName(TextStyle.FULL, NL)}"

@Composable
fun MenuScreen(vm: AppViewModel, state: AppState, snackbar: SnackbarHostState) {
    var weekStart by rememberSaveable { mutableStateOf(currentWeekStart().toString()) }
    val start = LocalDate.parse(weekStart)
    val days = Week.days(start)
    val today = LocalDate.now()
    val scope = rememberCoroutineScope()
    var planDialog by remember { mutableStateOf(false) }

    fun runSuggest(dates: List<LocalDate>, replace: Boolean) {
        val (count, previous) = vm.suggest(dates, replace)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            if (count == 0) {
                snackbar.showSnackbar(
                    if (state.recipes.isEmpty()) "Voeg eerst recepten toe in het tabblad Recepten."
                    else "Geen geschikt recept gevonden. Pas je voorkeuren aan of voeg recepten toe.",
                )
            } else {
                val res = snackbar.showSnackbar(
                    if (count == 1) "1 dag ingevuld" else "$count dagen ingevuld",
                    actionLabel = "Ongedaan maken",
                    duration = SnackbarDuration.Long,
                )
                if (res == SnackbarResult.ActionPerformed) vm.restoreMenu(previous)
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ weekStart = start.minusWeeks(1).toString() }) { Icon(Icons.Filled.KeyboardArrowLeft, "Vorige week") }
            Column(Modifier.weight(1f).clickable { weekStart = currentWeekStart().toString() }, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Week ${start.plusDays(2).get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    "${start.dayOfMonth} ${start.monthShort()} – ${start.plusDays(6).dayOfMonth} ${start.plusDays(6).monthShort()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            IconButton({ weekStart = start.plusWeeks(1).toString() }) { Icon(Icons.Filled.KeyboardArrowRight, "Volgende week") }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton({ planDialog = true }, Modifier.weight(1f)) { Text("Menu voorstellen") }
            Button(
                onClick = {
                    val n = vm.addWeekToList(days)
                    scope.launch {
                        snackbar.currentSnackbarData?.dismiss()
                        snackbar.showSnackbar(
                            when {
                                n > 0 -> "Ingrediënten van $n ${if (n == 1) "maaltijd" else "maaltijden"} op de lijst gezet"
                                else -> "Niets nieuws om toe te voegen"
                            },
                        )
                    }
                },
                modifier = Modifier.weight(1f),
            ) { Text("Naar boodschappen") }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(days, key = { it.toString() }) { date ->
                DayCard(vm, state, date, isToday = date == today) { runSuggest(listOf(date), true) }
            }
        }
    }

    if (planDialog) {
        AlertDialog(
            onDismissRequest = { planDialog = false },
            title = { Text("Menu voorstellen") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Op basis van je recepten, sterren en voorkeuren. Je kan alles nadien aanpassen of ongedaan maken.")
                    OutlinedButton({ planDialog = false; runSuggest(days, true) }, Modifier.fillMaxWidth()) { Text("Hele week opnieuw") }
                }
            },
            confirmButton = { TextButton({ planDialog = false; runSuggest(days, false) }) { Text("Enkel lege dagen") } },
            dismissButton = { TextButton({ planDialog = false }) { Text("Annuleren") } },
        )
    }
}

@Composable
private fun DayCard(vm: AppViewModel, state: AppState, date: LocalDate, isToday: Boolean, onReroll: () -> Unit) {
    val entry: MenuEntry = state.entryFor(date)
    val recipe = state.recipe(entry.recipeId)
    val title = recipe?.title ?: entry.customName
    Card(
        onClick = { vm.push(Route.PickMeal(date.toString())) },
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isToday) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(date.dayShort(), style = MaterialTheme.typography.labelLarge)
                Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    title ?: "Nog niets gepland",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (title != null) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (title != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (recipe != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StarRating(recipe.rating, size = 14.dp)
                        if (recipe.minutes > 0) Text("  ${recipe.minutes} min", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Stepper(entry.persons, { vm.setPersons(date, it) }, 1..20, label = { "$it pers." })
            }
            if (recipe != null) {
                IconButton({ vm.push(Route.RecipeDetail(recipe.id, entry.persons)) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Open recept van ${date.dayShort()} ${date.dayOfMonth}")
                }
            }
            IconButton(onReroll) { Icon(Icons.Filled.Refresh, contentDescription = "Ander voorstel voor deze dag") }
        }
    }
}
