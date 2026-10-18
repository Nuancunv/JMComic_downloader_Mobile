package com.nuancun.jmcomic.mobile.ui

import android.os.Build
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A source excludes its consumers, preventing recursive sampling or blurred controls. */
internal class ChromeBackdrop(val layer: GraphicsLayer, val ambient: ChromeBackdrop? = null) {
    var origin by mutableStateOf(Offset.Zero)
}

@Composable
internal fun rememberChromeBackdrop(includeAmbient: Boolean = true): ChromeBackdrop {
    val layer = rememberGraphicsLayer()
    val ambient = if (includeAmbient) LocalAmbientBackdrop.current else null
    return remember(layer, ambient) { ChromeBackdrop(layer, ambient) }
}

internal fun Modifier.chromeBackdrop(backdrop: ChromeBackdrop): Modifier =
    onGloballyPositioned { backdrop.origin = it.positionInRoot() }.drawWithContent {
        backdrop.layer.record { this@drawWithContent.drawContent() }
        drawLayer(backdrop.layer)
    }

internal fun Modifier.glassFinish(tint: Color, dark: Boolean, translucent: Boolean, radius: Dp): Modifier = drawWithCache {
    val reflection = Brush.linearGradient(listOf(Color.White.copy(alpha = if (dark) .035f else .17f), Color.Transparent))
    val rim = Brush.linearGradient(listOf(Color.White.copy(alpha = if (dark) .14f else .5f), Color.White.copy(alpha = if (dark) .025f else .12f)))
    val edge = Stroke(1.dp.toPx())
    onDrawWithContent {
    drawRect(tint.copy(alpha = if (translucent) .72f else .97f))
    drawRect(reflection)
    drawContent()
    val inset = .5.dp.toPx()
    drawRoundRect(rim,
        topLeft = Offset(inset, inset), size = size.copy(width = (size.width - inset * 2).coerceAtLeast(0f), height = (size.height - inset * 2).coerceAtLeast(0f)),
        cornerRadius = CornerRadius(radius.toPx()), style = edge)
    }
}

@Composable
internal fun FrostedChrome(
    backdrop: ChromeBackdrop, tint: Color, modifier: Modifier = Modifier,
    radius: Dp = 0.dp, content: @Composable () -> Unit,
) {
    val blurred = rememberGraphicsLayer()
    var position by remember { mutableStateOf(Offset.Zero) }
    val blur = with(LocalDensity.current) { 16.dp.toPx() }
    val busy = (LocalSceneActivity.current?.busyCount ?: 0) > 0
    val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !busy
    val dark = tint.luminance() < .4f
    val effect = remember(blur) { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) BlurEffect(blur, blur, TileMode.Clamp) else null }
    val sources = remember(backdrop) { listOfNotNull(backdrop.ambient, backdrop) }
    DisposableEffect(blurred, effect, canBlur) {
        blurred.renderEffect = if (canBlur) effect else null
        onDispose { }
    }
    Box(modifier.clip(RoundedCornerShape(radius)).onGloballyPositioned { position = it.positionInRoot() }
        .drawBehind {
            if (canBlur) {
                blurred.record {
                    sources.forEach { source ->
                        if (source.layer.size.width > 0 && source.layer.size.height > 0) {
                            val offset = position - source.origin
                            translate(-offset.x, -offset.y) { drawLayer(source.layer) }
                        }
                    }
                }
                drawLayer(blurred)
            }
        }.glassFinish(tint, dark, canBlur, radius)
        .pointerInput(Unit) { detectTapGestures(onTap = {}) }) { content() }
}
