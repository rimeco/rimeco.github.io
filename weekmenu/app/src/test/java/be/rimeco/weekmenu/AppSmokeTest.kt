package be.rimeco.weekmenu

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
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
import be.rimeco.weekmenu.ui.dayShort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        vm.addManual("2 kg appels")
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

        // Bulk-import: invoerscherm
        vm.push(Route.Bulk)
        rule.waitForIdle()
        rule.onNodeWithText("Recepten zoeken").assertExists()
        rule.onNodeWithText("Pagina's per link").assertExists()
        vm.pop()

        // Voorkeuren
        vm.stack.clear()
        rule.onNodeWithText("Voorkeuren").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Gezin & synchronisatie").assertExists()
        rule.onNodeWithText("Gezin aanmaken").assertExists()
        rule.onNodeWithText("Aantal personen").assertExists()
    }

    @Test fun addsMissingDessertAndMiscTagsToExistingRecipes() {
        val vm = AppViewModel(ApplicationProvider.getApplicationContext<Application>())
        vm.upsertRecipe(Recipe("a", "Chocolade brownie", tags = listOf("Vegetarisch"), ingredients = listOf(IngredientParser.parse("200 g bloem"))))
        vm.upsertRecipe(Recipe("b", "Pasta met kip", tags = listOf("Vlees", "Pasta")))
        vm.upsertRecipe(Recipe("c", "Havermout ontbijtbowl", tags = listOf("Vegetarisch", "Dessert")))   // heeft al een eigen tag: blijft ongemoeid
        assertEquals(1, vm.suggestMissingTags())
        val tags = vm.state.value.recipes.associate { it.id to it.tags }
        assertEquals(listOf("Dessert"), tags["a"])                       // Vegetarisch vervangen door Dessert
        assertEquals(listOf("Vlees", "Pasta"), tags["b"])
        assertEquals(listOf("Vegetarisch", "Dessert"), tags["c"])
        assertEquals(0, vm.suggestMissingTags())                          // tweede keer: niets meer te doen
    }

    @Test fun recipeListCanExcludeTags() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = AppViewModel(app)
        vm.upsertRecipe(Recipe("a", "Chocoladetaart", minutes = 60, tags = listOf("Dessert")))
        vm.upsertRecipe(Recipe("b", "Kippensoep", minutes = 30, tags = listOf("Soep", "Vlees")))
        vm.upsertRecipe(Recipe("c", "Smoothie", minutes = 5, tags = listOf("Divers")))
        rule.setContent { WeekmenuTheme { WeekmenuApp(vm) } }
        rule.onNodeWithText("Recepten").performClick()
        rule.onNodeWithText("Chocoladetaart").assertExists(); rule.onNodeWithText("Smoothie").assertExists()
        // Dessert: 1x tonen, 2x uitsluiten
        rule.onNodeWithText("Dessert").performClick()
        rule.onNodeWithText("Kippensoep").assertDoesNotExist()
        rule.onNodeWithText("Dessert").performClick()
        rule.onNodeWithText("Geen Dessert").assertExists()
        rule.onNodeWithText("Chocoladetaart").assertDoesNotExist()
        rule.onNodeWithText("Kippensoep").assertExists()
        // ook Divers uitsluiten: enkel de soep blijft over
        rule.onNodeWithText("Divers").performClick(); rule.onNodeWithText("Divers").performClick()
        rule.onNodeWithText("Smoothie").assertDoesNotExist()
        rule.onNodeWithText("Kippensoep").assertExists()
        // filters wissen
        rule.onNodeWithText("Wis filters").performClick()
        rule.onNodeWithText("Chocoladetaart").assertExists(); rule.onNodeWithText("Smoothie").assertExists()
    }

    @Test fun dayCardOpensTheRecipeForThatDaysPersons() {
        val vm = AppViewModel(ApplicationProvider.getApplicationContext<Application>())
        vm.upsertRecipe(Recipe("a", "Wafels van knolselderij", servings = 4, ingredients = listOf(IngredientParser.parse("400 g knolselderij"))))
        val today = LocalDate.now()
        vm.setMeal(today, "a"); vm.setPersons(today, 2)
        rule.setContent { WeekmenuTheme { WeekmenuApp(vm) } }
        rule.onNodeWithContentDescription("Open recept van ${today.dayShort()} ${today.dayOfMonth}").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Zet op boodschappenlijst").assertExists()
        rule.onNodeWithText("2 pers.").assertExists()          // het aantal personen van die dag, niet de 4 van het recept
        rule.onNodeWithText("200 g").assertExists()            // 400 g × 2/4
    }

    private fun vmWithList(vararg names: String): AppViewModel {
        val vm = AppViewModel(ApplicationProvider.getApplicationContext<Application>())
        names.forEach { vm.addManual(it) }
        return vm
    }

    @Test fun previouslyBoughtItemsCanBeAddedBackFromTheSheet() {
        val vm = vmWithList("Melk", "Brood")
        vm.state.value.shopping.forEach { vm.toggleItem(it.id) }
        vm.clearChecked()
        assertTrue(vm.state.value.shopping.isEmpty()); assertEquals(2, vm.state.value.history.size)
        rule.setContent { WeekmenuTheme { WeekmenuApp(vm) } }
        rule.onNodeWithText("Boodschappen").performClick()
        rule.onNodeWithText("Alle eerder gekochte…").performClick()
        rule.waitForIdle()
        // Semantische klik: een aanraking bereikt in Robolectric het aparte venster van het bottom sheet niet.
        rule.onAllNodesWithText("1× gekocht")[0].performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        rule.waitForIdle()
        assertEquals(1, vm.state.value.shopping.count { !it.checked })
        // het item dat nu op de lijst staat, meldt dat in het venster in plaats van enkel een vinkje
        rule.onNodeWithText("Staat al op de lijst").assertExists()
    }

    @Test fun tappingAnItemCanBeUndone() {
        val vm = vmWithList("Melk")
        rule.setContent { WeekmenuTheme { WeekmenuApp(vm) } }
        rule.onNodeWithText("Boodschappen").performClick()
        rule.onNodeWithText("Melk").performClick()                       // per ongeluk afgevinkt
        rule.waitForIdle()
        assertTrue(vm.state.value.shopping[0].checked)
        assertEquals(1, vm.state.value.history.size)
        rule.onNodeWithText("‘Melk’ afgevinkt").assertExists()
        rule.onNodeWithText("Ongedaan maken").performClick()
        rule.waitForIdle()
        assertFalse(vm.state.value.shopping[0].checked)
        assertTrue("historiek ook teruggedraaid", vm.state.value.history.isEmpty())
    }

    @Test fun deletedItemsAndClearingTheListCanBeUndone() {
        val vm = vmWithList("Melk", "Brood")
        rule.setContent { WeekmenuTheme { WeekmenuApp(vm) } }
        rule.onNodeWithText("Boodschappen").performClick()
        rule.onAllNodesWithContentDescription("Verwijderen")[0].performClick()
        rule.waitForIdle()
        assertEquals(1, vm.state.value.shopping.size)
        rule.onNodeWithText("Ongedaan maken").performClick()
        rule.waitForIdle()
        assertEquals(2, vm.state.value.shopping.size)
    }

    // De bewerkdialoog zelf wordt niet als UI-test gedraaid: een AlertDialog met tekstveld blijft in Robolectric eindeloos
    // "bezig" (ook zonder onze code). Daarom testen we de wijziging en de weergave los van de dialoog.
    @Test fun editedQuantityIsStoredAndShownExactly() {
        val vm = vmWithList("Melk")
        val id = vm.state.value.shopping[0].id
        vm.updateItem(id, "Halfvolle melk", 1.5, "l")
        val item = vm.state.value.shopping[0]
        assertEquals("halfvolle melk", item.name); assertEquals(1.5, item.quantity!!, 0.001); assertEquals("l", item.unit); assertTrue(item.manual)
        rule.setContent { WeekmenuTheme { WeekmenuApp(vm) } }
        rule.onNodeWithText("Boodschappen").performClick()
        rule.onNodeWithText("1,5 l  Halfvolle melk", substring = true).assertExists()   // niet afgerond naar "2"
        vm.updateItem(id, "Halfvolle melk", null, "l")
        assertEquals(null, vm.state.value.shopping[0].quantity); assertEquals("", vm.state.value.shopping[0].unit)
    }

    @Test fun autocompleteOffersHistoryAndRecipeIngredientsWhileTyping() {
        val vm = vmWithList()
        vm.upsertRecipe(Recipe("a", "Pasta", ingredients = listOf(IngredientParser.parse("3 teentjes knoflook"))))
        rule.setContent { WeekmenuTheme { WeekmenuApp(vm) } }
        rule.onNodeWithText("Boodschappen").performClick()
        rule.onNodeWithText("Item toevoegen (bv. 2 kg appels)").performTextInput("kno")
        rule.waitForIdle()
        rule.onNodeWithText("uit je recepten").assertExists()
        rule.onNodeWithText("Knoflook").performClick()
        rule.waitForIdle()
        assertEquals(listOf("knoflook"), vm.state.value.shopping.map { it.name })
    }
}
