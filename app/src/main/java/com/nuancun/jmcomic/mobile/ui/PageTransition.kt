package com.nuancun.jmcomic.mobile.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.hideFromAccessibility

/** Only route identity is animated; progress updates never become navigation events. */
@Composable
internal fun <S : Any> PageTransition(
    target: S,
    keyOf: (S) -> String,
    direction: (S, S) -> Int,
    saveState: (S) -> Boolean = { true },
    content: @Composable (S) -> Unit,
) {
    val transition = updateTransition(target, label = "page")
    val holder = rememberSaveableStateHolder()
    PauseHeavyEffects(transition.currentState != transition.targetState || transition.isRunning)
    transition.AnimatedContent(
        modifier = Modifier.fillMaxSize(),
        contentKey = keyOf,
        transitionSpec = {
            val sign = direction(initialState, targetState)
            ((fadeIn(tween(190, delayMillis = 30)) +
                slideInHorizontally(tween(240, easing = FastOutSlowInEasing)) { it / 14 * sign })
                togetherWith (fadeOut(tween(140)) +
                slideOutHorizontally(tween(200, easing = FastOutSlowInEasing)) { -it / 24 * sign }))
                .using(null)
        },
    ) { page ->
        val active = LocalPageActive.current && page == target
        val inactive = if (active) Modifier else Modifier
            .clearAndSetSemantics { hideFromAccessibility() }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            }
        Box(Modifier.fillMaxSize().then(inactive)) {
            CompositionLocalProvider(LocalPageActive provides active) {
                if (saveState(page)) holder.SaveableStateProvider(keyOf(page)) { content(page) }
                else content(page)
            }
        }
    }
}
