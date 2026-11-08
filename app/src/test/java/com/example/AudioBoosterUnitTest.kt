package com.example

import com.example.audio.AudioEffectManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioBoosterUnitTest {
    @Test
    fun testGainConstants() {
        assertEquals(600, AudioEffectManager.MAX_GAIN_MB)
        assertEquals(0, AudioEffectManager.MIN_GAIN_MB)
        assertEquals(30, AudioEffectManager.STEP_MB)
        // 20 steps of 5% = 100% (600 mB)
        assertEquals(600, AudioEffectManager.STEP_MB * 20)
    }

    @Test
    fun testDecibelCalculation() {
        val maxDb = AudioEffectManager.MAX_GAIN_MB / 100.0
        assertEquals(6.0, maxDb, 0.001)

        val halfDb = (AudioEffectManager.MAX_GAIN_MB / 2) / 100.0
        assertEquals(3.0, halfDb, 0.001)
    }
}
