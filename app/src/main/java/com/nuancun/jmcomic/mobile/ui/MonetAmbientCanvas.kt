package com.nuancun.jmcomic.mobile.ui

import android.animation.ValueAnimator
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

internal val LocalAmbientBackdrop = staticCompositionLocalOf<ChromeBackdrop?> { null }

/** Cached Gaussian brushes; the clock is read only by drawing, never by layout or a list. */
@Composable
internal fun AmbientScene(enabled: Boolean, content: @Composable () -> Unit) {
    val ambient = rememberChromeBackdrop(includeAmbient = false)
    val dark = LocalWarmDark.current
    val canvas = MaterialTheme.colorScheme.background
    val seconds = remember { mutableFloatStateOf(0f) }
    val activity = LocalSceneActivity.current
    val busy = (activity?.busyCount ?: 0) > 0
    val owner = LocalLifecycleOwner.current
    val resolver = LocalContext.current.contentResolver
    var resumed by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var motion by remember { mutableStateOf(ValueAnimator.areAnimatorsEnabled()) }
    DisposableEffect(owner, resolver) {
        val lifecycleObserver = LifecycleEventObserver { _, _ ->
            resumed = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        val scaleObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { motion = ValueAnimator.areAnimatorsEnabled() }
        }
        owner.lifecycle.addObserver(lifecycleObserver)
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, scaleObserver)
        onDispose { owner.lifecycle.removeObserver(lifecycleObserver); resolver.unregisterContentObserver(scaleObserver) }
    }
    LaunchedEffect(enabled, resumed, motion, busy) {
        if (enabled && resumed && motion && !busy) {
            var previous = 0L
            while (true) withFrameNanos { now ->
                if (previous == 0L || now - previous >= 33_000_000L) {
                    if (previous != 0L) seconds.floatValue += ((now - previous) / 1_000_000_000f).coerceAtMost(.1f)
                    previous = now
                }
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().chromeBackdrop(ambient).drawWithCache {
            val colors = listOf(Color(0xFFD97757), Color(0xFF8A9478), Color(0xFFC9A66B))
            val radii = listOf(80.dp.toPx(), 110.dp.toPx(), 140.dp.toPx())
            val brushes = colors.mapIndexed { index, color ->
                val stops = (0..12).map { step ->
                    val t = step / 12f
                    t to color.copy(alpha = exp(-4.5f * t * t) * if (dark) .17f else .26f)
                }.toTypedArray()
                Brush.radialGradient(*stops, center = Offset.Zero, radius = radii[index] * 3f)
            }
            onDrawBehind {
                drawRect(canvas)
                if (enabled) {
                    val time = seconds.floatValue
                    brushes.forEachIndexed { i, brush ->
                        val anchor = when (i) {
                            0 -> Offset(size.width * .12f, size.height * .15f)
                            1 -> Offset(size.width * .95f, size.height * .46f)
                            else -> Offset(size.width * .24f, size.height * .94f)
                        }
                        val phase = time / (23f + i * 8f) + i * 2.1f
                        val center = anchor + Offset(sin(phase) * 38.dp.toPx(), cos(phase * .8f) * 52.dp.toPx())
                        val pulse = 1f + sin(phase * 1.3f) * .08f
                        withTransform({ translate(center.x, center.y); scale(pulse, pulse, Offset.Zero) }) {
                            drawCircle(brush, radius = radii[i] * 3f, center = Offset.Zero)
                        }
                    }
                }
            }
        })
        CompositionLocalProvider(LocalAmbientBackdrop provides ambient, content = content)
    }
}
