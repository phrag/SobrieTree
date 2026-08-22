package com.sobrietree.android.engine

import com.sobrietree.android.BeerEntry
import kotlin.math.abs

/**
 * Finds logged drinks that are almost certainly carrying a placeholder
 * strength, and works out what they should be.
 *
 * Several paths stamp the default ABV rather than a real one: CSV import fills
 * blanks with it, "set total for day" invents an Adjustment entry at it, and an
 * old export wrote no strength at all so everything re-imported got it. The
 * unit maths is then exactly right on a wrong input - a 5.2% beer recorded at
 * 5.0% reports 2.5 units instead of 2.6, and the same error runs through
 * calories and the alcohol-free-day test.
 *
 * Suspicion is deliberately narrow: an entry qualifies only if its strength is
 * *exactly* the default. A drink that genuinely is the default strength will be
 * caught too, which is why nothing here changes anything on its own - it
 * proposes, and the user confirms per name.
 */
object AbvRepair {

    /** A saved drink, as a source of the real strength. */
    data class Drink(val name: String, val abv: Double)

    data class Group(
        /** The entry name shared by this group, as logged. */
        val name: String,
        val count: Int,
        val currentAbv: Double,
        /** Strength of the saved drink this name appears to be, if one matches. */
        val suggestedAbv: Double?,
        /** Which saved drink the suggestion came from, for showing the user. */
        val suggestedFrom: String?
    )

    /** Strengths within this of each other are treated as the same number. */
    private const val EPSILON = 0.001

    fun groups(
        entries: List<BeerEntry>,
        savedDrinks: List<Drink>,
        defaultAbv: Double
    ): List<Group> {
        if (defaultAbv <= 0) return emptyList()

        val suspect = entries.filter { abs(it.alcoholPercentage - defaultAbv) < EPSILON }
        if (suspect.isEmpty()) return emptyList()

        return suspect
            .groupBy { it.name.trim() }
            .map { (name, group) ->
                val match = suggestFor(name, savedDrinks, defaultAbv)
                Group(
                    name = name,
                    count = group.size,
                    currentAbv = defaultAbv,
                    suggestedAbv = match?.abv,
                    suggestedFrom = match?.name
                )
            }
            // Most-affected first: that is where confirming pays off most.
            .sortedWith(compareByDescending<Group> { it.count }.thenBy { it.name.lowercase() })
    }

    /**
     * The saved drink an entry name appears to be.
     *
     * Exact name wins. Otherwise the longest saved-drink name contained in the
     * entry name, which is what rescues imported names like
     * "Augustiner Bottle (1.30)" - they carry the real name plus noise.
     * A suggestion is only offered when it would actually change something.
     */
    private fun suggestFor(entryName: String, savedDrinks: List<Drink>, defaultAbv: Double): Drink? {
        val usable = savedDrinks.filter { it.abv > 0 && abs(it.abv - defaultAbv) >= EPSILON }
        if (usable.isEmpty()) return null
        val lower = entryName.lowercase()

        usable.firstOrNull { it.name.trim().equals(entryName, ignoreCase = true) }?.let { return it }

        return usable
            .filter { it.name.isNotBlank() && lower.contains(it.name.trim().lowercase()) }
            .maxByOrNull { it.name.trim().length }
    }

    /** Ids of the entries a confirmed group applies to. */
    fun entryIdsFor(entries: List<BeerEntry>, group: Group, defaultAbv: Double): List<String> =
        entries.filter {
            it.name.trim() == group.name && abs(it.alcoholPercentage - defaultAbv) < EPSILON
        }.map { it.id }
}
