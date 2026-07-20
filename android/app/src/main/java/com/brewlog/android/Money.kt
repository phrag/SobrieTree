package com.brewlog.android

import java.util.Currency
import java.util.Locale

/**
 * Formats money using a chosen currency symbol (e.g. €8, $8), falling back to
 * the device locale's currency, then to a bare number. Costs are entered by
 * the user in their own currency, so we only decorate.
 *
 * The chosen currency is process-wide: [applyFrom] is called at app start and
 * whenever the setting changes.
 */
object Money {

    private val localeSymbol: String = try {
        Currency.getInstance(Locale.getDefault()).symbol
    } catch (_: Exception) {
        ""
    }

    /** Symbol chosen in Settings; null means follow the device locale. */
    @Volatile
    private var overrideSymbol: String? = null

    private val symbol: String
        get() = overrideSymbol ?: localeSymbol

    /** ISO 4217 code of a currency, or null for the locale default. */
    fun setCurrencyCode(code: String?) {
        overrideSymbol = code?.let {
            try { Currency.getInstance(it).symbol } catch (_: Exception) { it }
        }
    }

    fun applyFrom(prefs: AppPrefs) = setCurrencyCode(prefs.currencyCode)

    fun format(amount: Double, decimals: Int = 0): String {
        val number = String.format(Locale.getDefault(), "%,.${decimals}f", amount)
        return if (symbol.isNotEmpty()) "$symbol$number" else number
    }
}
