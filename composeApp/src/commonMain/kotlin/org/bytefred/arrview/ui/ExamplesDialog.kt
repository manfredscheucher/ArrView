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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.bytefred.arrview.core.ExampleGroup
import org.bytefred.arrview.core.ExampleSet
import org.bytefred.arrview.core.Examples
import org.bytefred.arrview.core.PSEUDOCIRCLES_WEBSITE

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
            Spacer(Modifier.height(4.dp))
            LinkText(
                "Most of the data is from the pseudocircles website", PSEUDOCIRCLES_WEBSITE,
                MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(4.dp))
            @Composable
            fun leaves(sets: List<ExampleSet>, depth: Int) {
                for (set in sets) {
                    TreeLeaf(set.name, depth, ex.selected == set) {
                        // the same leaf again keeps its (maybe invalid) number field
                        if (ex.selected != set) indexError = false
                        ex.selected = set
                    }
                    if (ex.selected == set) key(set.name) {
                        ExampleDetails(state, set, ex.indexOf(set), depth) { i ->
                            if (i != null) ex.index[set.name] = i
                            indexError = i == null
                        }
                    }
                }
            }
            // two levels: the sets by group, then the single named arrangements
            TreeHeader(SETS, 0, SETS in open) { toggle(SETS) }
            HorizontalDivider()
            if (SETS in open) for (group in ExampleGroup.entries.filter { it != ExampleGroup.SINGLETONS }) {
                val sets = Examples.inGroup(group)
                TreeHeader("${group.title} (${sets.size})", 1, group.name in open) { toggle(group.name) }
                if (group.name in open) leaves(sets, 2)
            }
            val singletons = Examples.inGroup(ExampleGroup.SINGLETONS)
            TreeHeader("${ExampleGroup.SINGLETONS.title} (${singletons.size})", 0, ExampleGroup.SINGLETONS.name in open) {
                toggle(ExampleGroup.SINGLETONS.name)
            }
            HorizontalDivider()
            if (ExampleGroup.SINGLETONS.name in open) leaves(singletons, 1)
        }
    }
}

/** The top node of the sets (the groups below it). */
private const val SETS = "Sets"

/** Indentation per tree level. */
private val INDENT = 16.dp

/** A clickable, expand/collapse node at [depth] with a drawn arrow (no glyph: the web font lacks them). */
@Composable
private fun TreeHeader(label: String, depth: Int, open: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onToggle() }.padding(start = INDENT * depth).padding(vertical = 12.dp),
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
private fun TreeLeaf(name: String, depth: Int, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(start = INDENT * depth)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
    }
}

/**
 * The info of the selected set, as PoCoView's: description, n and the number of arrangements (for a
 * list the member number, typed or with − and +), the website page and the paper it comes from, or
 * where else its data is from. [onIndex] gets the 0-based member, or null while the typed number is
 * invalid.
 */
@Composable
private fun ExampleDetails(state: AppState, set: ExampleSet, index: Int, depth: Int, onIndex: (Int?) -> Unit) {
    Column(
        Modifier.padding(start = INDENT * depth + 48.dp, bottom = 8.dp).testTag("example-info"),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(set.description, style = MaterialTheme.typography.bodySmall)
        Text(
            "n = ${set.n}, " + if (set.size == 1) "1 arrangement" else "${set.size} arrangements",
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
        set.page?.let { LinkText("Website: ${it.title}", it.url, MaterialTheme.typography.labelSmall) }
        set.paper?.let { LinkText("Paper: ${it.title}", it.url, MaterialTheme.typography.labelSmall) }
        Text(
            set.source?.let { "Data: $it" } ?: "Typed in as intersection sequences",
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
