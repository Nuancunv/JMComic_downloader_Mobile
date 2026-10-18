@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.nuancun.jmcomic.mobile.ui

import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import java.util.function.Consumer

@Composable
internal fun PaperCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null,
    enabled: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val border = BorderStroke(.7.dp, MaterialTheme.colorScheme.outlineVariant)
    val color = MaterialTheme.colorScheme.surface
    if (onClick == null) Surface(modifier, shape, color, border = border, shadowElevation = 1.dp) { Column(content = content) }
    else Surface(onClick, modifier, enabled, shape, color, border = border, shadowElevation = 1.dp) { Column(content = content) }
}

@Composable
internal fun BookGlyph(modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.primary
    val paper = MaterialTheme.colorScheme.surface
    Canvas(modifier.size(width = 38.dp, height = 48.dp)) {
        drawRoundRect(ink.copy(alpha = .16f), cornerRadius = CornerRadius(5.dp.toPx()))
        drawRoundRect(ink.copy(alpha = .8f), size = Size(4.dp.toPx(), size.height), cornerRadius = CornerRadius(2.dp.toPx()))
        drawLine(paper, Offset(10.dp.toPx(), size.height - 9.dp.toPx()), Offset(size.width - 6.dp.toPx(), size.height - 9.dp.toPx()), 2.dp.toPx())
        drawLine(ink.copy(alpha = .38f), Offset(11.dp.toPx(), 12.dp.toPx()), Offset(size.width - 8.dp.toPx(), 12.dp.toPx()), 1.dp.toPx())
        drawLine(ink.copy(alpha = .38f), Offset(11.dp.toPx(), 18.dp.toPx()), Offset(size.width - 14.dp.toPx(), 18.dp.toPx()), 1.dp.toPx())
    }
}

/** Separate dialog windows use Android's cross-window blur; never share Compose layer coordinates. */
@Composable
internal fun rememberDialogBlur(): Boolean {
    val view = LocalView.current
    val manager = LocalContext.current.getSystemService(WindowManager::class.java)
    val radius = with(LocalDensity.current) { 16.dp.roundToPx() }
    var enabled by remember { mutableStateOf(false) }
    DisposableEffect(view, manager, radius) {
        val window = (view.parent as? DialogWindowProvider)?.window
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && window != null) {
            val listener = Consumer<Boolean> { available ->
                enabled = available
                window.attributes = window.attributes.apply { blurBehindRadius = if (available) radius else 0 }
                if (available) window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            }
            manager.addCrossWindowBlurEnabledListener(listener)
            listener.accept(manager.isCrossWindowBlurEnabled)
            onDispose {
                manager.removeCrossWindowBlurEnabledListener(listener)
                window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            }
        } else onDispose { }
    }
    return enabled
}

@Composable
internal fun GlassPanel(modifier: Modifier = Modifier, blurred: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val dark = LocalWarmDark.current
    val tint = if (dark) MaterialTheme.colorScheme.surface else Color.White
    Column(modifier.clip(RoundedCornerShape(28.dp)).glassFinish(tint, dark, blurred, 28.dp), content = content)
}

@Composable
internal fun GlassBottomSheet(onDismissRequest: () -> Unit, stableContent: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(onDismissRequest, containerColor = Color.Transparent, dragHandle = null) {
        val blurred = rememberDialogBlur()
        GlassPanel(Modifier.fillMaxWidth(), blurred) {
            Box(Modifier.align(androidx.compose.ui.Alignment.CenterHorizontally).padding(top = 12.dp, bottom = 20.dp)
                .size(width = 32.dp, height = 4.dp).clip(RoundedCornerShape(2.dp))
                .then(Modifier.glassFinish(MaterialTheme.colorScheme.onSurfaceVariant, LocalWarmDark.current, false, 2.dp)))
            // Reader pages can be arbitrarily dark or bright; give its controls a stable paper backing.
            if (stableContent) Column(Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = .88f), RoundedCornerShape(20.dp)), content = content)
            else content()
        }
    }
}

@Composable
internal fun GlassAlertDialog(onDismissRequest: () -> Unit, title: @Composable () -> Unit,
    text: @Composable () -> Unit, confirmButton: @Composable () -> Unit, dismissButton: @Composable () -> Unit) {
    Dialog(onDismissRequest) {
        GlassPanel(Modifier.fillMaxWidth(), rememberDialogBlur()) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ProvideTextStyle(MaterialTheme.typography.titleLarge, title)
                ProvideTextStyle(MaterialTheme.typography.bodyMedium, text)
                FlowRow(Modifier.align(androidx.compose.ui.Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    dismissButton(); confirmButton()
                }
            }
        }
    }
}

@Composable
internal fun WarmTextButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit) {
    TextButton(onClick, modifier, enabled, colors = ButtonDefaults.textButtonColors(contentColor = actionInk), content = content)
}

@Composable
internal fun WarmOutlinedButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit) {
    OutlinedButton(onClick, modifier, enabled, colors = ButtonDefaults.outlinedButtonColors(contentColor = actionInk), content = content)
}
