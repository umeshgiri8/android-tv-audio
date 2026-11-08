package com.example.audio

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.os.Handler
import android.os.Looper
import android.util.Log

enum class EffectType {
    NONE,
    LOUDNESS_ENHANCER,
    EQUALIZER_FALLBACK
}

data class BoosterState(
    val isEnabled: Boolean = false,
    val gainMilliBels: Int = 0, // 0 to 600 mB (0 to +6 dB)
    val activeEffect: EffectType = EffectType.NONE,
    val statusMessage: String = "Initializing audio engine...",
    val hasError: Boolean = false
)

/**
 * Manages global audio enhancement on Audio Session 0 (Global Mix).
 * Supports primary LoudnessEnhancer and fallback Equalizer/BassBoost/Virtualizer chain.
 * Provides watchdog monitoring and auto-reattachment if audioserver crashes or restarts.
 */
class AudioEffectManager(
    private val context: Context,
    private val onStateChanged: (BoosterState) -> Unit
) {
    companion object {
        private const val TAG = "AudioEffectManager"
        const val MAX_GAIN_MB = 600 // +6 dB hard cap
        const val MIN_GAIN_MB = 0   // 0 dB
        const val STEP_MB = 30      // 5% of 600 mB = 30 mB (0.3 dB)
        private const val SESSION_ID = 0 // Global output mix
        private const val WATCHDOG_INTERVAL_MS = 3000L
    }

    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null

    private var isEnabled = false
    private var currentGainMilliBels = 0
    private var currentEffectType = EffectType.NONE
    private var lastStatusMessage = "Ready"
    private var lastHasError = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private val watchdogRunnable = object : Runnable {
        override fun run() {
            checkAndRecoverEffect()
            mainHandler.postDelayed(this, WATCHDOG_INTERVAL_MS)
        }
    }

    init {
        // Load persistent settings
        val prefs = BoosterPreferences.getPreferences(context)
        isEnabled = prefs.getBoolean(BoosterPreferences.KEY_IS_ENABLED, false)
        currentGainMilliBels = prefs.getInt(BoosterPreferences.KEY_GAIN_MB, 0)
            .coerceIn(MIN_GAIN_MB, MAX_GAIN_MB)

        initEffects()
        mainHandler.postDelayed(watchdogRunnable, WATCHDOG_INTERVAL_MS)
    }

    @Synchronized
    private fun initEffects() {
        releaseEffectsInternal()

        // 1. Try LoudnessEnhancer on session 0
        var leSuccess = false
        try {
            val le = LoudnessEnhancer(SESSION_ID)
            le.setControlStatusListener { effect, status ->
                Log.d(TAG, "LoudnessEnhancer control status changed: $status")
                if (!status) {
                    mainHandler.post { checkAndRecoverEffect() }
                }
            }
            le.setEnableStatusListener { effect, enabled ->
                Log.d(TAG, "LoudnessEnhancer enable status: $enabled")
            }

            // Test applying target gain
            val targetGain = if (isEnabled) currentGainMilliBels else 0
            le.setTargetGain(targetGain)
            le.enabled = isEnabled

            loudnessEnhancer = le
            currentEffectType = EffectType.LOUDNESS_ENHANCER
            lastStatusMessage = if (isEnabled) {
                "Active: LoudnessEnhancer on Session 0 (+${formatDb(currentGainMilliBels)} dB)"
            } else {
                "Standby: LoudnessEnhancer ready (Boost Off)"
            }
            lastHasError = false
            leSuccess = true
            Log.i(TAG, "Successfully attached LoudnessEnhancer to session $SESSION_ID")
        } catch (e: Exception) {
            Log.w(TAG, "LoudnessEnhancer on session 0 failed: ${e.message}. Attempting fallback chain...", e)
            loudnessEnhancer?.release()
            loudnessEnhancer = null
        }

        // 2. Fallback to Equalizer/BassBoost/Virtualizer if LoudnessEnhancer failed
        if (!leSuccess) {
            try {
                val eq = Equalizer(0, SESSION_ID)
                eq.setControlStatusListener { effect, status ->
                    if (!status) mainHandler.post { checkAndRecoverEffect() }
                }
                applyEqualizerGain(eq, if (isEnabled) currentGainMilliBels else 0)
                eq.enabled = isEnabled

                val bb = try {
                    BassBoost(0, SESSION_ID).apply {
                        if (strengthSupported) {
                            val strength = if (isEnabled) ((currentGainMilliBels / MAX_GAIN_MB.toFloat()) * 500).toInt().toShort() else 0.toShort()
                            setStrength(strength)
                        }
                        enabled = isEnabled
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "BassBoost fallback init optional failure: ${e.message}")
                    null
                }

                val virt = try {
                    Virtualizer(0, SESSION_ID).apply {
                        if (strengthSupported) {
                            val strength = if (isEnabled) ((currentGainMilliBels / MAX_GAIN_MB.toFloat()) * 300).toInt().toShort() else 0.toShort()
                            setStrength(strength)
                        }
                        enabled = isEnabled
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Virtualizer fallback init optional failure: ${e.message}")
                    null
                }

                equalizer = eq
                bassBoost = bb
                virtualizer = virt
                currentEffectType = EffectType.EQUALIZER_FALLBACK
                lastStatusMessage = if (isEnabled) {
                    "Active: Fallback EQ chain on Session 0 (+${formatDb(currentGainMilliBels)} dB)"
                } else {
                    "Standby: Fallback EQ chain ready (Boost Off)"
                }
                lastHasError = false
                Log.i(TAG, "Successfully attached fallback Equalizer chain to session $SESSION_ID")
            } catch (e: Exception) {
                Log.e(TAG, "All audio effects on session 0 failed: ${e.message}", e)
                currentEffectType = EffectType.NONE
                lastStatusMessage = "Error: Cannot attach audio effects to Session 0 on this firmware"
                lastHasError = true
            }
        }

        notifyState()
    }

    private fun applyEqualizerGain(eq: Equalizer, gainMb: Int) {
        val bands = eq.numberOfBands
        val range = eq.bandLevelRange // [min, max] in millibels e.g. [-1500, 1500]
        if (range != null && range.size >= 2) {
            val minLevel = range[0]
            val maxLevel = range[1]
            val targetLevel = gainMb.coerceIn(minLevel.toInt(), maxLevel.toInt()).toShort()
            for (i in 0 until bands) {
                eq.setBandLevel(i.toShort(), targetLevel)
            }
        }
    }

    @Synchronized
    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        saveSettings()
        applyCurrentGainAndState()
    }

    @Synchronized
    fun toggleEnabled() {
        setEnabled(!isEnabled)
    }

    @Synchronized
    fun setGainMilliBels(gainMb: Int) {
        val clamped = gainMb.coerceIn(MIN_GAIN_MB, MAX_GAIN_MB)
        if (currentGainMilliBels != clamped) {
            currentGainMilliBels = clamped
            saveSettings()
            applyCurrentGainAndState()
        }
    }

    @Synchronized
    fun stepGain(deltaSteps: Int) {
        val deltaMb = deltaSteps * STEP_MB
        setGainMilliBels(currentGainMilliBels + deltaMb)
    }

    @Synchronized
    private fun applyCurrentGainAndState() {
        when (currentEffectType) {
            EffectType.LOUDNESS_ENHANCER -> {
                val le = loudnessEnhancer
                if (le != null) {
                    try {
                        val targetGain = if (isEnabled) currentGainMilliBels else 0
                        le.setTargetGain(targetGain)
                        le.enabled = isEnabled
                        lastStatusMessage = if (isEnabled) {
                            "Active: LoudnessEnhancer (+${formatDb(currentGainMilliBels)} dB)"
                        } else {
                            "Standby: LoudnessEnhancer ready (Boost Off)"
                        }
                        lastHasError = false
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed applying gain to LoudnessEnhancer: ${e.message}", e)
                        initEffects()
                        return
                    }
                } else {
                    initEffects()
                    return
                }
            }
            EffectType.EQUALIZER_FALLBACK -> {
                val eq = equalizer
                if (eq != null) {
                    try {
                        val targetGain = if (isEnabled) currentGainMilliBels else 0
                        applyEqualizerGain(eq, targetGain)
                        eq.enabled = isEnabled

                        bassBoost?.let { bb ->
                            if (bb.strengthSupported) {
                                val strength = if (isEnabled) ((currentGainMilliBels / MAX_GAIN_MB.toFloat()) * 500).toInt().toShort() else 0.toShort()
                                bb.setStrength(strength)
                            }
                            bb.enabled = isEnabled
                        }

                        virtualizer?.let { v ->
                            if (v.strengthSupported) {
                                val strength = if (isEnabled) ((currentGainMilliBels / MAX_GAIN_MB.toFloat()) * 300).toInt().toShort() else 0.toShort()
                                v.setStrength(strength)
                            }
                            v.enabled = isEnabled
                        }

                        lastStatusMessage = if (isEnabled) {
                            "Active: Fallback EQ (+${formatDb(currentGainMilliBels)} dB)"
                        } else {
                            "Standby: Fallback EQ ready (Boost Off)"
                        }
                        lastHasError = false
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed applying gain to fallback EQ chain: ${e.message}", e)
                        initEffects()
                        return
                    }
                } else {
                    initEffects()
                    return
                }
            }
            EffectType.NONE -> {
                initEffects()
                return
            }
        }
        notifyState()
    }

    @Synchronized
    private fun checkAndRecoverEffect() {
        var needsReinit = false
        try {
            when (currentEffectType) {
                EffectType.LOUDNESS_ENHANCER -> {
                    val le = loudnessEnhancer
                    if (le == null || !le.hasControl()) {
                        needsReinit = true
                    }
                }
                EffectType.EQUALIZER_FALLBACK -> {
                    val eq = equalizer
                    if (eq == null || !eq.hasControl()) {
                        needsReinit = true
                    }
                }
                EffectType.NONE -> {
                    // Try to acquire again in case audioserver just became available
                    needsReinit = true
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Effect watchdog check threw exception: ${e.message}", e)
            needsReinit = true
        }

        if (needsReinit) {
            Log.i(TAG, "Watchdog detected lost effect or audioserver restart. Re-attaching...")
            initEffects()
        }
    }

    private fun saveSettings() {
        BoosterPreferences.getPreferences(context).edit()
            .putBoolean(BoosterPreferences.KEY_IS_ENABLED, isEnabled)
            .putInt(BoosterPreferences.KEY_GAIN_MB, currentGainMilliBels)
            .apply()
    }

    private fun notifyState() {
        val state = BoosterState(
            isEnabled = isEnabled,
            gainMilliBels = currentGainMilliBels,
            activeEffect = currentEffectType,
            statusMessage = lastStatusMessage,
            hasError = lastHasError
        )
        onStateChanged(state)
    }

    fun getCurrentState(): BoosterState {
        return BoosterState(
            isEnabled = isEnabled,
            gainMilliBels = currentGainMilliBels,
            activeEffect = currentEffectType,
            statusMessage = lastStatusMessage,
            hasError = lastHasError
        )
    }

    @Synchronized
    private fun releaseEffectsInternal() {
        try {
            loudnessEnhancer?.enabled = false
            loudnessEnhancer?.release()
        } catch (ignored: Exception) {}
        loudnessEnhancer = null

        try {
            equalizer?.enabled = false
            equalizer?.release()
        } catch (ignored: Exception) {}
        equalizer = null

        try {
            bassBoost?.enabled = false
            bassBoost?.release()
        } catch (ignored: Exception) {}
        bassBoost = null

        try {
            virtualizer?.enabled = false
            virtualizer?.release()
        } catch (ignored: Exception) {}
        virtualizer = null
    }

    @Synchronized
    fun release() {
        mainHandler.removeCallbacks(watchdogRunnable)
        releaseEffectsInternal()
        currentEffectType = EffectType.NONE
        lastStatusMessage = "Service stopped. Effects released."
        notifyState()
    }

    private fun formatDb(milliBels: Int): String {
        val db = milliBels / 100.0
        return String.format(java.util.Locale.US, "%.1f", db)
    }
}
