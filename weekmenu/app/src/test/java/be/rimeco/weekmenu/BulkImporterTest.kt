package be.rimeco.weekmenu

import be.rimeco.weekmenu.logic.BulkImporter
import be.rimeco.weekmenu.logic.LinkHarvester
import be.rimeco.weekmenu.logic.Pagination
import be.rimeco.weekmenu.logic.RecipeImporter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class BulkImporterTest {
    private fun recipePage(title: String, vararg ing: String) =
        """<script type="application/ld+json">{"@type":"Recipe","name":"$title","recipeYield":"4","recipeIngredient":[${ing.joinToString(",") { "\"$it\"" }}]}</script>"""

    private fun links(vararg hrefs: String) = hrefs.joinToString("\n") { """<a href="$it">x</a>""" }

    @Test fun harvestsInternalLinksOnly() {
        val html = links(
            "/recept/soep/", "https://www.site.be/recept/pasta/?utm=1#top", "recept/relatief", "#anker", "mailto:a@b.be",
            "https://facebook.com/site", "/foto.jpg", "/alle/page/2/", "?page=3#pagination", "/", "/alle", "/recept/soep",
        )
        val c = LinkHarvester.candidates(html, "https://site.be/alle")
        assertEquals(
            listOf("https://site.be/recept/soep/", "https://www.site.be/recept/pasta/", "https://site.be/recept/relatief"),
            c,   // "recept/relatief" is relatief t.o.v. https://site.be/alle, dus https://site.be/recept/relatief
        )
        assertFalse(c.any { it.contains("facebook") || it.endsWith(".jpg") || it.contains("page") })
    }

    @Test fun paginationPatterns() {
        // Pagina zelf wijst naar /page/2/ (WordPress-stijl)
        val wp = Pagination.pageUrls("https://chicks.com/alle-recepten/", links("/alle-recepten/page/2/", "/alle-recepten/page/3/"), 3)
        assertEquals(listOf("https://chicks.com/alle-recepten/", "https://chicks.com/alle-recepten/page/2/", "https://chicks.com/alle-recepten/page/3/"), wp)
        // ?page=N in de pagina
        val q = Pagination.pageUrls("https://hippe.nl/posts/recepten/", links("/posts/recepten?page=1#pagination", "/posts/recepten?page=2#pagination"), 3)
        assertEquals("https://hippe.nl/posts/recepten?page=2", q[1]); assertEquals("https://hippe.nl/posts/recepten?page=3", q[2])
        // opgegeven URL bevat al een paginanummer: ga vanaf daar verder
        val fromUrl = Pagination.pageUrls("https://hippe.nl/posts/recepten?page=2#pagination", "", 3)
        assertEquals(listOf("https://hippe.nl/posts/recepten?page=2", "https://hippe.nl/posts/recepten?page=3", "https://hippe.nl/posts/recepten?page=4"), fromUrl)
        assertEquals("https://x.be/a/page/6/", Pagination.pageUrls("https://x.be/a/page/5/", "", 2)[1])
        // geen paginering -> één pagina
        assertEquals(listOf("https://colruyt.be/weekmenu?week=currentWeek"), Pagination.pageUrls("https://colruyt.be/weekmenu?week=currentWeek", links("/nl/x"), 5))
    }

    @Test fun keepsRecipesSkipsJunkAndStopsProbingJunkGroups() = runBlocking {
        val pages = mutableMapOf<String, String>()
        pages["https://site.be/lijst"] = links(
            "/recept/a", "/recept/b", "/recept/c", "/recept/geen-recept-1",
            "/info/over-ons", "/info/contact", "/info/vacatures", "/info/privacy", "/info/cookies", "/info/team",
        )
        listOf("over-ons", "contact", "vacatures", "privacy", "cookies", "team").forEach { pages["https://site.be/info/$it"] = "<html>gewone pagina</html>" }
        pages["https://site.be/recept/a"] = recipePage("Soep A", "1 ui", "200 g pompoen")
        pages["https://site.be/recept/b"] = recipePage("Pasta B", "300 g pasta")
        pages["https://site.be/recept/c"] = recipePage("Soep a", "1 ui")           // zelfde titel als A -> dubbel
        pages["https://site.be/recept/geen-recept-1"] = "<html>geen data</html>"
        val fetched = mutableListOf<String>()
        val found = mutableListOf<BulkImporter.Found>()
        BulkImporter(fetch = { u -> fetched += u; pages[u] ?: throw IOException("404") }, politenessDelayMs = 0)
            .run(listOf("https://site.be/lijst"), 1, emptySet(), emptySet(), onProgress = {}, onFound = { found += it })
        assertEquals(setOf("Soep A", "Pasta B"), found.map { it.draft.title }.toSet())
        // /info/ is een junk-groep: na 3 geladen pagina's zonder recept worden de overige 3 links niet meer opgehaald
        assertEquals(3, fetched.count { it.contains("/info/") })
    }

    @Test fun failingRequestsDoNotPoisonAGroupButStopItAfterFive() = runBlocking {
        val pages = mutableMapOf("https://site.be/lijst" to links(*(1..12).map { "/r/$it" }.toTypedArray()))
        // de eerste 3 mislukken (tijdelijke fout), daarna komen er echte recepten
        (4..12).forEach { pages["https://site.be/r/$it"] = recipePage("Recept $it", "1 ei") }
        val found = mutableListOf<String>()
        BulkImporter(fetch = { pages[it] ?: throw IOException("HTTP 503") }, politenessDelayMs = 0)
            .run(listOf("https://site.be/lijst"), 1, emptySet(), emptySet(), onProgress = {}, onFound = { found += it.draft.title })
        assertEquals(9, found.size)

        // alles mislukt (bv. site blokkeert ons): stop na 5 aanvragen in plaats van alle 12 te proberen
        var attempts = 0
        BulkImporter(fetch = { u -> if (u.contains("/r/")) { attempts++; throw IOException("HTTP 403") } else pages[u]!! }, politenessDelayMs = 0)
            .run(listOf("https://site.be/lijst"), 1, emptySet(), emptySet(), onProgress = {}, onFound = {})
        assertEquals(5, attempts)
    }

    @Test fun menuLinksAtTheStartOfARecipeGroupDoNotHideTheRecipes() = runBlocking {
        // Zoals bij Colruyt: /nl/recepten/alle-recepten, /weekmenu, /favorieten staan vóór de echte recepten in dezelfde pad-groep.
        val nav = listOf("alle-recepten", "weekmenu", "favorieten", "inspiratie")
        val pages = mutableMapOf("https://site.be/lijst" to links(*(nav + (1..12).map { "recept-$it" }).map { "/nl/recepten/$it" }.toTypedArray()))
        nav.forEach { pages["https://site.be/nl/recepten/$it"] = "<html>menu</html>" }
        (1..12).forEach { pages["https://site.be/nl/recepten/recept-$it"] = recipePage("Recept $it", "1 ei") }
        val found = mutableListOf<String>()
        BulkImporter(fetch = { pages[it] ?: throw IOException("404") }, politenessDelayMs = 0)
            .run(listOf("https://site.be/lijst"), 1, emptySet(), emptySet(), onProgress = {}, onFound = { found += it.draft.title })
        assertEquals(12, found.size)
    }

    @Test fun skipsRecipesYouAlreadyHaveAndFollowsPages() = runBlocking {
        val pages = mapOf(
            "https://site.be/alle" to links("/r/een", "/r/twee", "/alle/page/2/"),
            "https://site.be/alle/page/2/" to links("/r/drie", "/r/vier"),
            "https://site.be/r/een" to recipePage("Een", "1 ei"),
            "https://site.be/r/twee" to recipePage("Twee", "2 eieren"),
            "https://site.be/r/drie" to recipePage("Drie", "3 eieren"),
            "https://site.be/r/vier" to recipePage("Vier", "4 eieren"),
        )
        val found = mutableListOf<String>()
        BulkImporter(fetch = { pages[it] ?: throw IOException("404") }, politenessDelayMs = 0)
            .run(listOf("https://site.be/alle"), 2, knownUrls = setOf("https://www.site.be/r/twee/"), knownTitles = setOf("Vier"), onProgress = {}, onFound = { found += it.draft.title })
        assertEquals(setOf("Een", "Drie"), found.toSet())   // Twee: URL gekend, Vier: titel gekend
    }

    @Test fun respectsMaxChecksAndSurvivesFailingListing() = runBlocking {
        val pages = mutableMapOf("https://site.be/lijst" to links(*(1..30).map { "/r/$it" }.toTypedArray()))
        (1..30).forEach { pages["https://site.be/r/$it"] = recipePage("Recept $it", "1 ei") }
        var fetchedRecipes = 0
        val found = mutableListOf<String>()
        BulkImporter(fetch = { u -> if (u.contains("/r/")) fetchedRecipes++; if (u.contains("kapot")) throw IOException("HTTP 500"); pages[u] ?: throw IOException("404") }, politenessDelayMs = 0)
            .run(listOf("https://site.be/kapot", "https://site.be/lijst"), 1, emptySet(), emptySet(), maxChecks = 10, onProgress = {}, onFound = { found += it.draft.title })
        assertEquals(10, fetchedRecipes)
        assertEquals(10, found.size)
    }

    @Test fun datedListingsStepBackOneWeekAtATime() {
        // Maandag: de maandagen daarvoor
        assertEquals(
            listOf(
                "https://www.libelle-lekker.be/weekmenu/2026-11-09", "https://www.libelle-lekker.be/weekmenu/2026-11-02",
                "https://www.libelle-lekker.be/weekmenu/2026-10-26", "https://www.libelle-lekker.be/weekmenu/2026-10-19",
            ),
            Pagination.pageUrls("https://www.libelle-lekker.be/weekmenu/2026-11-09", "", 4),
        )
        // Donderdag 5 nov: de maandag van die week is 2 nov, dan 26 okt, ...
        assertEquals(
            listOf("https://www.libelle-lekker.be/weekmenu/2026-11-05", "https://www.libelle-lekker.be/weekmenu/2026-11-02", "https://www.libelle-lekker.be/weekmenu/2026-10-26"),
            Pagination.pageUrls("https://www.libelle-lekker.be/weekmenu/2026-11-05", "", 3),
        )
        assertEquals(listOf("https://x.be/weekmenu/2026-11-09/"), Pagination.pageUrls("https://x.be/weekmenu/2026-11-09/", "", 1))
        // datum buiten het pad (bv. een artikelnummer) wordt niet aangezien voor een datum
        assertEquals(listOf("https://x.be/artikelen/184495/soep"), Pagination.pageUrls("https://x.be/artikelen/184495/soep", "", 3))
    }

    @Test fun overlappingDatedListingsAreFetchedOnce() = runBlocking {
        val listingFetches = mutableListOf<String>()
        BulkImporter(fetch = { u -> if (u.contains("/weekmenu/")) listingFetches += u; "<html></html>" }, politenessDelayMs = 0)
            .run(
                listOf("https://s.be/weekmenu/2026-11-16", "https://s.be/weekmenu/2026-11-09"), 3,
                emptySet(), emptySet(), onProgress = {}, onFound = {},
            )
        // 16 nov + 9 nov + 2 nov, daarna 9 nov (al gedaan), 2 nov (al gedaan), 26 okt
        assertEquals(
            listOf("2026-11-16", "2026-11-09", "2026-11-02", "2026-10-26").map { "https://s.be/weekmenu/$it" }.sorted(),
            listingFetches.distinct().sorted(),
        )
    }

    @Test fun loginWallsAreRecognisedAndReported() {
        assertTrue(RecipeImporter.looksLikeLogin("https://sso.roularta.be/login"))
        assertTrue(RecipeImporter.looksLikeLogin("https://token.roularta.be/oauth/v1/authorize?client_id=1"))
        assertTrue(RecipeImporter.looksLikeLogin("https://site.be/inloggen"))
        assertFalse(RecipeImporter.looksLikeLogin("https://www.libelle-lekker.be/weekmenu/2026-11-09"))
        assertFalse(RecipeImporter.looksLikeLogin("https://site.be/recept/inloggen-bij-de-bakker"))
    }

    @Test fun loginRequirementIsPassedOnToTheScreen() = runBlocking {
        var last: BulkImporter.Progress? = null
        BulkImporter(fetch = { throw RecipeImporter.LoginRequiredException("https://www.libelle-lekker.be/weekmenu/2026-11-09") }, politenessDelayMs = 0)
            .run(listOf("https://www.libelle-lekker.be/weekmenu/2026-11-09"), 1, emptySet(), emptySet(), onProgress = { last = it }, onFound = {})
        assertEquals("https://www.libelle-lekker.be/weekmenu/2026-11-09", last?.loginUrl)
    }

    @Test fun groupsLinksWithNumericIdsTogether() {
        assertEquals("/bekijk-recept/#", LinkHarvester.groupOf("https://s.be/bekijk-recept/90009/kippensoep-met-mais"))
        assertEquals("/bekijk-recept/#", LinkHarvester.groupOf("https://s.be/bekijk-recept/92938/wortelsoep"))
        assertEquals("/bekijk-recept/#", LinkHarvester.groupOf("https://s.be/bekijk-recept/92938"))
        assertEquals("/recept", LinkHarvester.groupOf("https://chicks.com/recept/miso-egg-toast"))
        assertEquals("/nl/recepten", LinkHarvester.groupOf("https://colruyt.be/nl/recepten/herfstsoep"))
        assertEquals("/#", LinkHarvester.groupOf("https://blog.be/2024/05/pompoensoep"))
    }

    @Test fun libelleStyleWeekmenuIsImportedEvenThoughEveryRecipeHasItsOwnId() = runBlocking {
        val pages = mutableMapOf("https://s.be/weekmenu/2026-11-09" to links("/zoeken", "/profiel", "/weekmenu/2026-11-16", *(1..8).map { "/bekijk-recept/${90000 + it}/recept-$it" }.toTypedArray()))
        (1..8).forEach { pages["https://s.be/bekijk-recept/${90000 + it}/recept-$it"] = recipePage("Recept $it", "1 ei") }
        pages["https://s.be/zoeken"] = "<html>zoek</html>"; pages["https://s.be/profiel"] = "<html>profiel</html>"
        val found = mutableListOf<String>()
        BulkImporter(fetch = { pages[it] ?: throw IOException("404") }, politenessDelayMs = 0)
            .run(listOf("https://s.be/weekmenu/2026-11-09"), 1, emptySet(), emptySet(), onProgress = {}, onFound = { found += it.draft.title })
        assertEquals(8, found.size)
    }
}
