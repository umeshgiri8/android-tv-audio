package com.example.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BoosterService : Service() {

    companion object {
        private const val TAG = "BoosterService"
        private const val NOTIFICATION_CHANNEL_ID = "audio_booster_tv_service"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.example.audio.ACTION_START"
        const val ACTION_STOP = "com.example.audio.ACTION_STOP"
        const val ACTION_TOGGLE = "com.example.audio.ACTION_TOGGLE"
        const val ACTION_STEP_UP = "com.example.audio.ACTION_STEP_UP"
        const val ACTION_STEP_DOWN = "com.example.audio.ACTION_STEP_DOWN"

        private val _serviceState = MutableStateFlow(BoosterState())
        val serviceState: StateFlow<BoosterState> = _serviceState.asStateFlow()

        var isRunning = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, BoosterService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private var audioEffectManager: AudioEffectManager? = null
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): BoosterService = this@BoosterService
        fun getEffectManager(): AudioEffectManager? = audioEffectManager
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "BoosterService onCreate")
        createNotificationChannel()

        // Start in foreground immediately to satisfy Android background-start limits
        val initialNotification = buildNotification(_serviceState.value)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                initialNotification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, initialNotification)
        }
        isRunning = true

        audioEffectManager = AudioEffectManager(this) { state ->
            _serviceState.value = state
            updateNotification(state)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "BoosterService onStartCommand action: ${intent?.action}")

        when (intent?.action) {
            ACTION_TOGGLE -> {
                audioEffectManager?.toggleEnabled()
            }
            ACTION_STEP_UP -> {
                audioEffectManager?.stepGain(1)
            }
            ACTION_STEP_DOWN -> {
                audioEffectManager?.stepGain(-1)
            }
            ACTION_STOP -> {
                stopForeground(true)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                // Ensure audioEffectManager state is up to date
                audioEffectManager?.let { mgr ->
                    _serviceState.value = mgr.getCurrentState()
                    updateNotification(mgr.getCurrentState())
                }
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onDestroy() {
        Log.i(TAG, "BoosterService onDestroy: releasing audio effects cleanly")
        isRunning = false
        audioEffectManager?.release()
        audioEffectManager = null
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Audio Booster Background Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps TV system audio enhancer active on Session 0"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(state: BoosterState): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            pendingIntentFlags
        )

        val boostDb = String.format(java.util.Locale.US, "%.1f", state.gainMilliBels / 100.0)
        val title = if (state.isEnabled) "Audio Boost: +$boostDb dB (Active)" else "Audio Boost: Disabled (Standby)"
        val content = state.statusMessage

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(openAppPendingIntent)
            .build()
    }

    private fun updateNotification(state: BoosterState) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.notify(NOTIFICATION_ID, buildNotification(state))
    }
}
