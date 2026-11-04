package be.rimeco.weekmenu.logic

object Text {
    private val named = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
        "eacute" to "é", "egrave" to "è", "ecirc" to "ê", "euml" to "ë", "agrave" to "à", "acirc" to "â",
        "iuml" to "ï", "icirc" to "î", "ocirc" to "ô", "ouml" to "ö", "uuml" to "ü", "ucirc" to "û",
        "ccedil" to "ç", "frac12" to "½", "frac14" to "¼", "frac34" to "¾", "deg" to "°", "ndash" to "–",
        "rsquo" to "’", "lsquo" to "‘", "hellip" to "…",
    )
    private val entity = Regex("&(#x[0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);")

    fun unescapeHtml(s: String): String {
        if (!s.contains('&')) return s
        return entity.replace(s) { m ->
            val g = m.groupValues[1]
            when {
                g.startsWith("#x") || g.startsWith("#X") -> g.substring(2).toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: m.value
                g.startsWith("#") -> g.substring(1).toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value
                else -> named[g] ?: named[g.lowercase()] ?: m.value
            }
        }
    }

    fun stripTags(s: String): String = s.replace(Regex("<[^>]+>"), " ")

    fun collapse(s: String): String = s.replace(Regex("[\\s\\u00A0]+"), " ").trim()

    fun clean(s: String): String = collapse(unescapeHtml(stripTags(s)))

    fun capitalize(s: String): String = s.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}
