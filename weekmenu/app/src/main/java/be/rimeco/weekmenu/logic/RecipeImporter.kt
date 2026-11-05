package be.rimeco.weekmenu.logic

import be.rimeco.weekmenu.data.Tags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset

/** Haalt een recept op uit een webpagina via de schema.org/Recipe-gegevens (JSON-LD) die vrijwel alle receptensites bevatten. */
object RecipeImporter {
    data class Draft(
        val title: String,
        val imageUrl: String = "",
        val servings: Int = 4,
        val minutes: Int = 0,
        val ingredientLines: List<String> = emptyList(),
        val tags: List<String> = emptyList(),
        /** false als de pagina geen herkenbare receptgegevens bevatte (dan is enkel de titel ingevuld). */
        val foundRecipeData: Boolean = true,
    )

    private val json = Json { isLenient = true; ignoreUnknownKeys = true }
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    /** Zoekt de eerste URL in gedeelde tekst ("Bekijk dit recept https://..."). */
    fun extractUrl(text: String): String? =
        Regex("""https?://[^\s<>"']+""").find(text)?.value?.trimEnd(')', '.', ',', ';', '!')

    fun normalizeUrl(input: String): String {
        val t = (extractUrl(input) ?: input.trim())
        return when {
            t.startsWith("http://") -> "https://" + t.removePrefix("http://")
            t.startsWith("https://") -> t
            else -> "https://$t"
        }
    }

    /** Haalt de HTML van een pagina op (voor bulk-import). */
    suspend fun fetchHtml(url: String): String {
        val normalized = normalizeUrl(url)
        return withContext(Dispatchers.IO) { download(normalized) }
    }

    suspend fun fetchDraft(url: String): Draft {
        val normalized = normalizeUrl(url)
        val html = withContext(Dispatchers.IO) { download(normalized) }
        return parse(html)
    }

    // ---------- netwerk ----------

    private fun download(startUrl: String): String {
        var current = URL(startUrl)
        repeat(6) {
            val conn = current.openConnection() as HttpURLConnection
            try {
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 15_000
                conn.readTimeout = 20_000
                conn.setRequestProperty("User-Agent", USER_AGENT)
                conn.setRequestProperty("Accept", "text/html,application/xhtml+xml")
                conn.setRequestProperty("Accept-Language", "nl-BE,nl;q=0.9,en;q=0.7")
                val code = conn.responseCode
                if (code in 300..399) {
                    val loc = conn.getHeaderField("Location") ?: throw IOException("Doorverwijzing zonder adres")
                    current = URL(current, loc)
                    return@repeat
                }
                if (code >= 400) throw IOException("De pagina gaf fout $code")
                val bytes = conn.inputStream.use { it.readNBytesLimited(6_000_000) }
                return decode(bytes, conn.contentType)
            } finally {
                conn.disconnect()
            }
        }
        throw IOException("Te veel doorverwijzingen")
    }

    private fun java.io.InputStream.readNBytesLimited(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (out.size() < limit) {
            val n = read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private fun decode(bytes: ByteArray, contentType: String?): String {
        val fromHeader = contentType?.let { Regex("charset=([\\w-]+)", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1) }
        val sniff = String(bytes, 0, minOf(bytes.size, 3000), Charsets.ISO_8859_1)
        val fromMeta = Regex("""charset=["']?([\w-]+)""", RegexOption.IGNORE_CASE).find(sniff)?.groupValues?.get(1)
        val cs = runCatching { Charset.forName(fromHeader ?: fromMeta ?: "UTF-8") }.getOrDefault(Charsets.UTF_8)
        return String(bytes, cs)
    }

    // ---------- parsing (zuivere functie, getest zonder netwerk) ----------

    fun parse(html: String): Draft {
        val recipe = findRecipeNodes(html).firstOrNull()
        if (recipe == null) {
            return Draft(
                title = metaContent(html, "og:title") ?: titleTag(html) ?: "",
                imageUrl = metaContent(html, "og:image") ?: "",
                foundRecipeData = false,
            )
        }
        val title = Text.clean(recipe["name"].firstString() ?: "")
        val ingredients = (recipe["recipeIngredient"] ?: recipe["ingredients"]).allStrings()
            .map { Text.clean(it) }.filter { it.isNotBlank() }
        val servings = recipe["recipeYield"].allStrings()
            .firstNotNullOfOrNull { Regex("\\d+").find(it)?.value?.toIntOrNull() }
            ?.coerceIn(1, 99) ?: 4
        val minutes = duration(recipe["totalTime"].firstString())
            .takeIf { it > 0 }
            ?: (duration(recipe["prepTime"].firstString()) + duration(recipe["cookTime"].firstString()))
        val category = (recipe["recipeCategory"].allStrings() + recipe["keywords"].allStrings()).joinToString(" ")
        val names = ingredients.map { IngredientParser.parse(it).name }
        return Draft(
            title = title.ifBlank { metaContent(html, "og:title") ?: titleTag(html) ?: "" },
            imageUrl = imageOf(recipe["image"]) ?: metaContent(html, "og:image") ?: "",
            servings = servings,
            minutes = minutes,
            ingredientLines = ingredients,
            tags = Tags.suggest(title, names, category, minutes),
            foundRecipeData = true,
        )
    }

    private fun findRecipeNodes(html: String): List<JsonObject> {
        val scripts = Regex(
            """<script[^>]*type\s*=\s*["']application/ld\+json["'][^>]*>(.*?)</script>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        ).findAll(html).map { it.groupValues[1].trim() }
        val found = mutableListOf<JsonObject>()
        for (s in scripts) {
            val el = runCatching { json.parseToJsonElement(s) }.getOrNull() ?: continue
            collect(el, found)
        }
        return found
    }

    private fun collect(el: JsonElement, out: MutableList<JsonObject>) {
        when (el) {
            is JsonArray -> el.forEach { collect(it, out) }
            is JsonObject -> {
                val type = el["@type"]
                val isRecipe = type.allStrings().any { it.equals("Recipe", true) || it.endsWith("/Recipe", true) }
                if (isRecipe) out += el
                el["@graph"]?.let { collect(it, out) }
                el["mainEntity"]?.let { collect(it, out) }
            }
            else -> {}
        }
    }

    private fun JsonElement?.firstString(): String? = allStrings().firstOrNull()

    private fun JsonElement?.allStrings(): List<String> = when (this) {
        null, JsonNull -> emptyList()
        is JsonPrimitive -> listOf(content)
        is JsonArray -> flatMap { it.allStrings() }
        is JsonObject -> (this["text"] ?: this["name"]).allStrings()
    }

    private fun imageOf(el: JsonElement?): String? = when (el) {
        null, JsonNull -> null
        is JsonPrimitive -> el.content.takeIf { it.startsWith("http") }
        is JsonArray -> el.firstNotNullOfOrNull { imageOf(it) }
        is JsonObject -> imageOf(el["url"] ?: el["contentUrl"])
    }

    /** ISO-8601 duur ("PT1H20M") naar minuten. */
    fun duration(iso: String?): Int {
        if (iso.isNullOrBlank()) return 0
        val t = iso.substringAfter('T', iso)
        val d = Regex("(\\d+)\\s*D", RegexOption.IGNORE_CASE).find(iso.substringBefore('T'))?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val h = Regex("(\\d+)H", RegexOption.IGNORE_CASE).find(t)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val m = Regex("(\\d+)M", RegexOption.IGNORE_CASE).find(t)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        return d * 1440 + h * 60 + m
    }

    private fun metaContent(html: String, property: String): String? {
        val p = Regex.escape(property)
        val a = Regex("""<meta[^>]+(?:property|name)=["']$p["'][^>]*content=["']([^"']*)["']""", RegexOption.IGNORE_CASE)
        val b = Regex("""<meta[^>]+content=["']([^"']*)["'][^>]*(?:property|name)=["']$p["']""", RegexOption.IGNORE_CASE)
        return (a.find(html) ?: b.find(html))?.groupValues?.get(1)?.let { Text.clean(it) }?.takeIf { it.isNotBlank() }
    }

    private fun titleTag(html: String): String? =
        Regex("""<title[^>]*>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(html)?.groupValues?.get(1)?.let { Text.clean(it) }?.takeIf { it.isNotBlank() }
}
