@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.nuancun.jmcomic.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nuancun.jmcomic.mobile.source.SearchQuery
import com.nuancun.jmcomic.mobile.source.SearchRanker


@Composable
internal fun AppTopBar(
    title: String,
    navigation: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    largeTitle: Boolean = false,
    backdrop: ChromeBackdrop? = null,
) {
    val bar: @Composable () -> Unit = {
        Column {
            TopAppBar(
                title = { Text(title, style = if (largeTitle) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
                    maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = navigation, actions = actions,
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                expandedHeight = 48.dp,
            )
        }
    }
    val source = backdrop ?: LocalAmbientBackdrop.current
    if (source == null) bar() else FrostedChrome(source, if (LocalWarmDark.current) MaterialTheme.colorScheme.surface else Color.White) { bar() }
}

@Composable
internal fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    onSearch: (() -> Unit)? = null,
) {
    TextField(
        value = value, onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .border(BorderStroke(.7.dp, MaterialTheme.colorScheme.outline.copy(alpha = .25f)), RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        placeholder = { Text(hint, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(Icons.Default.Search, null) },
        trailingIcon = if (value.isNotEmpty() || onSearch != null) { {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (value.isNotEmpty()) IconButton(onClick = { onValueChange("") }) { Icon(Icons.Default.Close, "清空搜索") }
                if (onSearch != null) TextButton(onClick = onSearch, enabled = value.isNotBlank(), colors = ButtonDefaults.textButtonColors(contentColor = actionInk)) { Text("搜索") }
            }
        } } else null,
        singleLine = true, textStyle = MaterialTheme.typography.bodyMedium,
        keyboardOptions = KeyboardOptions(imeAction = if (onSearch == null) ImeAction.Done else ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch?.invoke() }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = .8f),
            cursorColor = actionInk,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

@Composable
internal fun SectionHeading(title: String, modifier: Modifier = Modifier, detail: String? = null) {
    Row(modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
internal fun StateLabel(label: String, failed: Boolean = false) {
    Text(label, style = MaterialTheme.typography.labelMedium,
        color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun PageHeading(title: String) {
    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
    }
}

@Composable
internal fun AppearanceSheet(mode: AppearanceMode, onSelect: (AppearanceMode) -> Unit, onDismiss: () -> Unit) {
    GlassBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text("纸与光", style = MaterialTheme.typography.headlineSmall)
            Text("选择书库的明暗，让阅读回到舒适的节奏。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            AppearanceMode.entries.forEach { choice ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 60.dp)
                        .selectable(selected = mode == choice, role = Role.RadioButton, onClick = { onSelect(choice) })
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = mode == choice, onClick = null)
                    Text(choice.label, Modifier.padding(start = 14.dp), style = MaterialTheme.typography.bodyLarge)
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End), colors = ButtonDefaults.textButtonColors(contentColor = actionInk)) { Text("完成") }
        }
    }
}

@Composable
internal fun highlightedText(value: String, query: SearchQuery?): AnnotatedString {
    val ranges = remember(value, query) { query?.let { SearchRanker.highlights(value, it) }.orEmpty() }
    val background = MaterialTheme.colorScheme.primaryContainer
    return remember(value, ranges, background) {
        buildAnnotatedString {
            append(value)
            ranges.forEach { addStyle(SpanStyle(background = background, fontWeight = FontWeight.Bold), it.start, it.end) }
        }
    }
}

@Composable
internal fun EmptyState(
    title: String,
    body: String,
    action: String?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val compact = with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.height.toDp() < 600.dp
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = if (compact) 4.dp else 32.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.MenuBook, null, Modifier.size(if (compact) 42.dp else 56.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(if (compact) 4.dp else 12.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = if (compact) 4.dp else 8.dp))
            if (action != null && onClick != null) Button(onClick = onClick) { Text(action) }
        }
    }
}

