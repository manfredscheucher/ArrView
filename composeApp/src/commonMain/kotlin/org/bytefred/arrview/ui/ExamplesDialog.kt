package org.bytefred.arrview.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.bytefred.arrview.core.ExampleGroup
import org.bytefred.arrview.core.ExampleSet
import org.bytefred.arrview.core.Examples

/**
 * What the Examples dialog remembers between openings ([AppState.examples]): the expanded groups
 * (everything starts collapsed), the selected set, the chosen member per set and the scroll
 * position. Reopening shows the tree exactly as it was left.
 */
class ExamplesState {
    val expanded = mutableStateListOf<String>()
    var selected by mutableStateOf<ExampleSet?>(null)

    /** The chosen member (0-based) per set name; 0 if none was chosen. */
    val index = mutableStateMapOf<String, Int>()
    val scroll = ScrollState(0)

    fun indexOf(set: ExampleSet): Int = index[set.name] ?: 0
}

/**
 * The groups of bundled examples ([Examples]), each set a leaf. The selected set shows its
 * description, size and, for a list, a chooser for the member. Load replaces the arrangement with
 * it (after "Save changes?", [AppState.leave]). The data is compiled in, so nothing has to load.
 */
@Composable
fun ExamplesDialog(state: AppState, close: () -> Unit) {
    val ex = state.examples
    val open = ex.expanded
    fun toggle(key: String) { if (key in open) open.remove(key) else open.add(key) }
    // a typed member number out of range: Load waits until it is fixed
    var indexError by remember { mutableStateOf(false) }
    fun load() {
        val set = ex.selected ?: return
        val i = ex.indexOf(set)
        state.leave { state.load(set.memberName(i), set.arrangement(i)); close() }
    }
    BigDialog(
        "Examples",
        onDismiss = close,
        buttons = {
            TextButton(onClick = close) { Text("Cancel") }
            TextButton(onClick = ::load, enabled = ex.selected != null && !indexError) { Text("Load") }
        },
    ) {
        ScrollColumn(Modifier.fillMaxSize(), ex.scroll) {
            FeedbackBox("Know an arrangement that belongs here, or have general feedback?")
            Spacer(Modifier.height(8.dp))
            for (group in ExampleGroup.entries) {
                val sets = Examples.inGroup(group)
                TreeHeader("${group.title} (${sets.size})", group.name in open) { toggle(group.name) }
                HorizontalDivider()
                if (group.name in open) for (set in sets) {
                    TreeLeaf(set.name, ex.selected == set) {
                        // the same leaf again keeps its (maybe invalid) number field
                        if (ex.selected != set) indexError = false
                        ex.selected = set
                    }
                    if (ex.selected == set) key(set.name) {
                        ExampleDetails(state, set, ex.indexOf(set)) { i ->
                            if (i != null) ex.index[set.name] = i
                            indexError = i == null
                        }
                    }
                }
            }
        }
    }
}

/** A clickable, expand/collapse group with a drawn arrow (no glyph: the web font lacks them). */
@Composable
private fun TreeHeader(label: String, open: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onToggle() }.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TreeArrow(open)
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/** A triangle pointing right (collapsed) or down (expanded). */
@Composable
private fun TreeArrow(open: Boolean) {
    val color = MaterialTheme.colorScheme.onSurface
    Canvas(Modifier.size(12.dp)) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            if (open) {
                moveTo(0.05f * w, 0.25f * h)
                lineTo(0.95f * w, 0.25f * h)
                lineTo(0.5f * w, 0.85f * h)
            } else {
                moveTo(0.25f * w, 0.05f * h)
                lineTo(0.85f * w, 0.5f * h)
                lineTo(0.25f * w, 0.95f * h)
            }
            close()
        }
        drawPath(path, color)
    }
}

/** An example leaf: a radio button; a click selects it. */
@Composable
private fun TreeLeaf(name: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(start = 16.dp)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
    }
}

/**
 * The selected set: its description and size; for a list the member number (1..size, typed or
 * with < and >). [onIndex] gets the 0-based member, or null while the typed number is invalid.
 */
@Composable
private fun ExampleDetails(state: AppState, set: ExampleSet, index: Int, onIndex: (Int?) -> Unit) {
    Column(Modifier.padding(start = 64.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(set.description, style = MaterialTheme.typography.bodySmall)
        Text(
            if (set.size == 1) "n = ${set.n}" else "n = ${set.n}, ${set.size} arrangements",
            style = MaterialTheme.typography.bodySmall,
        )
        if (set.size > 1) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompactNumberField(
                state, (index + 1).toString(), { it.toIntOrNull()?.takeIf { i -> i in 1..set.size } },
                "1 to ${set.size}", set.size.toString().length, "example-index",
                Steppers({ onIndex(index - 1) }, index > 0, { onIndex(index + 1) }, index < set.size - 1),
            ) { i -> onIndex(i?.minus(1)) }
            Text("of ${set.size}", Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
