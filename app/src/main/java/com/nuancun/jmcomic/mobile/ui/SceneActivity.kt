package com.nuancun.jmcomic.mobile.ui

import androidx.compose.runtime.*

/** Reference-counted because an outgoing page and an incoming page can both be scrolling. */
internal class SceneActivity {
    var busyCount by mutableIntStateOf(0)
        private set
    fun pause(): () -> Unit {
        busyCount++
        var released = false
        return {
            if (!released) { released = true; busyCount-- }
        }
    }
}

internal val LocalSceneActivity = staticCompositionLocalOf<SceneActivity?> { null }
internal val LocalPageActive = staticCompositionLocalOf { true }

@Composable
internal fun PauseHeavyEffects(paused: Boolean) {
    val activity = LocalSceneActivity.current
    DisposableEffect(activity, paused) {
        val resume = if (paused) activity?.pause() else null
        onDispose { resume?.invoke() }
    }
}
