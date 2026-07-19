package com.brewlog.android

import java.util.Currency
import java.util.Locale

/**
 * Formats money using the device locale's currency symbol (e.g. €8, $8),
 * falling back to a bare number if the locale has no associated currency.
 * Costs are entered by the user in their own currency, so we only decorate.
 */
object Money {

    private val symbol: String = try {
        Currency.getInstance(Locale.getDefault()).symbol
    } catch (_: Exception) {
        ""
    }

    fun format(amount: Double, decimals: Int = 0): String {
        val number = String.format(Locale.getDefault(), "%,.${decimals}f", amount)
        return if (symbol.isNotEmpty()) "$symbol$number" else number
    }
}
