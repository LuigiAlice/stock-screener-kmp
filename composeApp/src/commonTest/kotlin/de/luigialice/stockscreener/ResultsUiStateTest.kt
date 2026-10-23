package de.luigialice.stockscreener

import de.luigialice.stockscreener.data.InMemoryBarStore
import de.luigialice.stockscreener.model.ScanMode
import de.luigialice.stockscreener.ui.results.ResultsUiState
import de.luigialice.stockscreener.ui.results.SCORES_KEY
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ResultsUiStateTest {

    @Test
    fun remembersFilterPositionPerModeAcrossInstances() {
        val store = InMemoryBarStore()
        val first = ResultsUiState(store)
        first.rememberScore(ScanMode.REBOUND_START, 6)
        first.rememberScore(ScanMode.ONGOING_RECOVERY, 3)

        val second = ResultsUiState(store)
        assertEquals(6, second.scoresByMode[ScanMode.REBOUND_START])
        assertEquals(3, second.scoresByMode[ScanMode.ONGOING_RECOVERY])
    }

    @Test
    fun clampsScoresToModeRange() {
        val store = InMemoryBarStore()
        val state = ResultsUiState(store)
        state.rememberScore(ScanMode.ONGOING_RECOVERY, 99) // maxScore 5
        assertEquals(5, state.scoresByMode[ScanMode.ONGOING_RECOVERY])

        store.save(SCORES_KEY, """{"positions":{"REBOUND_START":42}}""")
        val reloaded = ResultsUiState(store)
        assertEquals(8, reloaded.scoresByMode[ScanMode.REBOUND_START]) // maxScore 8
    }

    @Test
    fun unknownModesAndCorruptDataAreIgnored() {
        val store = InMemoryBarStore()
        store.save(SCORES_KEY, """{"positions":{"GARBAGE":3}}""")
        assertTrue(ResultsUiState(store).scoresByMode.isEmpty())

        store.save(SCORES_KEY, "kein-json")
        assertTrue(ResultsUiState(store).scoresByMode.isEmpty())
    }

    @Test
    fun worksWithoutStore() {
        val state = ResultsUiState(null)
        state.rememberScore(ScanMode.REBOUND_START, 4)
        assertEquals(4, state.scoresByMode[ScanMode.REBOUND_START])
        assertNull(ResultsUiState(null).scoresByMode[ScanMode.REBOUND_START]) // keine Persistenz, aber stabil
    }

    @Test
    fun allCriteriaStartAsKann() {
        val state = ResultsUiState(InMemoryBarStore())
        assertTrue(state.mussByMode.isEmpty())
    }

    @Test
    fun toggleMussMarksAndUnmarksACriterion() {
        val state = ResultsUiState(InMemoryBarStore())
        state.toggleMuss(ScanMode.REBOUND_START, "RSI im Band")
        assertEquals(setOf("RSI im Band"), state.mussByMode[ScanMode.REBOUND_START])

        state.toggleMuss(ScanMode.REBOUND_START, "Volumen ok")
        assertEquals(setOf("RSI im Band", "Volumen ok"), state.mussByMode[ScanMode.REBOUND_START])

        state.toggleMuss(ScanMode.REBOUND_START, "RSI im Band") // wieder abwählen
        assertEquals(setOf("Volumen ok"), state.mussByMode[ScanMode.REBOUND_START])
    }

    @Test
    fun mussMarksAreRememberedPerModeAcrossInstances() {
        val store = InMemoryBarStore()
        ResultsUiState(store).toggleMuss(ScanMode.REBOUND_START, "Perf-Kriterium")
        val reloaded = ResultsUiState(store)
        assertEquals(setOf("Perf-Kriterium"), reloaded.mussByMode[ScanMode.REBOUND_START])
    }

    @Test
    fun sortTogglesDescendingAscendingAndBackToScore() {
        val state = ResultsUiState(null)
        assertNull(state.sortKey)

        state.toggleSort("Kurs")
        assertEquals("Kurs", state.sortKey)
        assertEquals(true, state.sortDescending)

        state.toggleSort("Kurs")
        assertEquals("Kurs", state.sortKey)
        assertEquals(false, state.sortDescending)

        state.toggleSort("Kurs")
        assertNull(state.sortKey, "dritter Klick setzt auf die Score-Reihenfolge zurück")

        state.toggleSort("Score")
        assertEquals("Score", state.sortKey)
        assertEquals(true, state.sortDescending)
    }

    @Test
    fun sortingAnotherColumnStartsDescending() {
        val state = ResultsUiState(null)
        state.toggleSort("Kurs")
        state.toggleSort("Kurs") // aufsteigend
        state.toggleSort("RSI")
        assertEquals("RSI", state.sortKey)
        assertEquals(true, state.sortDescending)
    }
}
