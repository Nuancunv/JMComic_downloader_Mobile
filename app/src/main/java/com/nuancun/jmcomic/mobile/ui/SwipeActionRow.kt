package com.nuancun.jmcomic.mobile.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

data class SwipeAction(
    val label: String,
    val icon: ImageVector,
    val background: Color,
    val foreground: Color,
    val onClick: () -> Unit,
)

val ArchiveActionColors = Color(0xFFDCEBFA) to Color(0xFF285A86)
val DeleteActionColors = Color(0xFFF9DEDE) to Color(0xFF8C3535)

fun archiveAction(archived: Boolean, onClick: () -> Unit) = SwipeAction(
    label = if (archived) "取消归档" else "归档",
    icon = if (archived) Icons.Default.Unarchive else Icons.Default.Archive,
    background = ArchiveActionColors.first,
    foreground = ArchiveActionColors.second,
    onClick = onClick,
)

fun deleteAction(label: String = "删除", onClick: () -> Unit) = SwipeAction(
    label = label,
    icon = Icons.Default.Delete,
    background = DeleteActionColors.first,
    foreground = DeleteActionColors.second,
    onClick = onClick,
)

@Composable
fun SwipeActionRow(
    itemKey: String,
    isOpen: Boolean,
    onOpenChange: (Boolean) -> Unit,
    actions: List<SwipeAction>,
    content: @Composable () -> Unit,
) {
    val actionWidth = 88.dp
    val maxOffset = with(LocalDensity.current) { (actionWidth * actions.size).toPx() }
    var offset by remember(itemKey) { mutableFloatStateOf(0f) }
    var dragging by remember(itemKey) { mutableStateOf(false) }

    LaunchedEffect(isOpen, maxOffset, dragging) {
        if (!dragging) animate(
            initialValue = offset,
            targetValue = if (isOpen) -maxOffset else 0f,
            animationSpec = spring(dampingRatio = 0.88f, stiffness = 520f),
        ) { value, _ -> offset = value }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .semantics {
                customActions = actions.map { action ->
                    CustomAccessibilityAction(action.label) { action.onClick(); true }
                }
            },
    ) {
        Row(
            Modifier
                .matchParentSize(),
            horizontalArrangement = Arrangement.End,
        ) {
            actions.forEach { action ->
                Box(
                    Modifier
                        .width(actionWidth)
                        .fillMaxHeight()
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(action.background)
                        .clickable {
                            onOpenChange(false)
                            action.onClick()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.foundation.layout.Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    ) {
                        Icon(action.icon, action.label, tint = action.foreground)
                        Text(action.label, color = action.foreground)
                    }
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .offset { IntOffset(offset.roundToInt(), 0) }
                .pointerInput(itemKey, maxOffset) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragging = true; onOpenChange(true) },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            offset = (offset + dragAmount).coerceIn(-maxOffset, 0f)
                        },
                        onDragEnd = { dragging = false; onOpenChange(offset <= -maxOffset * 0.35f) },
                        onDragCancel = { dragging = false; onOpenChange(offset <= -maxOffset * 0.35f) },
                    )
                },
        ) { content() }
    }
}
