package org.bytefred.arrview.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import org.bytefred.arrview.core.DigonCreate
import org.bytefred.arrview.core.cellDescription
import kotlin.math.roundToInt

/** An entry of an [ActionMenu]: its text, the face it acts on (highlighted while hovered) and the action. */
class MenuAction(val label: String, val face: Int, val run: () -> Unit)

/**
 * A menu of flips at [at] (pixels in the parent): a heading, then one entry per action. Entries
 * with equal labels get " (1)", " (2)", ... Hovering an entry highlights its face in the canvas
 * ([AppState.menuHighlight]).
 */
@Composable
fun ActionMenu(state: AppState, at: Offset, heading: String, actions: List<MenuAction>, close: () -> Unit) {
    val labels = menuLabels(actions)
    fun done() {
        state.menuHighlight = null
        close()
    }
    // a zero-size anchor at the pointer: the menu opens right there
    Box(Modifier.offset { IntOffset(at.x.roundToInt(), at.y.roundToInt()) }) {
        DropdownMenu(expanded = true, onDismissRequest = ::done) {
            MenuHeading(heading)
            for ((a, label) in actions.zip(labels)) {
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        done()
                        a.run()
                    },
                    modifier = Modifier.pointerInput(a.face) {
                        awaitPointerEventScope {
                            while (true) {
                                val ev = awaitPointerEvent()
                                when (ev.type) {
                                    PointerEventType.Enter, PointerEventType.Move -> state.menuHighlight = a.face
                                    PointerEventType.Exit -> if (state.menuHighlight == a.face) state.menuHighlight = null
                                }
                            }
                        }
                    },
                )
            }
        }
    }
}

/** The entry texts of [actions]: equal labels get " (1)", " (2)", ... */
fun menuLabels(actions: List<MenuAction>): List<String> {
    val counts = actions.groupingBy { it.label }.eachCount()
    val seen = HashMap<String, Int>()
    return actions.map { a ->
        val k = (seen[a.label] ?: 0) + 1
        seen[a.label] = k
        if (counts.getValue(a.label) > 1) "${a.label} ($k)" else a.label
    }
}

/** A cell for a menu entry: its description, " (outer face)" for the outer face. */
fun AppState.cellLabel(f: Int, marked: Set<Int> = emptySet()): String =
    arrangement.cellDescription(f, marked) + if (f == outerFace) " (outer face)" else ""

/** The cell of a digon create with the two sides in brackets. */
fun AppState.createCellLabel(op: DigonCreate): String = cellLabel(op.face, setOf(op.dartI, op.dartJ))

/** The circles of a pair or triple, 1-based, separated by spaces. */
fun circlesLabel(circles: List<Int>): String = circles.joinToString(" ") { "${it + 1}" }

/** The canvas menu of face [f] with several digon creates: "i j: cell with the two sides in brackets". */
fun AppState.createEntries(f: Int): List<MenuAction> =
    actions.createsIn(f).map { op -> MenuAction("${circlesLabel(listOf(op.i, op.j))}: ${createCellLabel(op)}", f) { createDigon(op) } }

/** The heading of the intersection graph menu of pair [i] < [j]. */
fun AppState.pairHeading(i: Int, j: Int): String =
    (if (actions.createsOf(i, j).isNotEmpty()) "Create a digon of " else "Collapse a digon of ") + circlesLabel(listOf(i, j))

/** The intersection graph menu of pair [i] < [j]: the cells to create through, or the digons to collapse. */
fun AppState.pairEntries(i: Int, j: Int): List<MenuAction> =
    actions.createsOf(i, j).map { op -> MenuAction(createCellLabel(op), op.face) { createDigon(op) } } +
        actions.collapsesOf(i, j).map { f -> MenuAction(cellLabel(f), f) { collapseDigon(f) } }

/** The triangle flip menu entries of [triple] (one per triangle cell). */
fun AppState.triangleEntries(triple: List<Int>, faces: List<Int>): List<MenuAction> =
    faces.map { f -> MenuAction("${circlesLabel(triple)}: ${cellLabel(f)}", f) { flipTriangle(f) } }
