package be.rimeco.weekmenu.ui

import android.webkit.CookieManager
import be.rimeco.weekmenu.logic.RecipeImporter

/** Deelt de cookies van de ingebouwde browser (waarin je inlogt) met de recept-importer. */
object WebCookieJar : RecipeImporter.CookieJar {
    override fun get(url: String): String? = runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull()

    override fun set(url: String, setCookieHeaders: List<String>) {
        runCatching {
            val cm = CookieManager.getInstance()
            setCookieHeaders.forEach { cm.setCookie(url, it) }
        }
    }
}
