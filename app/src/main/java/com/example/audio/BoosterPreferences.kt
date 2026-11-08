package com.example.audio

import android.content.Context
import android.content.SharedPreferences

object BoosterPreferences {
    private const val PREFS_NAME = "audio_booster_tv_prefs"
    const val KEY_IS_ENABLED = "key_is_enabled"
    const val KEY_GAIN_MB = "key_gain_mb"

    fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}
