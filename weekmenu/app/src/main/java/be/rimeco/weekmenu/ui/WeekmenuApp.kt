@file:OptIn(ExperimentalMaterial3Api::class)

package be.rimeco.weekmenu.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun WeekmenuApp(vm: AppViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val top = vm.stack.lastOrNull()

    BackHandler(enabled = top != null) { vm.pop() }

    Box(Modifier.fillMaxSize()) {
        // Zolang er een scherm bovenop ligt, is de onderlaag onzichtbaar voor schermlezers (TalkBack).
        Scaffold(
            modifier = if (top != null) Modifier.clearAndSetSemantics {} else Modifier,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.Filled.DateRange, null) }, label = { Text("Weekmenu") })
                    NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.Filled.List, null) }, label = { Text("Recepten") })
                    NavigationBarItem(tab == 2, { tab = 2 }, { Icon(Icons.Filled.ShoppingCart, null) }, label = { Text("Boodschappen") })
                    NavigationBarItem(tab == 3, { tab = 3 }, { Icon(Icons.Filled.Settings, null) }, label = { Text("Voorkeuren") })
                }
            },
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                when (tab) {
                    0 -> MenuScreen(vm, state, snackbar)
                    1 -> RecipesScreen(vm, state)
                    2 -> ShoppingScreen(vm, state)
                    else -> PrefsScreen(vm, state)
                }
            }
        }
        if (top != null) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                key(top) {
                    when (top) {
                        is Route.RecipeDetail -> RecipeDetailScreen(vm, state, top.id)
                        is Route.RecipeEdit -> RecipeEditScreen(vm, state, top.initial)
                        is Route.PickMeal -> PickMealScreen(vm, state, top.date)
                        is Route.Import -> ImportScreen(vm, top.url)
                        Route.Bulk -> BulkImportScreen(vm)
                        is Route.SiteLogin -> SiteLoginScreen(vm, top.url)
                    }
                }
            }
        }
    }
}
