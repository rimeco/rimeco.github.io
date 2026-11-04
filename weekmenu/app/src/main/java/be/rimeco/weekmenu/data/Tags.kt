package be.rimeco.weekmenu.data

object Tags {
    const val VEGETARIAN = "Vegetarisch"
    const val MEAT = "Vlees"
    const val FISH = "Vis"
    const val PASTA = "Pasta"
    const val SOUP = "Soep"
    const val QUICK = "Snel"

    val defaults = listOf(VEGETARIAN, MEAT, FISH, PASTA, SOUP, QUICK, "Ovenschotel", "Wraps")

    /** Tags waarvan we niet willen dat ze op twee opeenvolgende dagen terugkomen. */
    val varietyTags = setOf(MEAT, FISH, PASTA, SOUP)

    private val meatWords = listOf(
        "kip", "rund", "varken", "gehakt", "spek", "ham", "worst", "lam", "vlees", "chorizo", "salami",
        "kalkoen", "eend", "bacon", "biefstuk", "entrecote", "schnitzel", "kalf", "gans", "konijn",
        "merguez", "pancetta", "prosciutto", "speklap", "ribbetje", "ribs", "hamburger", "burger",
        "chicken", "beef", "pork", "lamb", "sausage",
    )
    private val fishWords = listOf(
        "zalm", "tonijn", "kabeljauw", "garnaal", "garnalen", "scampi", "haring", "makreel", "forel",
        "pangasius", "mossel", "mosselen", "schelvis", "koolvis", "zeebaars", "ansjovis", "kreeft",
        "inktvis", "vis", "witvis", "tilapia", "sardine", "sardines", "salmon", "tuna", "shrimp", "cod",
    )
    private val pastaWords = listOf("pasta", "spaghetti", "penne", "tagliatelle", "lasagne", "macaroni", "fusilli", "rigatoni", "farfalle", "tortellini", "gnocchi")

    private fun tokens(text: String): List<String> =
        text.lowercase().split(Regex("[^a-zà-ÿ]+")).filter { it.isNotBlank() }

    private fun hasWord(tokens: List<String>, words: List<String>): Boolean =
        tokens.any { t -> !t.contains("bouillon") && words.any { w -> t == w || t.startsWith(w) } }

    /** Raadt tags uit titel, categorie-tekst en ingrediënten. De gebruiker kan ze nadien aanpassen. */
    fun suggest(title: String, ingredientNames: List<String>, categoryText: String = "", minutes: Int = 0): List<String> {
        val result = linkedSetOf<String>()
        val ing = ingredientNames.flatMap { tokens(it) }
        val meat = hasWord(ing, meatWords)
        val fish = hasWord(ing, fishWords)
        if (meat) result += MEAT
        if (fish) result += FISH
        if (!meat && !fish && ingredientNames.isNotEmpty()) result += VEGETARIAN
        val head = tokens("$title $categoryText")
        if (hasWord(head, pastaWords)) result += PASTA
        if (head.any { it.startsWith("soep") }) result += SOUP
        if (minutes in 1..30) result += QUICK
        return result.toList()
    }
}
