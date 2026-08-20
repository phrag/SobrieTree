package com.treetotal.android.engine

import com.treetotal.android.BeerEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AbvRepairTest {

    private var nextId = 0

    private fun entry(name: String, abv: Double) =
        BeerEntry("${nextId++}", name, abv, 500.0, "2026-08-20", "")

    private val augustiner = AbvRepair.Drink("Augustiner", 5.2)
    private val wine = AbvRepair.Drink("Merlot", 13.0)

    @Test
    fun `entries sitting exactly on the default are flagged`() {
        val entries = listOf(entry("Augustiner", 5.0), entry("Augustiner", 5.0))
        val groups = AbvRepair.groups(entries, listOf(augustiner), defaultAbv = 5.0)
        assertEquals(1, groups.size)
        assertEquals("Augustiner", groups[0].name)
        assertEquals(2, groups[0].count)
        assertEquals(5.2, groups[0].suggestedAbv!!, 0.0001)
    }

    @Test
    fun `entries with a real strength are left alone`() {
        // 5.2 and 4.8 are both deliberate, so neither is a placeholder.
        val entries = listOf(entry("Augustiner", 5.2), entry("Helles", 4.8))
        assertTrue(AbvRepair.groups(entries, listOf(augustiner), defaultAbv = 5.0).isEmpty())
    }

    @Test
    fun `an imported name with noise still finds its drink`() {
        // The shape the old export produced: real name plus a price suffix.
        val entries = listOf(entry("Augustiner Bottle (1.30)", 5.0))
        val groups = AbvRepair.groups(entries, listOf(augustiner), defaultAbv = 5.0)
        assertEquals(5.2, groups[0].suggestedAbv!!, 0.0001)
        assertEquals("Augustiner", groups[0].suggestedFrom)
    }

    @Test
    fun `the most specific saved drink wins a contained match`() {
        val drinks = listOf(AbvRepair.Drink("Augustiner", 5.2), AbvRepair.Drink("Augustiner Edelstoff", 5.6))
        val groups = AbvRepair.groups(listOf(entry("Augustiner Edelstoff 500ml", 5.0)), drinks, 5.0)
        assertEquals(5.6, groups[0].suggestedAbv!!, 0.0001)
        assertEquals("Augustiner Edelstoff", groups[0].suggestedFrom)
    }

    @Test
    fun `an unrecognised name is still reported, just without a suggestion`() {
        // The user can still set it by hand; hiding it would hide the problem.
        val groups = AbvRepair.groups(listOf(entry("Adjustment", 5.0)), listOf(augustiner), 5.0)
        assertEquals(1, groups.size)
        assertEquals("Adjustment", groups[0].name)
        assertNull(groups[0].suggestedAbv)
        assertNull(groups[0].suggestedFrom)
    }

    @Test
    fun `a saved drink that is itself the default suggests nothing`() {
        // Proposing 5.0 -> 5.0 would be a change that changes nothing.
        val drinks = listOf(AbvRepair.Drink("Augustiner", 5.0))
        val groups = AbvRepair.groups(listOf(entry("Augustiner", 5.0)), drinks, 5.0)
        assertEquals(1, groups.size)
        assertNull(groups[0].suggestedAbv)
    }

    @Test
    fun `groups are ordered by how many entries they would fix`() {
        val entries = listOf(
            entry("Merlot", 5.0),
            entry("Augustiner", 5.0), entry("Augustiner", 5.0), entry("Augustiner", 5.0)
        )
        val groups = AbvRepair.groups(entries, listOf(augustiner, wine), 5.0)
        assertEquals(listOf("Augustiner", "Merlot"), groups.map { it.name })
        assertEquals(3, groups[0].count)
    }

    @Test
    fun `names differing only by surrounding space are one group`() {
        val entries = listOf(entry("Augustiner", 5.0), entry("  Augustiner  ", 5.0))
        val groups = AbvRepair.groups(entries, listOf(augustiner), 5.0)
        assertEquals(1, groups.size)
        assertEquals(2, groups[0].count)
    }

    @Test
    fun `no default strength means nothing is suspect`() {
        val entries = listOf(entry("Augustiner", 0.0))
        assertTrue(AbvRepair.groups(entries, listOf(augustiner), defaultAbv = 0.0).isEmpty())
    }

    @Test
    fun `only the matching entries are handed over for updating`() {
        val keep = entry("Augustiner", 5.2)          // deliberate strength
        val fixA = entry("Augustiner", 5.0)
        val fixB = entry("Augustiner", 5.0)
        val other = entry("Merlot", 5.0)
        val entries = listOf(keep, fixA, fixB, other)

        val group = AbvRepair.groups(entries, listOf(augustiner), 5.0).first { it.name == "Augustiner" }
        val ids = AbvRepair.entryIdsFor(entries, group, 5.0)

        assertEquals(setOf(fixA.id, fixB.id), ids.toSet())
        assertTrue(keep.id !in ids)
        assertTrue(other.id !in ids)
    }

    @Test
    fun `case differences still match a saved drink`() {
        val groups = AbvRepair.groups(listOf(entry("AUGUSTINER", 5.0)), listOf(augustiner), 5.0)
        assertEquals(5.2, groups[0].suggestedAbv!!, 0.0001)
    }
}
