@file:OptIn(ExperimentalMaterial3Api::class)

package be.rimeco.weekmenu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import be.rimeco.weekmenu.data.Recipe

/** Haalt een recept op via een link en toont daarna het bewerkscherm om het te controleren. */
@Composable
fun ImportScreen(vm: AppViewModel, url: String) {
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var loginUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(url, attempt) {
        error = null
        loginUrl = null
        try {
            val draft = vm.fetchDraft(url)
            vm.replaceTop(Route.RecipeEdit(draft))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: be.rimeco.weekmenu.logic.RecipeImporter.LoginRequiredException) {
            loginUrl = e.siteUrl
            error = "Voor deze site moet je eerst inloggen met je eigen account."
        } catch (e: Exception) {
            error = e.message?.takeIf { it.isNotBlank() } ?: "Onbekende fout"
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Recept ophalen") }, navigationIcon = { BackButton { vm.pop() } }) }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val err = error
            if (err == null) {
                CircularProgressIndicator()
                Text("Recept wordt opgehaald…")
                Text(url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            } else {
                Text("Ophalen is niet gelukt", style = MaterialTheme.typography.titleMedium)
                Text(err, color = MaterialTheme.colorScheme.error)
                loginUrl?.let { login -> Button({ vm.push(Route.SiteLogin(login)) }) { Text("Inloggen") } }
                Button({ attempt++ }) { Text("Opnieuw proberen") }
                OutlinedButton({ vm.replaceTop(Route.RecipeEdit(Recipe(id = vm.blankRecipe().id, title = "", url = url, createdAt = System.currentTimeMillis()))) }) {
                    Text("Zelf invullen")
                }
            }
        }
    }
}
