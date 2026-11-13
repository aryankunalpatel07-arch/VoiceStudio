package com.voicestudio.mobile.local

import kotlin.math.abs
import org.junit.Assert.*
import org.junit.Test

class PitchGameTest {
    private fun scripted(vararg draws: Double): PitchGame {
        val values = draws.iterator()
        return PitchGame { values.next() }
    }

    // The same vectors run in iOS PitchGameTests.swift: both apps must draw the same rounds.
    @Test fun roundsNarrowAfterRightAnswersAndKeepTheGapAfterMisses() {
        val game = scripted(0.0, 0.9, 0.5, 0.1, 0.0, 0.9, 0.0, 0.9, 0.0, 0.9)
        assertEquals(220.0, game.round.first, 1e-9)
        assertEquals(277.18263097687205, game.round.second, 1e-6)
        assertTrue(game.round.higher)
        assertTrue(game.answer(true))
        assertEquals(1, game.score);assertEquals(3, game.lives);assertEquals(340.0, game.cents, 1e-9)
        assertEquals(311.1269837220809, game.round.first, 1e-6)
        assertFalse(game.round.higher)
        assertEquals(-340.0, 1200 * Math.log(game.round.second / game.round.first) / Math.log(2.0), 1e-6)
        assertFalse(game.answer(true))
        assertEquals(1, game.score);assertEquals(2, game.lives);assertEquals(340.0, game.cents, 1e-9)
        assertFalse(game.answer(false));assertFalse(game.over)
        assertFalse(game.answer(false));assertTrue(game.over)
        assertEquals(1, game.score)
        assertThrows(IllegalStateException::class.java) { game.answer(true) }
    }

    @Test fun gapNeverDropsBelowTenCents() {
        assertEquals(400.0, PitchGame.cents(0), 1e-9)
        assertEquals(10.0, PitchGame.cents(200), 1e-9)
        assertTrue((0 until 200).all { PitchGame.cents(it + 1) <= PitchGame.cents(it) })
    }

    @Test fun samplesHoldTwoFadedTonesAroundSilence() {
        val samples = PitchGame.samples(PitchRound(220.0, 440.0), 8000)
        assertEquals(3600 * 2 + 1600, samples.size)
        assertEquals(0, samples[0].toInt());assertEquals(0, samples[3599].toInt())
        assertTrue(samples.slice(3600 until 5200).all { it.toInt() == 0 })
        fun crossings(range: IntRange) = range.zipWithNext().count { (a, b) -> samples[a] < 0 && samples[b] >= 0 }
        assertTrue(abs(crossings(0 until 3600) - 99) <= 1)
        assertTrue(abs(crossings(5200 until 8800) - 198) <= 1)
        assertTrue(samples.maxOf { abs(it.toInt()) } in 16000..16384)
    }
}
