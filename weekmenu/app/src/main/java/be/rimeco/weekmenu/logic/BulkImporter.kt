package be.rimeco.weekmenu.logic

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.coroutineContext

/** Verzamelt links uit een overzichtspagina. */
object LinkHarvester {
    private val hrefRe = Regex("""<a\s[^>]*?href\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val assetRe = Regex("""\.(jpe?g|png|gif|webp|svg|pdf|css|js|ico|xml|zip|mp4|mp3)$""", RegexOption.IGNORE_CASE)
    private val pagePathRe = Regex("""/page/\d+/?$""")
    private val pageQueryRe = Regex("""(^|&)(page|paged|pagina|p)=\d+""")

    /** Vergelijkingssleutel van een URL: host (zonder www) + pad, zonder query/fragment/slash achteraan. */
    fun key(url: String): String = runCatching {
        val u = URI(url.substringBefore('#'))
        (u.host ?: "").lowercase().removePrefix("www.") + (u.path ?: "").trimEnd('/')
    }.getOrDefault(url)

    /** Pad zonder laatste segment: "/recept/soep" -> "/recept". Wordt gebruikt om links te groeperen. */
    fun parentOf(url: String): String = runCatching {
        URI(url).path.trimEnd('/').substringBeforeLast('/', "")
    }.getOrDefault("")

    /** Alle interne links (zelfde site) die geen bijlage, paginering of de pagina zelf zijn; zonder query en fragment. */
    fun candidates(html: String, pageUrl: String): List<String> {
        val base = runCatching { URI(pageUrl.substringBefore('#')) }.getOrNull() ?: return emptyList()
        val host = base.host?.lowercase()?.removePrefix("www.") ?: return emptyList()
        val self = key(pageUrl)
        val out = LinkedHashMap<String, String>()
        for (m in hrefRe.findAll(html)) {
            val raw = Text.unescapeHtml(m.groupValues[1].trim())
            if (raw.isEmpty() || raw.startsWith("#") || raw.startsWith("mailto:") || raw.startsWith("tel:") || raw.startsWith("javascript:")) continue
            val u = runCatching { base.resolve(raw.replace(" ", "%20")) }.getOrNull() ?: continue
            if (u.scheme != "http" && u.scheme != "https") continue
            val h = u.host?.lowercase() ?: continue
            if (h.removePrefix("www.") != host) continue
            val path = u.path ?: ""
            if (path.isEmpty() || path == "/") continue
            if (assetRe.containsMatchIn(path) || pagePathRe.containsMatchIn(path)) continue
            if (u.query != null && pageQueryRe.containsMatchIn(u.query)) continue
            val clean = "https://${u.host}$path"
            val k = key(clean)
            if (k == self) continue
            out.putIfAbsent(k, clean)
        }
        return out.values.toList()
    }
}

/** Herkent "volgende pagina"-patronen: /page/2/, ?page=2, ?paged=2, ?pagina=2. */
object Pagination {
    data class Template(val prefix: String, val suffix: String, val start: Int) {
        fun url(n: Int) = prefix + n + suffix
    }

    private val queryStyle = Regex("""^([^#]*?[?&](?:page|paged|pagina)=)(\d+)([^#]*)""")
    private val pathStyle = Regex("""^(.*?/page/)(\d+)(/?)$""")

    /** De URL bevat zelf al een paginanummer ("...?page=2#pagination"); we gaan vanaf dat nummer verder. */
    fun fromUrl(url: String): Template? {
        val noFragment = url.substringBefore('#')
        queryStyle.find(noFragment)?.let { return Template(it.groupValues[1], it.groupValues[3], it.groupValues[2].toInt()) }
        val path = noFragment.substringBefore('?')
        pathStyle.find(path)?.let { m ->
            val query = noFragment.substringAfter('?', "").let { if (it.isEmpty()) "" else "?$it" }
            return Template(m.groupValues[1], m.groupValues[3] + query, m.groupValues[2].toInt())
        }
        return null
    }

    /** Zoekt in de pagina zelf naar links naar volgende pagina's van dezelfde lijst. */
    fun fromHtml(html: String, pageUrl: String): Template? {
        val base = runCatching { URI(pageUrl.substringBefore('#')) }.getOrNull() ?: return null
        val host = base.host?.lowercase()?.removePrefix("www.") ?: return null
        val pagePath = (base.path ?: "").trimEnd('/')
        for (m in Regex("""<a\s[^>]*?href\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE).findAll(html)) {
            val raw = Text.unescapeHtml(m.groupValues[1].trim())
            val u = runCatching { base.resolve(raw.replace(" ", "%20")) }.getOrNull() ?: continue
            if (u.host?.lowercase()?.removePrefix("www.") != host) continue
            val path = u.path ?: ""
            pathStyle.find(path)?.let { pm ->
                if (pm.groupValues[1].removeSuffix("page/").trimEnd('/') == pagePath) {
                    return Template("https://${u.host}${pm.groupValues[1]}", pm.groupValues[3], 1)
                }
            }
            val q = u.query
            if (q != null && path.trimEnd('/') == pagePath) {
                Regex("""(?:^|&)(page|paged|pagina)=\d+""").find(q)?.let { qm ->
                    return Template("https://${u.host}$path?${qm.groupValues[1]}=", "", 1)
                }
            }
        }
        return null
    }

    /** De pagina-URL's die doorlopen worden: de opgegeven pagina plus de volgende, tot [pages] in totaal. */
    fun pageUrls(firstUrl: String, html: String, pages: Int): List<String> {
        val n = pages.coerceAtLeast(1)
        fromUrl(firstUrl)?.let { t -> return (0 until n).map { t.url(t.start + it) } }
        val t = fromHtml(html, firstUrl) ?: return listOf(firstUrl)
        return listOf(firstUrl) + (2..n).map { t.url(it) }
    }
}

/**
 * Haalt recepten in bulk binnen vanuit overzichtspagina's (bv. een "alle recepten"-lijst of een weekmenu):
 *  1. de overzichtspagina('s) ophalen, volgende pagina's automatisch herkennen
 *  2. alle interne links verzamelen en groeperen per pad ("/recept/…", "/posts/…")
 *  3. elke link ophalen en enkel bewaren wat een echt recept is (schema.org/Recipe met ingrediënten)
 * Per groep worden eerst 3 verspreide proefmonsters opgehaald; bevat geen enkel monster een recept (menu, categorieën,
 * contact…), dan wordt de rest van de groep overgeslagen. Groepen waarvan 5 aanvragen mislukken worden ook opgegeven.
 * Vriendelijk voor de websites: maximaal [concurrency] gelijktijdige aanvragen met [politenessDelayMs] pauze.
 */
class BulkImporter(
    private val fetch: suspend (String) -> String,
    private val politenessDelayMs: Long = 300,
    private val concurrency: Int = 3,
) {
    companion object { const val DEFAULT_MAX_CHECKS = 200 }

    data class Found(val url: String, val draft: RecipeImporter.Draft)

    data class Progress(
        val page: Int,
        val pages: Int,
        val checked: Int,
        val found: Int,
        val lastError: String? = null,
    )

    private fun normTitle(t: String) = t.lowercase().filter { it.isLetterOrDigit() }

    suspend fun run(
        listingUrls: List<String>,
        pagesPerListing: Int,
        knownUrls: Set<String>,
        knownTitles: Set<String>,
        maxChecks: Int = DEFAULT_MAX_CHECKS,
        onProgress: (Progress) -> Unit,
        onFound: (Found) -> Unit,
    ) {
        val checked = AtomicInteger(0)
        val found = AtomicInteger(0)
        val seen = ConcurrentHashMap.newKeySet<String>().also { set -> knownUrls.forEach { set += LinkHarvester.key(it) } }
        val titles = ConcurrentHashMap.newKeySet<String>().also { set -> knownTitles.forEach { set += normTitle(it) } }
        val groupTried = ConcurrentHashMap<String, AtomicInteger>()
        val groupOk = ConcurrentHashMap<String, AtomicInteger>()
        val groupErrors = ConcurrentHashMap<String, AtomicInteger>()
        val sem = Semaphore(concurrency.coerceAtLeast(1))
        val lastError = java.util.concurrent.atomic.AtomicReference<String?>(null)
        var pageNo = 0
        var totalPages = listingUrls.size * pagesPerListing.coerceAtLeast(1)

        fun progress() = onProgress(Progress(pageNo, totalPages, checked.get(), found.get(), lastError.get()))

        /** Haalt één pagina op en telt het resultaat in de groepsstatistieken. */
        suspend fun check(u: String, ok: AtomicInteger, junk: AtomicInteger, errors: AtomicInteger) {
            delay(politenessDelayMs)
            checked.incrementAndGet()
            val draft = try {
                val html = fetch(u)
                withContext(Dispatchers.Default) { RecipeImporter.parse(html) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError.set(e.message)
                errors.incrementAndGet()
                progress()
                return
            }
            if (draft.foundRecipeData && draft.ingredientLines.isNotEmpty() && draft.title.isNotBlank()) {
                ok.incrementAndGet()
                if (titles.add(normTitle(draft.title))) {
                    found.incrementAndGet()
                    onFound(Found(u, draft))
                }
            } else {
                junk.incrementAndGet()
            }
            progress()
        }

        suspend fun processGroup(parent: String, urls: List<String>) {
            val junk = groupTried.getOrPut(parent) { AtomicInteger() }     // pagina's die laadden maar geen recept waren
            val errors = groupErrors.getOrPut(parent) { AtomicInteger() }  // aanvragen die mislukten
            val ok = groupOk.getOrPut(parent) { AtomicInteger() }
            fun giveUp() = ok.get() == 0 && (junk.get() >= 3 || errors.get() >= 5)
            if (giveUp()) return
            var rest = urls
            if (ok.get() == 0) {
                // Eerst 3 verspreide proefmonsters: menulinks staan vaak vooraan in dezelfde pad-groep als de recepten.
                val k = minOf(3, urls.size)
                val probeIdx = (0 until k).map { if (k == 1) 0 else it * (urls.size - 1) / (k - 1) }.toSet()
                for (i in probeIdx.sorted()) {
                    coroutineContext.ensureActive()
                    if (checked.get() >= maxChecks) return
                    check(urls[i], ok, junk, errors)
                }
                if (ok.get() == 0 && errors.get() == 0) { junk.set(maxOf(junk.get(), 3)); return }   // duidelijk geen receptgroep
                rest = urls.filterIndexed { i, _ -> i !in probeIdx }
            }
            for (u in rest) {
                coroutineContext.ensureActive()
                if (checked.get() >= maxChecks) return
                if (giveUp()) return
                check(u, ok, junk, errors)
            }
        }

        for (listing in listingUrls) {
            coroutineContext.ensureActive()
            if (checked.get() >= maxChecks) break
            val firstHtml = try {
                fetch(listing)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError.set("${listing}: ${e.message}"); progress(); continue
            }
            val pages = Pagination.pageUrls(listing, firstHtml, pagesPerListing)
            totalPages += pages.size - pagesPerListing.coerceAtLeast(1)
            for ((i, pageUrl) in pages.withIndex()) {
                coroutineContext.ensureActive()
                if (checked.get() >= maxChecks) break
                pageNo++
                val html = if (i == 0) firstHtml else try {
                    delay(politenessDelayMs); fetch(pageUrl)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastError.set("${pageUrl}: ${e.message}"); progress(); break   // waarschijnlijk voorbij de laatste pagina
                }
                progress()
                val fresh = LinkHarvester.candidates(html, pageUrl).filter { LinkHarvester.key(it) !in seen }
                var groups = fresh.groupBy { LinkHarvester.parentOf(it) }
                // Een lijst met recepten heeft meerdere recepten onder hetzelfde pad; losse links (menu, contact) slaan we dan over.
                if (groups.any { it.value.size >= 3 }) groups = groups.filter { it.value.size >= 3 }
                groups.values.forEach { g -> g.forEach { seen += LinkHarvester.key(it) } }
                coroutineScope {
                    groups.map { (parent, urls) -> async { sem.withPermit { processGroup(parent, urls) } } }.awaitAll()
                }
            }
        }
        progress()
    }
}
