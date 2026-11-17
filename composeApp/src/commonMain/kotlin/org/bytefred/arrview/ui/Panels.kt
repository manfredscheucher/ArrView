package org.bytefred.arrview.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import org.bytefred.arrview.core.DrawModel
import org.bytefred.arrview.core.hasModel1
import org.bytefred.arrview.core.isDigonFree
import org.bytefred.arrview.core.isGreat
import org.bytefred.arrview.core.nonIntersectingPairCount

/** The row below the menu bar: the drawing model, the widget toggles and the status at the right. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ToolBar(state: AppState) {
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ToolbarLabel("Drawing")
        for (m in DrawModel.entries) FilterChip(
            selected = state.model == m,
            onClick = { state.changeModel(m) },
            enabled = m == DrawModel.MODEL2 || state.arrangement.hasModel1,
            label = { Text(m.label) },
        )
        VerticalDivider(Modifier.height(32.dp).align(Alignment.CenterVertically).padding(horizontal = 4.dp))
        ToolbarLabel("Widgets")
        for (w in Widget.entries) FilterChip(
            selected = w in state.openWidgets, onClick = { state.toggleWidget(w) },
            label = { Text(w.label) },
        )
        ToolbarStatus(state)
    }
}

/**
 * The rest of the toolbar row: the name of the arrangement, n and the last message. One line with
 * ellipsis, so it never pushes the buttons away; when the row is full it wraps to a line of its own.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowScope.ToolbarStatus(state: AppState) {
    val status = state.status
    // pure info (it disappears after a few seconds) in green
    val info = status?.transient == true
    Text(
        buildAnnotatedString {
            append("${state.title}$STATUS_SEPARATOR" + "n = ${state.arrangement.n}")
            if (status != null) {
                append(STATUS_SEPARATOR)
                if (info) withStyle(SpanStyle(color = INFO_COLOR)) { append(status.text) } else append(status.text)
            }
        },
        style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f).widthIn(min = STATUS_MIN_WIDTH).align(Alignment.CenterVertically).padding(horizontal = 4.dp).testTag("toolbar-status"),
    )
}

/** Between the parts of the toolbar status (plain ASCII, no font fallback on the web). */
const val STATUS_SEPARATOR = "   |   "

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowScope.ToolbarLabel(text: String) =
    Text(text, Modifier.align(Alignment.CenterVertically).padding(horizontal = 4.dp), style = MaterialTheme.typography.labelSmall)

private val INFO_COLOR = Color(0xFF1A7F37)

/** Less room than this left of the toolbar status in its row: it wraps to a line of its own. */
private val STATUS_MIN_WIDTH = 280.dp

/** Title row of a widget: its name and a close button. */
@Composable
fun WidgetHeader(state: AppState, w: Widget) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(start = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(w.label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        TextButton(onClick = { state.toggleWidget(w) }) { Text("x") }
    }
}

/** The content of a widget (without scrolling; the caller scrolls). */
@Composable
fun WidgetContent(state: AppState, w: Widget) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)) {
        when (w) {
            Widget.PROPERTIES -> PropertiesWidget(state)
        }
    }
}

/** The basic numbers of the arrangement. */
@Composable
private fun PropertiesWidget(state: AppState) {
    val arr = state.arrangement
    val rows = listOf(
        "n" to "${arr.n}",
        "faces" to "${arr.faceCount}",
        "triangles (p3)" to "${arr.triangleCount}",
        "digons (p2)" to "${arr.digonCount}",
        "non-intersecting pairs" to "${arr.nonIntersectingPairCount}",
        "class" to when {
            arr.isGreat -> "pseudo great circles"
            arr.isIntersecting && arr.isDigonFree -> "intersecting, digon-free"
            arr.isIntersecting -> "intersecting"
            else -> "connected"
        },
    )
    for ((label, value) in rows) Row(Modifier.padding(vertical = 2.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
    Text("p-vector (p2, p3, ...)", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium)
    Text(arr.pVector.drop(2).joinToString(", ", "(", ")"), style = MonoStyle)
}
