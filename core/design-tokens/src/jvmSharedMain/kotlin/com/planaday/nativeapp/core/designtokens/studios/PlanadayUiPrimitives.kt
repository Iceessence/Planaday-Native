package com.planaday.nativeapp.core.designtokens.studios

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.planaday.nativeapp.core.designtokens.LocalPlanadaySpacing

/**
 * Shared commercial Planaday UI primitives (Update 2 §7).
 * Clean hairline borders, refined surface contrast, >=48dp accessible controls, zero engineering jargon.
 */
@Composable
fun StudioCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    headerAction: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spacing = LocalPlanadaySpacing.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(spacing.cardCornerRadius),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, spacing.semantics.subtleBorder),
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(spacing.density.itemPadding + 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (title != null || headerAction != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        if (title != null) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (subtitle != null) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    headerAction?.invoke()
                }
            }
            content()
        }
    }
}

@Composable
fun StudioMetricTile(
    label: String,
    value: String,
    accent: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalPlanadaySpacing.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, spacing.semantics.subtleBorder),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun StudioStatusPill(
    text: String,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(999.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            maxLines = 1,
        )
    }
}

@Composable
fun StudioEmptyState(
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val spacing = LocalPlanadaySpacing.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, spacing.semantics.subtleBorder),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (actionLabel != null && onAction != null) {
                Button(
                    onClick = onAction,
                    modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = actionLabel },
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
fun StudioTabRow(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        tabs.forEachIndexed { idx, label ->
            FilterChip(
                selected = idx == selectedIndex,
                onClick = { onSelect(idx) },
                label = { Text(label, maxLines = 1) },
                modifier = Modifier.heightIn(min = 40.dp).semantics { contentDescription = "Switch tab to $label" },
            )
        }
    }
}

/**
 * Simple bounded Undo/Redo state stack shared across Notes, Spreadsheets, Planner, Slides, Whiteboard, and Book Studio.
 */
class SnapshotUndoRedoStack<T>(private val maxHistory: Int = 50) {
    private val undoStack = ArrayDeque<T>()
    private val redoStack = ArrayDeque<T>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun pushCheckpoint(current: T) {
        if (undoStack.lastOrNull() == current) return
        undoStack.addLast(current)
        while (undoStack.size > maxHistory) undoStack.removeFirst()
        redoStack.clear()
    }

    fun undo(current: T): T? {
        if (undoStack.isEmpty()) return null
        val previous = undoStack.removeLast()
        redoStack.addLast(current)
        return previous
    }

    fun redo(current: T): T? {
        if (redoStack.isEmpty()) return null
        val next = redoStack.removeLast()
        undoStack.addLast(current)
        return next
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
}

@Composable
fun StudioMasterDetailPane(
    isCompact: Boolean,
    showDetailOnCompact: Boolean,
    onBackCompact: () -> Unit,
    masterTitle: String,
    masterContent: @Composable () -> Unit,
    detailContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (isCompact) {
        Column(modifier = modifier.fillMaxSize()) {
            if (showDetailOnCompact) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = onBackCompact,
                        modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Back to $masterTitle" },
                    ) { Text("← $masterTitle") }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) { detailContent() }
            } else {
                Box(Modifier.fillMaxSize()) { masterContent() }
            }
        }
    } else {
        Row(modifier = modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.width(290.dp).fillMaxHeight()) { masterContent() }
            Box(
                Modifier.width(1.dp).fillMaxHeight()
                    .background(LocalPlanadaySpacing.current.semantics.subtleBorder)
            )
            Box(Modifier.weight(1f).fillMaxHeight()) { detailContent() }
        }
    }
}
