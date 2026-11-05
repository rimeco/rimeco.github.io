package be.rimeco.weekmenu.ui

import be.rimeco.weekmenu.data.Recipe

sealed interface Route {
    data class RecipeDetail(val id: String) : Route
    /** [initial] is een bestaand recept (bewerken) of een nieuw/ingelezen concept. */
    data class RecipeEdit(val initial: Recipe) : Route
    data class PickMeal(val date: String) : Route
    data class Import(val url: String) : Route
    data object Bulk : Route
    /** Ingebouwde browser om in te loggen bij een site die een account vraagt. */
    data class SiteLogin(val url: String) : Route
}
