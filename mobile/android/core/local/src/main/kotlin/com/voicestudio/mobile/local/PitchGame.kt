package com.voicestudio.mobile.local

import kotlin.math.PI
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

data class PitchRound(val first: Double, val second: Double) {
    val higher get() = second > first
}

/**
 * Ear-training game shared with iOS (`PitchGame.swift`): two tones, is the second
 * higher or lower? Needs no model, network or microphone. `random` yields [0, 1).
 */
class PitchGame(private val random: () -> Double = { kotlin.random.Random.nextDouble() }) {
    var score = 0; private set
    var lives = LIVES; private set
    var round = next(); private set
    val over get() = lives == 0
    val cents get() = cents(score)

    /** A miss costs a life and keeps the gap; every answer draws fresh tones. */
    fun answer(higher: Boolean): Boolean {
        check(!over)
        val correct = higher == round.higher
        if(correct) score++ else lives--
        if(!over) round = next()
        return correct
    }

    private fun next(): PitchRound {
        val first = LOWEST * 2.0.pow(random().coerceIn(0.0, 1.0))
        val up = random() >= 0.5
        return PitchRound(first, first * 2.0.pow((if(up) 1 else -1) * cents(score) / 1200))
    }

    companion object {
        const val LIVES = 3
        const val SAMPLE_RATE = 44100
        private const val LOWEST = 220.0
        private const val TONE_SECONDS = 0.45
        private const val PAUSE_SECONDS = 0.2
        private const val FADE_SECONDS = 0.01

        /** Starts at a major third and narrows 15% per right answer, never below 10 cents. */
        fun cents(score: Int): Double = max(10.0, 400 * 0.85.pow(score))

        /** Both tones as mono PCM16 with a short silence between; faded to avoid clicks. */
        fun samples(round: PitchRound, sampleRate: Int = SAMPLE_RATE): ShortArray {
            val tone = (TONE_SECONDS * sampleRate).roundToInt()
            val pause = (PAUSE_SECONDS * sampleRate).roundToInt()
            val fade = (FADE_SECONDS * sampleRate).roundToInt()
            val output = ShortArray(tone * 2 + pause)
            for((start, frequency) in listOf(0 to round.first, tone + pause to round.second)) {
                for(index in 0 until tone) {
                    val envelope = minOf(1.0, index.toDouble() / fade, (tone - 1 - index).toDouble() / fade)
                    output[start + index] = (sin(2 * PI * frequency * index / sampleRate) * envelope * 0.5 * Short.MAX_VALUE).roundToInt().toShort()
                }
            }
            return output
        }
    }
}
