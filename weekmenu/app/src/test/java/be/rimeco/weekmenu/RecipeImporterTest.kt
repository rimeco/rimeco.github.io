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
}
