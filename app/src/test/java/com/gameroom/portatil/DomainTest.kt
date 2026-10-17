package com.gameroom.portatil

import com.gameroom.portatil.domain.EventDraft
import com.gameroom.portatil.domain.EventIdentity
import com.gameroom.portatil.domain.EventType
import com.gameroom.portatil.domain.formatNumber
import com.gameroom.portatil.domain.identityAfterEdit
import com.gameroom.portatil.domain.parseDecimal
import com.gameroom.portatil.domain.suggest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DomainTest {
    @Test
    fun editingNotExportedEventKeepsUuid() {
        val id = EventIdentity("abc", null)
        assertEquals(id, identityAfterEdit(id) { "novo" })
    }

    @Test
    fun editingExportedEventGetsNewUuidAndBecomesPending() {
        assertEquals(EventIdentity("novo", null), identityAfterEdit(EventIdentity("abc", 123L)) { "novo" })
    }

    @Test
    fun playingDropsStatsButKeepsNotes() {
        val d = EventDraft(EventType.PLAYING, LocalDate.of(2026, 1, 1), 10.0, 8.0, 5.0, "  começo  ").normalized()
        assertNull(d.playedHours); assertNull(d.score); assertNull(d.difficulty)
        assertEquals("começo", d.notes)
        val f = EventDraft(EventType.FINISHED, LocalDate.of(2026, 1, 1), 10.0, notes = "   ").normalized()
        assertEquals(10.0, f.playedHours!!, 0.0)
        assertNull(f.notes)
    }

    @Test
    fun suggestionsIgnoreAccentsAndCasePrefixFirst() {
        val options = listOf("Nintendo Switch", "PC", "Ação Arcade", "Switch Lite", "nintendo switch")
        assertEquals(listOf("Switch Lite", "Nintendo Switch"), suggest("swi", options))
        assertEquals(listOf("Ação Arcade"), suggest("acao", options))
        assertTrue(suggest("", options).isEmpty())
        assertTrue(suggest("PC", options).isEmpty())  // já digitado por inteiro
    }

    @Test
    fun decimalParsing() {
        assertEquals(12.5, parseDecimal("12,5").getOrNull()!!, 0.0)
        assertEquals(9.0, parseDecimal(" 9 ", 10.0).getOrNull()!!, 0.0)
        assertNull(parseDecimal("").getOrNull())
        assertTrue(parseDecimal("11", 10.0).isFailure)
        assertTrue(parseDecimal("-1").isFailure)
        assertTrue(parseDecimal("abc").isFailure)
        assertEquals("38", formatNumber(38.0))
        assertEquals("9,5", formatNumber(9.5))
    }
}
