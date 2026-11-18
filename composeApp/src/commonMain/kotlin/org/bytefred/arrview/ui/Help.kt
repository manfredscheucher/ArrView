package org.bytefred.arrview.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import org.bytefred.arrview.core.LayoutSettings

/** The Help menu's pages, in menu order (About has its own dialog). */
enum class HelpPage(val title: String) {
    GENERAL("General"),
    WIDGETS("Widgets"),
    FORMATS("Formats"),
    SHORTCUTS("Keyboard shortcuts"),
}

/** One Help page in a [BigDialog]: plain paragraphs; the shortcuts page lists the keys in bold. */
@Composable
fun HelpDialog(page: HelpPage, onDismiss: () -> Unit) {
    BigDialog(page.title, onDismiss, buttons = { TextButton(onClick = onDismiss) { Text("OK") } }) {
        val vScroll = rememberScrollState()
        ScrollbarBox(vScroll, Modifier.weight(1f).fillMaxWidth()) {
            Column(
                Modifier.verticalScroll(vScroll).padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(if (page == HelpPage.SHORTCUTS) 4.dp else 12.dp),
            ) {
                if (page == HelpPage.SHORTCUTS) {
                    for ((key, action) in SHORTCUTS) KeyLine(key, action)
                    Paragraph(SHORTCUTS_NOTE)
                } else {
                    for (text in helpText(page)) Paragraph(text)
                }
            }
        }
    }
}

/** The paragraphs of [page] as plain text (the shortcuts page: "key: action" per key). */
internal fun helpText(page: HelpPage): List<String> = when (page) {
    HelpPage.GENERAL -> GENERAL_TEXT
    HelpPage.WIDGETS -> WIDGETS_TEXT
    HelpPage.FORMATS -> FORMATS_TEXT
    HelpPage.SHORTCUTS -> SHORTCUTS.map { (key, action) -> "$key: $action" } + SHORTCUTS_NOTE
}

@Composable
private fun Paragraph(text: String) = Text(text, style = MaterialTheme.typography.bodyLarge)

/** A shortcut: the key in bold, then what it does. */
@Composable
private fun KeyLine(key: String, action: String) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(key) }
            append(": $action")
        },
        style = MaterialTheme.typography.bodyLarge,
    )
}

/** [eps] as typed and shown: `1e-8`, not `1.0E-8` (the JVM's toString). */
fun epsText(eps: Double): String = eps.toString().replace("E", "e").replace(".0e", "e")

private val GENERAL_TEXT = listOf(
    "ArrView shows arrangements of pseudocircles: closed curves on the sphere, every two of them cross " +
        "twice or not at all, and together they are connected. Triangles are filled light orange, digons light blue.",
    "Click a triangle to flip it. Click a digon to collapse it, so that its two circles no longer cross; " +
        "this is refused if the arrangement would fall apart. Click a face where two circles that do not " +
        "cross both have a side to create a digon: one is pushed across the other, they cross twice. If there " +
        "are several ways, a menu lists the pairs of circles (or directly the ways of the only pair), and a " +
        "pair with several ways opens a submenu: " +
        "the face with a star after the two sides that are pushed together. The outer face works the same. " +
        "A \"!\" appears in the face under the mouse when a click does something there (for the outer face " +
        "in a corner of the canvas). Undo and Redo are in the Edit menu.",
    "In menus a face is written as the circles of its sides in order around it, for example 1 2 1 3. " +
        "Pointing at a menu entry highlights its face in yellow (the outer face: its boundary, and the entry " +
        "says (outer face)), the two sides of a digon create in orange.",
    "Right-click a face (Ctrl+click on a Mac) for its menu: Flip triangle, Collapse digon, one Create " +
        "digon entry per pair of circles that can create a digon there (several ways: a submenu), and Make " +
        "outer face, which draws that face around the rest. Drag to pan, zoom with the wheel or by pinching.",
    "Where there are several ways to create a digon of circles i and j, they are listed along circle i " +
        "(the smaller number), in the order of its sequence, then along circle j.",
    "Model 1 and Model 2 (toolbar, View menu, Layout widget) are two ways to draw. Model 1 draws the " +
        "crossings only, a digon is a point where its two circles touch (blue dot). It needs an " +
        "intersecting arrangement (every two circles cross) of at least 3 circles. Model 2 draws digons as regions and " +
        "works for every arrangement. The drawing is an iterated Tutte embedding as in the paper by " +
        "Felsner and Scheucher (see About).",
    "In the Arrangement menu, Examples... has the bundled arrangements, Import... reads pasted text and " +
        "Export... gives the text to copy. Open file... and Save file... read and write files, Export SVG " +
        "saves the picture.",
    "If the arrangement has changed since it was loaded or saved, ArrView asks whether to save it before " +
        "it replaces it. On the desktop, closing the app asks too. An unapplied edit in a widget is never " +
        "saved, only discarded.",
)

private val WIDGETS_TEXT = listOf(
    "Widgets are columns next to the canvas (below it on a narrow window). The toolbar and the View menu " +
        "show and hide them. The open widgets, the divider positions, the model and the layout settings " +
        "are remembered for the next start.",
    "Properties: n, the numbers of crossings and faces, the p-vector (pk faces with k sides, p2 digons " +
        "and p3 triangles), the pairs of circles that do not cross, the classes and how many triples of " +
        "pairwise crossing circles are Krupp or NonKrupp. Intersecting: every two circles cross. " +
        "Digon-free: no digon. Pseudo great circles: intersecting and every triple a Krupp. Cylindrical: " +
        "two faces are separated by every circle.",
    "Sequences: for every circle the crossings along it (see Formats). Edit them and press Apply; " +
        "ArrView checks the conditions of Linhart and Ortner and says why invalid input is rejected. " +
        "Revert brings back the current sequences.",
    "Encoding: the model 1 and model 2 sparse6 strings (see Formats), editable the same way. Copy puts " +
        "a string on the clipboard. Model 1 exists only for intersecting arrangements.",
    "Intersection graph: the circles as vertices 1 to n in their colours, an edge where two circles cross. " +
        "A dashed edge: the two circles do not cross but can create a digon. A yellow stripe marks every pair " +
        "that can create or collapse a digon, twice as strong if there are several ways; click it to do it " +
        "(a menu lists the faces if there are several, a face with several ways opens a submenu).",
    "Triangle hypergraph: the same vertices, and a transparent triangle for every triple of circles that " +
        "bounds a triangle face, so overlapping triangles add up to darker areas. A triple with two or more " +
        "triangle faces is twice as strong. Click a triangle to flip it; where several are under the mouse, " +
        "a menu lists them with their faces (several triples: a submenu per triple).",
    "Layout: Max. iterations (default ${LayoutSettings().maxIterations}) and eps (default " +
        "${epsText(LayoutSettings().eps)}). The drawing stops after max. iterations, or earlier when the " +
        "weights change by less than eps. Reset restores the defaults.",
)

private val FORMATS_TEXT = listOf(
    "Sequences: {1: [2, -2, 3, -3], 2: [...], ...}. List i has the crossings of circle i in the order of " +
        "its orientation: +j where circle j crosses circle i from its right to its left, -j where it " +
        "crosses from left to right. So +j in list i and -i in list j are the same crossing.",
    "Model 1 sparse6: the dual graph of an intersecting arrangement (a vertex per face, an edge per arc " +
        "between two crossings) in the sparse6 format of nauty.",
    "Model 2 sparse6: the vertex-edge-face graph (a vertex per crossing, arc and face, an edge where " +
        "they meet) plus a leaf at every face vertex, so faces can be told apart. It works for every " +
        "arrangement. The older encoding with marker hubs is read too.",
    "Both are the formats of the pseudocircles website (see About). ArrView may number the vertices " +
        "differently, so its strings describe the same arrangements but need not be equal to the website's.",
    "Save file writes JSON {\"name\": ..., \"sequences\": [[...], ...]} (list i for circle i) or the " +
        "sequences as text. Open file and Import read both and a sparse6 line; model 1 is tried first.",
)

/** The keyboard shortcuts page: key and action (doc/USER_GUIDE.md has the same list). */
private val SHORTCUTS = listOf(
    "Esc" to "close the open menu",
    "Ctrl+Z" to "undo",
    "Ctrl+Shift+Z or Ctrl+Y" to "redo",
    "+ / -" to "zoom in / out",
    "Home or 0" to "fit",
    "Mouse" to "click a triangle to flip it, a digon to collapse it, a face to create a digon, right-click a face for its menu, " +
        "drag to pan, wheel or pinch to zoom",
)

private const val SHORTCUTS_NOTE = "On a Mac, Cmd works like Ctrl and Ctrl+click like a right-click. The keys " +
    "do nothing while you type in a field or a menu is open (except Esc)."
