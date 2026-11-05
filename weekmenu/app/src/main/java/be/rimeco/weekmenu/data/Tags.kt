package be.rimeco.weekmenu.data

object Tags {
    const val VEGETARIAN = "Vegetarisch"
    const val MEAT = "Vlees"
    const val FISH = "Vis"
    const val PASTA = "Pasta"
    const val SOUP = "Soep"
    const val QUICK = "Snel"
    const val DESSERT = "Dessert"
    /** Alles wat geen avondmaal is of nergens past: ontbijt, snacks, dranken, sauzen, ... */
    const val MISC = "Divers"

    val defaults = listOf(VEGETARIAN, MEAT, FISH, PASTA, SOUP, QUICK, "Ovenschotel", "Wraps", DESSERT, MISC)

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

    // Voorvoegsels (tokens die ermee beginnen) en exacte woorden die duidelijk op een dessert wijzen.
    private val dessertPrefixes = listOf(
        "dessert", "nagerecht", "brownie", "cheesecake", "tiramisu", "pudding", "mousse", "cupcake", "muffin", "koekje", "cookie",
        "gebak", "donut", "bonbon", "truffel", "pannacotta", "cake", "appeltaart", "vlaai", "chocolade", "speculoos", "marshmallow",
    )
    private val dessertExact = setOf("ijs", "ijsje", "roomijs", "waterijs", "sorbet", "taart", "taartje", "taartjes", "zoet")
    private val miscPrefixes = listOf(
        "ontbijt", "brunch", "snack", "hapje", "borrel", "drank", "cocktail", "smoothie", "dip", "dressing", "granola", "bijgerecht",
        "jam", "brood", "boterham", "toast", "tosti",
    )
    private val miscExact = setOf("saus", "sauzen", "lunch", "drankje", "sap", "ontbijtgranen", "overnight")

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
        val dessert = hasWord(head, dessertPrefixes) || head.any { it in dessertExact }
        val misc = !dessert && (hasWord(head, miscPrefixes) || head.any { it in miscExact || it.endsWith("dip") })   // "tzatzikidip"
        if (dessert) result += DESSERT
        if (misc) result += MISC
        if ((dessert || misc) && !meat && !fish) result -= VEGETARIAN    // een dessert of ontbijt telt niet als "vegetarische dag"
        if (hasWord(head, pastaWords)) result += PASTA
        if (head.any { it.startsWith("soep") }) result += SOUP
        if (minutes in 1..30) result += QUICK
        return result.toList()
    }
}
