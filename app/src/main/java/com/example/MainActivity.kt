package com.example

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.AudioEffectManager
import com.example.audio.BoosterService
import com.example.audio.BoosterState
import com.example.audio.EffectType
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.TVCardBorder
import com.example.ui.theme.TVCardBorderFocused
import com.example.ui.theme.TVCyan
import com.example.ui.theme.TVGreen
import com.example.ui.theme.TVNavyCard
import com.example.ui.theme.TVNavyDark
import com.example.ui.theme.TVRed
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

class MainActivity : ComponentActivity() {

    private var boosterService: BoosterService? = null
    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? BoosterService.LocalBinder
            boosterService = binder?.getService()
            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            boosterService = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ensure service is started whenever app is opened
        BoosterService.start(this)

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = TVNavyDark
                ) {
                    val serviceState by BoosterService.serviceState.collectAsState()

                    AudioBoosterTVScreen(
                        state = serviceState,
                        onToggleEnabled = {
                            val intent = Intent(this, BoosterService::class.java).apply {
                                action = BoosterService.ACTION_TOGGLE
                            }
                            startService(intent)
                        },
                        onStepGain = { deltaSteps ->
                            val intent = Intent(this, BoosterService::class.java).apply {
                                action = if (deltaSteps > 0) BoosterService.ACTION_STEP_UP else BoosterService.ACTION_STEP_DOWN
                            }
                            startService(intent)
                        }
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val intent = Intent(this, BoosterService::class.java)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }
}

@Composable
fun AudioBoosterTVScreen(
    state: BoosterState,
    onToggleEnabled: () -> Unit,
    onStepGain: (Int) -> Unit
) {
    val mainControlFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        // Automatically request focus on the primary TV control
        mainControlFocusRequester.requestFocus()
    }

    val gainPercent = ((state.gainMilliBels.toFloat() / AudioEffectManager.MAX_GAIN_MB) * 100).toInt()
    val gainDbStr = String.format(java.util.Locale.US, "%.1f", state.gainMilliBels / 100.0)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Header Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (state.isEnabled) TVCyan else TVNavyCard)
                        .border(1.dp, if (state.isEnabled) TVCyan else TVCardBorder, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = "Audio Booster Logo",
                        tint = if (state.isEnabled) Color.Black else TextSecondary,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = "Audio Booster TV",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                    Text(
                        text = "Global System Mix Amplifier • Session 0",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                    )
                }
            }

            // Power status indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(TVNavyCard)
                    .border(1.dp, TVCardBorder, RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                state.hasError -> TVRed
                                state.isEnabled -> TVGreen
                                else -> TextMuted
                            }
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when {
                        state.hasError -> "EFFECT FAILED"
                        state.isEnabled -> "BOOST ACTIVE"
                        else -> "STANDBY (OFF)"
                    },
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = when {
                            state.hasError -> TVRed
                            state.isEnabled -> TVGreen
                            else -> TextSecondary
                        }
                    )
                )
            }
        }

        // Center Primary Card: The D-Pad controllable slider & toggle
        Card(
            modifier = Modifier
                .widthIn(max = 840.dp)
                .fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = TVNavyCard),
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, TVCardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Interactive TV Control Box
                var isSliderFocused by remember { mutableStateOf(false) }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(mainControlFocusRequester)
                        .onFocusChanged { isSliderFocused = it.isFocused }
                        .focusable()
                        .onKeyEvent { keyEvent ->
                            if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                when (keyEvent.key) {
                                    Key.DirectionLeft -> {
                                        onStepGain(-1)
                                        true
                                    }
                                    Key.DirectionRight -> {
                                        onStepGain(1)
                                        true
                                    }
                                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                        onToggleEnabled()
                                        true
                                    }
                                    else -> false
                                }
                            } else {
                                false
                            }
                        }
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSliderFocused) Color(0xFF1E3A5F) else Color(0xFF131D2D))
                        .border(
                            width = if (isSliderFocused) 3.dp else 1.dp,
                            color = if (isSliderFocused) TVCardBorderFocused else TVCardBorder,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(24.dp)
                        .testTag("tv_dpad_gain_control")
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Value display
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Column {
                                Text(
                                    text = "BOOST GAIN",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = if (isSliderFocused) TVCyan else TextSecondary,
                                        letterSpacing = 1.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Text(
                                    text = if (state.isEnabled) "+$gainDbStr dB" else "Off (0 dB)",
                                    style = MaterialTheme.typography.displayMedium.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (state.isEnabled) TextPrimary else TextMuted
                                    )
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "STEPS OF 5%",
                                    style = MaterialTheme.typography.labelSmall.copy(color = TextMuted)
                                )
                                Text(
                                    text = "$gainPercent% / 100%",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (state.isEnabled) TVCyan else TextMuted
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Progress / Level Bar
                        val progress = state.gainMilliBels.toFloat() / AudioEffectManager.MAX_GAIN_MB
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(14.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(Color(0xFF0B132B))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fraction = progress.coerceIn(0f, 1f))
                                    .height(14.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(
                                        if (state.isEnabled) {
                                            if (state.gainMilliBels > 450) Color(0xFFF97316) else TVCyan
                                        } else {
                                            Color(0xFF334155)
                                        }
                                    )
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Visual tick marks: 0 dB to +6 dB
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("0 dB (Default)", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted))
                            Text("+3.0 dB (50%)", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted))
                            Text("+6.0 dB (Max Cap)", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFF87171), fontWeight = FontWeight.Bold))
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Focus hint instruction
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSliderFocused) Color(0xFF0F2B48) else Color(0xFF0F172A))
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = if (isSliderFocused) TVCyan else TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isSliderFocused) {
                                    "D-PAD: ◀ Left (-5%)   ▶ Right (+5%)   [● OK / Center] Toggle On/Off"
                                } else {
                                    "Press D-pad to focus and adjust booster"
                                },
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSliderFocused) TVCyan else TextSecondary
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Action buttons for accessibility / alternative clicking
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    TVActionButton(
                        text = if (state.isEnabled) "Turn Boost Off" else "Turn Boost On",
                        icon = Icons.Default.PowerSettingsNew,
                        isPrimary = state.isEnabled,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("toggle_boost_button"),
                        onClick = onToggleEnabled
                    )

                    TVActionButton(
                        text = "- 5% Boost",
                        icon = Icons.Default.VolumeDown,
                        isPrimary = false,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("decrease_gain_button"),
                        onClick = { onStepGain(-1) }
                    )

                    TVActionButton(
                        text = "+ 5% Boost",
                        icon = Icons.Default.VolumeUp,
                        isPrimary = false,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("increase_gain_button"),
                        onClick = { onStepGain(1) }
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Engine status banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            when {
                                state.hasError -> Color(0xFF450A0A)
                                state.activeEffect == EffectType.LOUDNESS_ENHANCER -> Color(0xFF06283D)
                                state.activeEffect == EffectType.EQUALIZER_FALLBACK -> Color(0xFF3B2A05)
                                else -> Color(0xFF1E293B)
                            }
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (state.hasError) Icons.Default.Warning else Icons.Default.Info,
                            contentDescription = null,
                            tint = if (state.hasError) TVRed else TVCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = state.statusMessage,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = if (state.hasError) Color(0xFFFCA5A5) else TextPrimary,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }
            }
        }

        // Warning & TV Optimization Notice Footer
        Column(
            modifier = Modifier
                .widthIn(max = 840.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF261E08))
                    .border(1.dp, Color(0xFF78350F), RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Warning",
                    tint = Color(0xFFFBBF24),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "HARDWARE SAFETY LIMIT: Gain is capped at +6 dB. Excessive amplification beyond speaker capacity can cause acoustic distortion, buzzing, or permanent speaker driver damage. Set TV master volume to 15/15 for optimal dynamic range.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFFFDE68A),
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Autonomous TV Background Service • Auto-start on boot enabled • Zero external network requests",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = TextMuted,
                    fontSize = 11.sp
                )
            )
        }
    }
}

@Composable
fun TVActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isPrimary: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Button(
        onClick = onClick,
        modifier = modifier
            .height(50.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .border(
                width = if (isFocused) 3.dp else 1.dp,
                color = if (isFocused) TVCardBorderFocused else TVCardBorder,
                shape = RoundedCornerShape(10.dp)
            ),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isFocused) {
                if (isPrimary) TVCyan else Color(0xFF1E3A5F)
            } else {
                if (isPrimary) Color(0xFF0369A1) else Color(0xFF1E293B)
            },
            contentColor = if (isFocused && isPrimary) Color.Black else TextPrimary
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}
