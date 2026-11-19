package org.bytefred.arrview.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import org.bytefred.arrview.core.DrawModel
import org.bytefred.arrview.core.LayoutSettings

/** The Help menu's pages, in menu order (About has its own dialog). */
enum class HelpPage(val title: String) {
    GENERAL("General"),
    VISUALIZATION("Visualization"),
    WIDGETS("Widgets"),
    FORMATS("Formats"),
    SHORTCUTS("Keyboard shortcuts"),
}

/**
 * One Help page in a [BigDialog]: plain paragraphs; the shortcuts page lists the keys in bold, the
 * Visualization page has the figures of the three graphs (in the colours of [scheme]).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HelpDialog(page: HelpPage, scheme: ColorScheme, onDismiss: () -> Unit) {
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
                } else if (page == HelpPage.VISUALIZATION) {
                    Paragraph(VISUALIZATION_INTRO)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        for (kind in GraphKind.entries) Column(Modifier.width(FIGURE_SIZE)) {
                            Text(kind.title, style = MaterialTheme.typography.titleSmall)
                            GraphFigure(kind, scheme)
                            Text(kind.caption, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    for (text in VISUALIZATION_TEXT) Paragraph(text)
                    for (paper in VISUALIZATION_REFERENCES) Text(paper, style = MaterialTheme.typography.bodySmall)
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
    HelpPage.VISUALIZATION -> listOf(VISUALIZATION_INTRO) + GraphKind.entries.map { "${it.title}: ${it.caption}" } +
        VISUALIZATION_TEXT + VISUALIZATION_REFERENCES
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

/** What arrangements of pseudocircles are (Welcome dialog and the first Help paragraph). */
internal const val PSEUDOCIRCLES_INTRO = "ArrView is a viewer for arrangements of pseudocircles: closed curves where every " +
    "two curves meet at most twice and cross at each meeting point. The curves need not be circles (hence " +
    "\"pseudo\"); only the combinatorics counts."

/** The Welcome dialog's text (Manfred's wording). */
internal const val WELCOME_TEXT = "$PSEUDOCIRCLES_INTRO Change an arrangement by flips (click a triangle or a " +
    "digon) and explore and visualise its combinatorial properties."

private val GENERAL_TEXT = listOf(
    "$PSEUDOCIRCLES_INTRO ArrView shows connected arrangements (together the curves are connected). " +
        "Triangles are filled light orange, digons light blue.",
    "Click a triangle to flip it. Click a digon to collapse it, so that its two circles no longer cross; " +
        "this is refused if the arrangement would fall apart. Click a face where two circles that do not " +
        "cross both have a side to create a digon: one is pushed across the other, they cross twice. If there " +
        "are several ways, a menu lists the pairs of circles (or directly the ways of the only pair), and a " +
        "pair with several ways opens a submenu: " +
        "the face with a star after the two sides that are pushed together. The outer face works the same. " +
        "A \"!\" appears in the face under the mouse when a click does something there (for the outer face " +
        "in a corner of the canvas). Undo and Redo are in the Edit menu (also for Make outer face).",
    "In menus a face is written as the circles of its sides in order around it, for example 1 2 1 3. " +
        "Pointing at a menu entry highlights its face in yellow (the outer face: its boundary, and the entry " +
        "says (outer face)), the two sides of a digon create in orange.",
    "Right-click a face (Ctrl+click on a Mac, long press on touch screens) for its menu: Flip triangle, " +
        "Collapse digon, one Create digon entry per pair of circles that can create a digon there (several " +
        "ways: a submenu), and Make outer face, which draws that face around the rest. Drag to pan, zoom " +
        "with the wheel or by pinching.",
    "Where there are several ways to create a digon of circles i and j, they are listed along circle i " +
        "(the smaller number), in the order of its sequence, then along circle j.",
    "The drawing is model 2 by default; model 1 (legacy) is chosen in the Settings widget (Visualization) " +
        "and shows intersecting arrangements only. The Help page Visualization explains both.",
    "In the Arrangement menu, Examples... lists the bundled arrangements by purpose, as on the pseudocircles " +
        "website; an open group says what it is, a selected set shows its description. Loading a member of a " +
        "set opens the whole set: < and > in the toolbar (or PageUp / PageDown) step through it. " +
        "Import... reads pasted text and " +
        "Export... gives the text to copy. Open file... and Save file... read and write files, Export SVG " +
        "saves the picture.",
    "If the arrangement has changed since it was loaded or saved, ArrView asks whether to save it before " +
        "it replaces it. On the desktop, closing the app asks too. An unapplied edit in a widget is never " +
        "saved, only discarded.",
)

private const val VISUALIZATION_INTRO = "ArrView draws an arrangement as a plane embedding of one of its graphs. " +
    "The figures show the three graphs of the start example n6-digonfree-nonr-8-triangles over its model 2 curves."

private val VISUALIZATION_TEXT = listOf(
    "Model 1 (legacy) is the iterated weighted Tutte embedding of the primal graph; each circle is a B-spline " +
        "through its crossings. The Tutte embedding needs a 3-connected graph, so model 1 shows intersecting " +
        "arrangements (every two circles cross) of at least 3 circles only. The two arcs of a digon are one edge " +
        "there: the two circles touch along a short piece (blue dot).",
    "Model 2 (default) is the same on the primal-dual graph; each circle is a B-spline through its crossing and " +
        "arc vertices. It works for every connected arrangement and draws digons as regions.",
    "Iterated: the first embedding has equal edge weights; then each edge weight grows with the edge's length " +
        "and the areas of its two faces, and the graph is embedded again, up to Max. iterations times (Settings). " +
        "The outer face is a regular polygon; by default it is the face with the most symmetries.",
    "References:",
)

private val VISUALIZATION_REFERENCES = PAPERS.take(2) +
    "The information page of the pseudocircles website (models and formats; see About)."

private val WIDGETS_TEXT = listOf(
    "Widgets are columns next to the canvas (below it on a narrow window). The toolbar and the View menu " +
        "show and hide them. By default at most ${AppState.DEFAULT_MAX_WIDGETS} are open (Settings widget, Max. widgets); " +
        "opening another one closes the one opened first. The open widgets, the divider positions and the " +
        "settings are remembered for the next start.",
    "Properties: n, the numbers of crossings and faces, the p-vector (pk faces with k sides, p2 digons " +
        "and p3 triangles), the pairs of circles that do not cross, the classes and how many triples of " +
        "pairwise crossing circles are Krupp or NonKrupp. Intersecting: every two circles cross. " +
        "Digon-free: no digon. Pseudo great circles: intersecting and every triple a Krupp. Cylindrical: " +
        "two faces are separated by every circle.",
    "Encoding: the sequences, the model 1 and the model 2 sparse6 string (see Formats); the (i) next to each " +
        "says what it encodes. Edit one and press Apply; ArrView checks the conditions of Linhart and Ortner " +
        "and says why invalid input is rejected. Revert brings back the current text, Copy puts a sparse6 " +
        "string on the clipboard. Model 1 exists only for intersecting arrangements.",
    "Intersections: the intersection graph, the circles as vertices 1 to n in their colours, an edge where two circles cross. " +
        "A dashed edge: the two circles do not cross but can create a digon. A yellow stripe marks every pair " +
        "that can create or collapse a digon, twice as strong if there are several ways; click it to do it " +
        "(a menu lists the faces if there are several, a face with several ways opens a submenu). Below the " +
        "graph: the possible digon creates and collapses, e.g. possible digon creates (3): 1 3 (x2), 2 4, 2 5. " +
        "(x2) marks a pair with 2 ways. The entries are links: a click does what a click on the pair in the " +
        "graph does, and pointing at one highlights the pair and its faces.",
    "Triangles: the triangle hypergraph, the same vertices, and a transparent triangle for every triple of circles that " +
        "bounds a triangle face, so overlapping triangles add up to darker areas. A triple with two or more " +
        "triangle faces is twice as strong. Click a triangle to flip it; where several are under the mouse, " +
        "a menu lists them with their faces (several triples: a submenu per triple). A triple is always written " +
        "ascending, as in the menu title Flip a triangle of 1 2 3; the entries are the faces in their own " +
        "order around, e.g. 1 3 2. Below it: the possible " +
        "triangle flips, e.g. possible triangle flips (3): 1 2 3 (x2), 1 2 4, 3 4 6; click an entry to flip it " +
        "(several triangles: a menu); pointing at one highlights the triple and its faces.",
    "Settings: Display, Widgets and Layout. Display: the colour scheme of the circles (" +
        ColorScheme.entries.joinToString(", ") { if (it == ColorScheme.DEFAULT) "${it.label} (default)" else it.label } +
        "; Standard is a fixed list of 12 colours, rainbow and rainbow2 are computed from n) and the line width (default ${AppState.DEFAULT_LINE_WIDTH.toInt()} px, also in Export SVG). The " +
        "scheme colours the curves, labels, SVG and the vertices of the graph views. Widgets: Max. widgets " +
        "(default ${AppState.DEFAULT_MAX_WIDGETS}). Layout, the drawing only: Visualization (default " +
        "${DrawModel.MODEL2.option}; see Help > Visualization) and Max. iterations (default ${LayoutSettings().maxIterations}). Reset to defaults resets " +
        "everything in the widget.",
)

private val FORMATS_TEXT = listOf(
    "Sequences: {1: [2, -2, 3, -3], 2: [...], ...}. List i has the crossings of circle i in the order of " +
        "its orientation: +j where circle j crosses from the right of circle i to its left, -j where it " +
        "crosses from left to right. So +j in list i and -i in list j are the same crossing.",
    "Model 1 sparse6: the dual graph of an intersecting arrangement (a vertex per face, an edge per arc " +
        "between two crossings) in the sparse6 format of nauty.",
    "Model 2 sparse6: the primal-dual graph (a vertex per crossing, arc and face, an edge from every arc " +
        "to its two crossings and two faces) plus a leaf at every face vertex, so faces can be told apart. It works for every " +
        "arrangement. The older encoding with marker hubs is read too.",
    "Both are the formats of the pseudocircles website (see About). ArrView may number the vertices " +
        "differently, so its strings describe the same arrangements but need not be equal to the website's.",
    "Save file writes JSON {\"name\": ..., \"sequences\": [[...], ...]} (list i for circle i) or the " +
        "sequences as text. Open file and Import read both and sparse6 lines; model 1 is tried first.",
    "A file or text with several arrangements (one sparse6 string per line, several sequences one after " +
        "the other, or a JSON list of the objects) is loaded as a set, like an example set: < and > in the " +
        "toolbar (or PageUp / PageDown) step through it, and Save file writes all of them.",
)

/** The keyboard shortcuts page: key and action (doc/USER_GUIDE.md has the same list). */
private val SHORTCUTS = listOf(
    "Esc" to "close the open menu",
    "Ctrl+Z" to "undo",
    "Ctrl+Shift+Z or Ctrl+Y" to "redo",
    "+ / -" to "zoom in / out",
    "Home or 0" to "fit",
    "PageUp or , / PageDown or ." to "previous / next arrangement of a file or example set",
    "Mouse" to "click a triangle to flip it, a digon to collapse it, a face to create a digon, right-click a face " +
        "(long press on touch screens) for its menu, drag to pan, wheel or pinch to zoom",
)

private const val SHORTCUTS_NOTE = "On a Mac, Cmd works like Ctrl and Ctrl+click like a right-click. The keys " +
    "do nothing while you type in a field or a menu is open (except Esc)."
