package be.rimeco.weekmenu.logic

import be.rimeco.weekmenu.data.Ingredient

/** Zet een ingrediëntregel zoals "200 g gerookte zalm" om naar hoeveelheid, eenheid en naam. */
object IngredientParser {
    private val fractionChars = mapOf(
        '½' to "1/2", '¼' to "1/4", '¾' to "3/4", '⅓' to "1/3", '⅔' to "2/3", '⅛' to "1/8",
    )

    private val units: Map<String, String> = buildMap {
        fun add(canon: String, vararg alts: String) {
            put(canon, canon)
            alts.forEach { put(it, canon) }
        }
        add("g", "gr", "gram", "grams")
        add("kg", "kilo", "kilogram", "kilos")
        add("ml", "milliliter")
        add("cl", "centiliter")
        add("dl", "deciliter")
        add("l", "liter", "ltr", "liters", "litre")
        add("el", "eetlepel", "eetlepels", "eetl", "tbsp", "tablespoon", "tablespoons")
        add("tl", "theelepel", "theelepels", "theel", "tsp", "teaspoon", "teaspoons")
        add("kl", "koffielepel", "koffielepels")
        add("blik", "blikje", "blikken", "blikjes")
        add("pak", "pakje", "pakken", "pakjes", "pakket")
        add("zakje", "zakjes")
        add("bos", "bosje", "bosjes")
        add("teen", "tenen", "teentje", "teentjes")
        add("plak", "plakje", "plakken", "plakjes")
        add("snuf", "snufje")
        add("handvol", "handje", "handjes")
        add("scheut", "scheutje", "scheuten")
        add("stuk", "stuks", "stukken", "stukje", "stukjes", "st")
        add("takje", "takjes", "tak", "takken")
        add("pot", "potje", "potten")
        add("cup", "cups")
        add("oz", "ounce", "ounces")
        add("lb", "lbs", "pound", "pounds")
    }

    private val quantityRegex =
        Regex("""^(\d+/\d+|\d+(?:[.,]\d+)?(?:\s+\d+/\d+)?)(?:\s*[-–]\s*(\d+(?:[.,]\d+)?))?\s*(.*)$""")
    private val unitTokenRegex = Regex("""^([A-Za-zÀ-ÿ]+)\.?(?:\s+|$)(.*)$""")

    private val stapleWords = setOf(
        "zout", "peper", "en", "water", "zwarte", "witte", "versgemalen", "gemalen", "vers", "verse", "fijn",
        "grof", "zeezout", "kraanwater", "kokend", "lauw", "koud", "warm", "naar", "smaak", "van", "de", "of",
        "snuf", "snufje", "beetje", "wat", "een", "flinke", "mespunt",
    )

    fun parseNumber(s: String): Double? {
        val t = s.trim()
        if (t.isEmpty()) return null
        val parts = t.split(Regex("\\s+"))
        var total = 0.0
        for (p in parts) {
            total += if (p.contains('/')) {
                val (a, b) = p.split('/').let { it[0].toDoubleOrNull() to it.getOrNull(1)?.toDoubleOrNull() }
                if (a == null || b == null || b == 0.0) return null else a / b
            } else p.replace(',', '.').toDoubleOrNull() ?: return null
        }
        return total
    }

    fun parse(input: String): Ingredient {
        val raw = Text.clean(input)
        var s = raw.trimStart('-', '•', '*', '–', '·', ' ').trim()
        s = s.replace(Regex("\\((?:s|en)\\)"), "")
        for ((ch, frac) in fractionChars) {
            s = s.replace(Regex("(\\d)\\s*$ch"), "$1 $frac").replace(ch.toString(), frac)
        }
        s = s.replace(Regex("^(?:ca\\.?|circa|±|~)\\s*", RegexOption.IGNORE_CASE), "")

        var quantity: Double? = null
        var unit = ""
        var rest = s
        val qm = quantityRegex.find(s)
        if (qm != null) {
            val lower = parseNumber(qm.groupValues[1])
            val upper = qm.groupValues[2].takeIf { it.isNotEmpty() }?.let { parseNumber(it) }
            quantity = upper?.takeIf { lower == null || it >= lower } ?: lower
            rest = qm.groupValues[3]
            if (quantity != null) {
                val um = unitTokenRegex.find(rest)
                val canon = um?.let { units[it.groupValues[1].lowercase()] }
                if (um != null && canon != null) {
                    unit = canon
                    rest = um.groupValues[2]
                }
            } else {
                rest = s
            }
        }
        rest = rest.replace(Regex("^(?:van|aan)\\s+", RegexOption.IGNORE_CASE), "")
        val name = cleanName(rest).ifBlank { cleanName(s) }.ifBlank { raw.lowercase() }
        return Ingredient(name = name, quantity = quantity, unit = unit, raw = raw)
    }

    /** "Ui, fijngesnipperd (optioneel)" -> "ui" */
    fun cleanName(s: String): String {
        var n = s.replace(Regex("\\([^)]*\\)"), " ")
        n = n.split(',', ';').first()
        return Text.collapse(n).lowercase().trim('.', ' ', '-')
    }

    fun isStaple(name: String): Boolean {
        val tokens = name.lowercase().split(Regex("[^a-zà-ÿ]+")).filter { it.isNotBlank() }
        return tokens.isNotEmpty() && tokens.all { it in stapleWords } &&
            tokens.any { it == "zout" || it == "peper" || it == "water" || it == "zeezout" || it == "kraanwater" }
    }
}
