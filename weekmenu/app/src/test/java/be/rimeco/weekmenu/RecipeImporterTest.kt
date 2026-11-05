package be.rimeco.weekmenu

import be.rimeco.weekmenu.data.Tags
import be.rimeco.weekmenu.logic.RecipeImporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeImporterTest {
    private val simple = """
        <html><head><title>Pagina</title>
        <script type="application/ld+json">
        {"@context":"https://schema.org","@type":"Recipe","name":"Pasta met gerookte zalm &amp; spinazie",
         "image":["https://img.example/a.jpg"],"recipeYield":"4 personen","totalTime":"PT35M",
         "recipeIngredient":["300 g penne","200 g gerookte zalm","2 el olijfolie","1 ui, fijngesnipperd"]}
        </script></head><body></body></html>
    """.trimIndent()

    @Test fun parsesSimpleRecipe() {
        val d = RecipeImporter.parse(simple)
        assertTrue(d.foundRecipeData)
        assertEquals("Pasta met gerookte zalm & spinazie", d.title)
        assertEquals(4, d.servings)
        assertEquals(35, d.minutes)
        assertEquals("https://img.example/a.jpg", d.imageUrl)
        assertEquals(4, d.ingredientLines.size)
        assertTrue(Tags.FISH in d.tags); assertTrue(Tags.PASTA in d.tags); assertFalse(Tags.VEGETARIAN in d.tags)
    }

    @Test fun parsesGraphWithTypeArrayAndObjectImage() {
        val html = """
            <script type='application/ld+json'>
            {"@context":"https://schema.org","@graph":[
              {"@type":"WebSite","name":"Site"},
              {"@type":["Recipe","NewsArticle"],"name":"Groentecurry","image":{"@type":"ImageObject","url":"https://x/y.png"},
               "recipeYield":["3","3 porties"],"prepTime":"PT10M","cookTime":"PT1H","recipeIngredient":["1 pot kokosmelk","2 wortelen","1 el currypasta"]}
            ]}
            </script>
        """.trimIndent()
        val d = RecipeImporter.parse(html)
        assertEquals("Groentecurry", d.title)
        assertEquals(3, d.servings)
        assertEquals(70, d.minutes)
        assertEquals("https://x/y.png", d.imageUrl)
        assertTrue(Tags.VEGETARIAN in d.tags)
    }

    @Test fun parsesTopLevelArray() {
        val html = """<script type="application/ld+json">[{"@type":"Recipe","name":"X","recipeIngredient":"1 ei"}]</script>"""
        assertEquals(listOf("1 ei"), RecipeImporter.parse(html).ingredientLines)
    }

    @Test fun fallsBackToMetaWhenNoRecipeData() {
        val html = """<html><head><meta property="og:title" content="Lekker &amp; snel"><meta content="https://i/j.jpg" property="og:image"></head></html>"""
        val d = RecipeImporter.parse(html)
        assertFalse(d.foundRecipeData)
        assertEquals("Lekker & snel", d.title)
        assertEquals("https://i/j.jpg", d.imageUrl)
    }

    @Test fun ignoresBrokenJson() {
        val html = """<script type="application/ld+json">{ nope</script><script type="application/ld+json">{"@type":"Recipe","name":"Ok"}</script>"""
        assertEquals("Ok", RecipeImporter.parse(html).title)
    }

    @Test fun extractsUrlFromSharedText() {
        assertEquals("https://www.15gram.be/recept/x", RecipeImporter.extractUrl("Kijk eens: https://www.15gram.be/recept/x."))
        assertEquals("https://a.be/b", RecipeImporter.normalizeUrl("http://a.be/b"))
        assertEquals("https://a.be/b", RecipeImporter.normalizeUrl("a.be/b"))
    }

    @Test fun durations() {
        assertEquals(80, RecipeImporter.duration("PT1H20M"))
        assertEquals(45, RecipeImporter.duration("PT45M"))
        assertEquals(120, RecipeImporter.duration("PT2H"))
        assertEquals(0, RecipeImporter.duration(null))
    }

    @Test fun tagSuggestionsAvoidFalsePositives() {
        // "champignons" bevat "ham", maar is geen vlees; kippenbouillon telt niet als vlees.
        val tags = Tags.suggest("Risotto", listOf("champignons", "rijst", "kippenbouillon"))
        assertTrue(Tags.VEGETARIAN in tags); assertFalse(Tags.MEAT in tags)
    }

    @Test fun fallsBackToIngredientsRenderedByTheSitesOwnScript() {
        // Zoals Libelle Lekker: schema.org-recept zonder ingrediënten; die staan als JSON in het React-mountpunt.
        val html = """
            <script type="application/ld+json">{"@type":"Recipe","name":"Kippensoep","recipeYield":"4"}</script>
            <div id="react-recipe-ingredients" data-affiliate="" data-ingredient-groups="[{&quot;title&quot;:&quot;&quot;,&quot;ingredients&quot;:[
              {&quot;quantity&quot;:&quot;300.00&quot;,&quot;plural&quot;:0,&quot;unit&quot;:{&quot;name&quot;:&quot;g&quot;},&quot;ingredient&quot;:{&quot;nameDisplay&quot;:&quot;kipfilet&quot;,&quot;nameDisplayPlural&quot;:&quot;kipfilets&quot;}},
              {&quot;quantity&quot;:&quot;4.00&quot;,&quot;plural&quot;:1,&quot;unit&quot;:{&quot;name&quot;:&quot;&quot;},&quot;ingredient&quot;:{&quot;nameDisplay&quot;:&quot;lente-ui&quot;,&quot;nameDisplayPlural&quot;:&quot;lente-uitjes&quot;}},
              {&quot;quantity&quot;:&quot;0.00&quot;,&quot;plural&quot;:0,&quot;unit&quot;:null,&quot;ingredient&quot;:{&quot;nameDisplay&quot;:&quot;zout&quot;}},
              {&quot;quantity&quot;:&quot;1.50&quot;,&quot;plural&quot;:0,&quot;unit&quot;:{&quot;name&quot;:&quot;l&quot;},&quot;ingredient&quot;:{&quot;nameDisplay&quot;:&quot;bouillon&quot;}}]}]"></div>
        """.trimIndent().replace("\n", " ")
        val d = RecipeImporter.parse(html)
        assertEquals(listOf("300 g kipfilet", "4 lente-uitjes", "zout", "1,5 l bouillon"), d.ingredientLines)
    }

    @Test fun schemaOrgIngredientsWinOverTheFallback() {
        val html = """<script type="application/ld+json">{"@type":"Recipe","name":"X","ingredients":["1 ei"]}</script>
            <div id="react-recipe-ingredients" data-ingredient-groups="[{&quot;ingredients&quot;:[{&quot;quantity&quot;:&quot;9.00&quot;,&quot;ingredient&quot;:{&quot;nameDisplay&quot;:&quot;ander&quot;}}]}]"></div>"""
        assertEquals(listOf("1 ei"), RecipeImporter.parse(html).ingredientLines)
    }

    @Test fun suggestsDessertAndMiscTags() {
        val brownie = Tags.suggest("Brownie mugcake met 6 ingrediënten", listOf("bloem", "cacao", "suiker"))
        assertTrue(Tags.DESSERT in brownie); assertFalse(Tags.VEGETARIAN in brownie)
        assertTrue(Tags.DESSERT in Tags.suggest("Gebakken appel", listOf("appel"), categoryText = "Dessert"))
        assertTrue(Tags.DESSERT in Tags.suggest("Frozen yoghurt bites", listOf("yoghurt"), categoryText = "Nagerecht"))
        val ontbijt = Tags.suggest("Havermoutbowl", listOf("havermout", "melk"), categoryText = "Ontbijt")
        assertTrue(Tags.MISC in ontbijt); assertFalse(Tags.DESSERT in ontbijt); assertFalse(Tags.VEGETARIAN in ontbijt)
        assertTrue(Tags.MISC in Tags.suggest("Tzatzikidip", listOf("yoghurt", "komkommer")))
        // een gewoon avondmaal blijft ongemoeid, ook met woorden die erop lijken ("ijsbergsla", "champignons")
        val avond = Tags.suggest("Wraps met ijsbergsla en kip", listOf("kipfilet", "ijsbergsla", "champignons"))
        assertFalse(Tags.DESSERT in avond); assertFalse(Tags.MISC in avond); assertTrue(Tags.MEAT in avond)
        assertTrue(Tags.DESSERT in Tags.defaults && Tags.MISC in Tags.defaults)
    }

    @Test fun tagFilterWithPositiveAndNegativeSelection() {
        val dessert = listOf("Dessert", "Snel"); val soep = listOf("Soep", "Vegetarisch"); val ontbijt = listOf("Divers", "Snel")
        val exclude = setOf("Dessert", "Divers")
        assertFalse(Tags.matchesFilter(dessert, emptySet(), exclude))
        assertFalse(Tags.matchesFilter(ontbijt, emptySet(), exclude))
        assertTrue(Tags.matchesFilter(soep, emptySet(), exclude))
        // tonen en uitsluiten gecombineerd: snel, maar geen dessert
        assertTrue(Tags.matchesFilter(ontbijt, setOf("Snel"), setOf("Dessert")))
        assertFalse(Tags.matchesFilter(dessert, setOf("Snel"), setOf("Dessert")))
        // meerdere gevraagde tags: alle moeten aanwezig zijn
        assertTrue(Tags.matchesFilter(soep, setOf("Soep", "Vegetarisch"), emptySet()))
        assertFalse(Tags.matchesFilter(listOf("Soep"), setOf("Soep", "Vegetarisch"), emptySet()))
        assertTrue(Tags.matchesFilter(emptyList(), emptySet(), emptySet()))
    }

    @Test fun tagChipCyclesThroughThreeStates() {
        var inc = emptySet<String>(); var exc = emptySet<String>()
        Tags.cycle("Dessert", inc, exc).let { inc = it.first; exc = it.second }
        assertEquals(setOf("Dessert") to emptySet<String>(), inc to exc)            // 1x: tonen
        Tags.cycle("Dessert", inc, exc).let { inc = it.first; exc = it.second }
        assertEquals(emptySet<String>() to setOf("Dessert"), inc to exc)            // 2x: uitsluiten
        Tags.cycle("Dessert", inc, exc).let { inc = it.first; exc = it.second }
        assertEquals(emptySet<String>() to emptySet<String>(), inc to exc)          // 3x: uit
        assertEquals(setOf("a", "b"), Tags.decodeSet(Tags.encodeSet(setOf("b", "a"))))
        assertEquals(emptySet<String>(), Tags.decodeSet(""))
    }
}
