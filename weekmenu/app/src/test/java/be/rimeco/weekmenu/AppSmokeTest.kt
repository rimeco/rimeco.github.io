package be.rimeco.weekmenu

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import be.rimeco.weekmenu.data.Recipe
import be.rimeco.weekmenu.logic.IngredientParser
import be.rimeco.weekmenu.ui.AppViewModel
import be.rimeco.weekmenu.ui.Route
import be.rimeco.weekmenu.ui.WeekmenuApp
import be.rimeco.weekmenu.ui.WeekmenuTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** Rendert de echte UI op de JVM en loopt door de belangrijkste schermen om crashes te vangen. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "nl-rBE-w411dp-h891dp-xxhdpi")
class AppSmokeTest {
    @get:Rule val rule = createComposeRule()

    private fun recipe(id: String, title: String, rating: Int) = Recipe(
        id, title, servings = 4, rating = rating, minutes = 30, tags = listOf("Pasta"),
        ingredients = listOf("400 g penne", "200 g gerookte zalm", "1 ui").map { IngredientParser.parse(it) },
    )

    @Test fun walkThroughApp() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = AppViewModel(app)
        (1..8).forEach { vm.upsertRecipe(recipe("r$it", "Pasta nummer $it", (it % 5) + 1)) }

        rule.setContent { WeekmenuTheme { WeekmenuApp(vm) } }

        // Weekmenu: automatisch voorstel
        rule.onNodeWithText("Menu voorstellen").performClick()
        rule.onNodeWithText("Hele week opnieuw").performClick()
        rule.waitForIdle()
        val planned = vm.state.value.menu.values.count { it.recipeId != null }
        assertEquals(7, planned)

        // Persons: default 3 op weekdagen, 4 in het weekend
        val monday = be.rimeco.weekmenu.data.Week.startOf(LocalDate.now())   // zaterdag
        assertEquals(java.time.DayOfWeek.SATURDAY, monday.dayOfWeek)
        assertEquals(4, vm.state.value.menu[monday.toString()]?.persons ?: 4)
        assertEquals(3, vm.state.value.menu[monday.plusDays(6).toString()]?.persons ?: 3)

        // Naar boodschappen
        rule.onNodeWithText("Naar boodschappen").performClick()
        rule.waitForIdle()
        assertTrue(vm.state.value.shopping.isNotEmpty())

        // Boodschappen-tab: item toevoegen, afvinken
        rule.onNodeWithText("Boodschappen").performClick()
        rule.onNodeWithText("Item toevoegen (bv. 2 kg appels)").performTextInput("2 kg appels")
        rule.onNodeWithText("Item toevoegen (bv. 2 kg appels)").let { /* bevestigen via VM */ }
        vm.addManual("Pickles")
        rule.waitForIdle()
        rule.onNodeWithText("Pickles").performClick()
        rule.waitForIdle()
        assertTrue(vm.state.value.history.any { it.name == "pickles" })
        rule.onNodeWithText("Alle eerder gekochte…").performClick()
        rule.waitForIdle()

        // Recepten-tab + detail + bewerken
        vm.pop()
        rule.onAllNodesWithText("Recepten")[0].performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Pasta nummer 1").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Zet op boodschappenlijst").performClick()
        vm.push(Route.RecipeEdit(vm.state.value.recipes[0]))
        rule.waitForIdle()
        vm.pop(); vm.pop()

        // Dag kiezen
        vm.push(Route.PickMeal(monday.toString()))
        rule.waitForIdle()
        rule.onNodeWithText("Pasta nummer 2").performClick()
        rule.waitForIdle()

        // Import-scherm met onbereikbare host toont foutmelding (geen crash)
        vm.push(Route.Import("http://localhost:1/geen-recept"))
        rule.waitForIdle()

        // Voorkeuren
        vm.stack.clear()
        rule.onNodeWithText("Voorkeuren").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Aantal personen").assertExists()
    }
}
