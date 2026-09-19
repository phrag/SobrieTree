package com.sobrietree.android.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EducationLibraryTest {

    @Test
    fun `every card carries an attribution`() {
        // The Journey tab presents these as evidence-based reads; a card with no
        // source is a health claim with nothing standing behind it.
        for (card in EducationLibrary.cards) {
            assertTrue("${card.id} has no source", card.source.isNotBlank())
        }
    }

    @Test
    fun `every card has the text the list row and detail view need`() {
        // The list row renders title + summary and the detail view renders body;
        // a blank one would show as an empty row nobody can tell apart.
        for (card in EducationLibrary.cards) {
            assertTrue("${card.id} has no title", card.title.isNotBlank())
            assertTrue("${card.id} has no summary", card.summary.isNotBlank())
            assertTrue("${card.id} has no body", card.body.isNotBlank())
        }
    }

    @Test
    fun `card ids are unique`() {
        // The id is a card's stable identity across releases, so two cards
        // sharing one would make the reads indistinguishable to anything keying on it.
        val ids = EducationLibrary.cards.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `the general guidance card leads the reading list`() {
        // Cards are shown in declaration order, and the guidance card is the one
        // that frames every other read, so it has to come first.
        assertTrue(EducationLibrary.cards.isNotEmpty())
        assertEquals("guidance", EducationLibrary.cards.first().id)
    }
}
