package be.rimeco.weekmenu.logic

import kotlin.math.abs
import kotlin.math.roundToLong

/** Eenheden, omrekening en mooie weergave van hoeveelheden. */
object Units {
    data class Dim(val key: String, val factor: Double, val baseUnit: String)

    fun dimOf(unit: String): Dim = when (unit) {
        "g" -> Dim("mass", 1.0, "g")
        "kg" -> Dim("mass", 1000.0, "g")
        "ml" -> Dim("vol", 1.0, "ml")
        "cl" -> Dim("vol", 10.0, "ml")
        "dl" -> Dim("vol", 100.0, "ml")
        "l" -> Dim("vol", 1000.0, "ml")
        else -> Dim("u:$unit", 1.0, unit)
    }

    /** Zet om naar de basiseenheid (g of ml; andere eenheden blijven gelijk). */
    fun toBase(quantity: Double, unit: String): Pair<Double, String> {
        val d = dimOf(unit)
        return quantity * d.factor to d.baseUnit
    }

    /** Kiest de handigste eenheid: 1500 g -> 1,5 kg, 1000 ml -> 1 l. */
    fun pretty(baseQuantity: Double, baseUnit: String): Pair<Double, String> = when {
        baseUnit == "g" && baseQuantity >= 1000 -> baseQuantity / 1000 to "kg"
        baseUnit == "ml" && baseQuantity >= 1000 -> baseQuantity / 1000 to "l"
        else -> baseQuantity to baseUnit
    }

    /** Voorkomt kommagetallen zoals 0,30000000004. */
    fun clean(q: Double): Double = Math.round(q * 1000) / 1000.0

    /** Eenheden die je per stuk koopt: hoeveelheden worden op de boodschappenlijst naar boven afgerond. */
    private val countUnits = setOf("", "blik", "pak", "zakje", "bos", "pot", "stuk", "takje", "plak", "teen", "bakje")
    private val metricUnits = setOf("g", "kg", "ml", "cl", "dl", "l")

    private fun glyph(frac: Double): String? = when {
        abs(frac - 0.25) < 0.04 -> "¼"
        abs(frac - 1.0 / 3) < 0.04 -> "⅓"
        abs(frac - 0.5) < 0.04 -> "½"
        abs(frac - 2.0 / 3) < 0.04 -> "⅔"
        abs(frac - 0.75) < 0.04 -> "¾"
        else -> null
    }

    private fun decimal(q: Double): String {
        val r = when {
            q >= 100 -> q.roundToLong().toDouble()
            q >= 10 -> (q * 10).roundToLong() / 10.0
            else -> (q * 100).roundToLong() / 100.0
        }
        return if (abs(r - r.roundToLong()) < 0.001) r.roundToLong().toString()
        else r.toString().trimEnd('0').trimEnd('.').replace('.', ',')
    }

    /** 1,5 -> "1½", 0,75 -> "¾", 2 -> "2". */
    fun formatNumber(q: Double, fractions: Boolean = true): String {
        if (fractions) {
            val whole = kotlin.math.floor(q + 1e-9)
            val frac = q - whole
            if (frac < 0.04) return whole.roundToLong().toString()
            if (frac > 0.96) return (whole + 1).roundToLong().toString()
            glyph(frac)?.let { g -> return if (whole >= 1) "${whole.roundToLong()}$g" else g }
        }
        return decimal(q)
    }

    /** Weergave in een recept: grammen/liters met decimalen, lepels en stuks met breuken. */
    fun format(quantity: Double?, unit: String): String {
        if (quantity == null) return ""
        val n = formatNumber(quantity, fractions = unit !in metricUnits)
        return if (unit.isBlank()) n else "$n $unit"
    }

    /** Weergave op de boodschappenlijst: koopbare stuks naar boven afgerond, grammen op 5 afgerond. */
    fun formatShopping(quantity: Double?, unit: String): String {
        if (quantity == null) return ""
        val text = when {
            unit in countUnits -> kotlin.math.ceil(quantity - 0.05).toLong().coerceAtLeast(1).toString()
            unit == "g" || unit == "ml" ->
                if (quantity >= 50) ((quantity / 5).roundToLong() * 5).toString() else quantity.roundToLong().coerceAtLeast(1).toString()
            else -> formatNumber(quantity, fractions = unit !in metricUnits)
        }
        return if (unit.isBlank()) text else "$text $unit"
    }
}
