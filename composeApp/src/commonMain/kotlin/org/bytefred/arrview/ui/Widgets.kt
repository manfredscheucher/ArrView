package org.bytefred.arrview.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import org.bytefred.arrview.core.DrawModel
import org.bytefred.arrview.core.TextFormat
import org.bytefred.arrview.core.crosses
import org.bytefred.arrview.core.intersectionGraph
import org.bytefred.arrview.core.isCylindrical
import org.bytefred.arrview.core.isDigonFree
import org.bytefred.arrview.core.isGreat
import org.bytefred.arrview.core.kruppTriples
import org.bytefred.arrview.core.nonIntersectingPairCount
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** The content of a widget (without scrolling; the caller scrolls). */
@Composable
fun WidgetContent(state: AppState, w: Widget) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when (w) {
            Widget.PROPERTIES -> PropertiesWidget(state)
            Widget.ENCODING -> EncodingWidget(state)
            Widget.INTERSECTIONS -> IntersectionsWidget(state)
            Widget.TRIANGLES -> TrianglesWidget(state)
            Widget.SETTINGS -> SettingsWidget(state)
        }
    }
}

@Composable
private fun Heading(text: String) = Text(text, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleSmall)

@Composable
private fun ValueRow(label: String, value: String) = Row(Modifier.padding(vertical = 1.dp)) {
    Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    Text(value, style = MaterialTheme.typography.bodyMedium)
}

/** The numbers and classes of the arrangement; p3 and p2 large on top. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PropertiesWidget(state: AppState) {
    val arr = state.arrangement
    val (krupp, nonKrupp) = remember(arr) { arr.kruppTriples }
    val great = remember(arr) { arr.isGreat }
    val cylindrical = remember(arr) { arr.isCylindrical }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BigNumber("p3", "triangles", arr.triangleCount, Modifier.weight(1f))
        BigNumber("p2", "digons", arr.digonCount, Modifier.weight(1f))
    }
    ValueRow("n", "${arr.n}")
    ValueRow("crossings", "${arr.crossingCount}")
    ValueRow("faces", "${arr.faceCount}")
    ValueRow("non-intersecting pairs", "${arr.nonIntersectingPairCount}")
    ValueRow("Krupp triples", "$krupp")
    ValueRow("NonKrupp triples", "$nonKrupp")
    Heading("p-vector")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        for ((k, count) in arr.pVector.withIndex()) if (k >= 2) Text("p$k = $count", style = MonoStyle)
    }
    Heading("Classes")
    // every arrangement here is connected (Linhart-Ortner Def. 2.4 (iv) rejects the others)
    ValueRow("connected", "yes")
    ValueRow("intersecting", yesNo(arr.isIntersecting))
    ValueRow("digon-free", yesNo(arr.isDigonFree))
    ValueRow("pseudo great circles", yesNo(great))
    ValueRow("cylindrical", yesNo(cylindrical))
}

private fun yesNo(b: Boolean) = if (b) "yes" else "no"

/** A count in large digits with its name below (p3 and p2). */
@Composable
private fun BigNumber(symbol: String, name: String, value: Int, modifier: Modifier) {
    Column(
        modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text("$value", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text("$symbol  $name", style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * The three texts of the arrangement, editable: the sequences (one line per circle), model 1 sparse6
 * (if intersecting) and model 2 sparse6, each with an (i) saying what it encodes; the sparse6 strings
 * with Copy.
 */
@Composable
private fun EncodingWidget(state: AppState) {
    val arr = state.arrangement
    EncodingHeading(TextFormat.SEQUENCES, SEQUENCES_INFO)
    DraftEditor(state, TextFormat.SEQUENCES, wrap = false)
    EncodingHeading(TextFormat.MODEL1, MODEL1_INFO)
    if (!arr.isIntersecting) {
        val (c, d) = remember(arr) { (0 until arr.n).flatMap { c -> (c + 1 until arr.n).map { c to it } }.first { (c, d) -> !arr.crosses(c, d) } }
        Text(
            "Only for intersecting arrangements; circles ${c + 1} and ${d + 1} do not cross.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
    // an edit made while it was intersecting stays until applied or reverted
    if (arr.isIntersecting || TextFormat.MODEL1 in state.drafts) DraftEditor(state, TextFormat.MODEL1, wrap = true, copy = true)
    EncodingHeading(TextFormat.MODEL2, MODEL2_INFO)
    DraftEditor(state, TextFormat.MODEL2, wrap = true, copy = true)
}

/** The heading of one encoding with its (i). */
@Composable
private fun EncodingHeading(format: TextFormat, info: String) = Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(format.label, style = MaterialTheme.typography.titleSmall)
    InfoButton(info, format.name)
}

/** What the sequences encode (the (i) in the Encoding widget); the sign as in Arrangement and the Formats page. */
internal const val SEQUENCES_INFO = "For each circle, the order of its crossings with the other circles along its " +
    "orientation: +j where circle j crosses from the right of this circle to its left, -j the other way; so +j in " +
    "list i and -i in list j are the same crossing."

internal const val MODEL1_INFO = "The dual graph of the arrangement: a vertex per cell, an edge per arc, encoded as sparse6 " +
    "(website format mod1s6 / enc, see the information page of the pseudocircles website)."

internal const val MODEL2_INFO = "The primal-dual graph: a vertex per crossing, arc and cell, an edge where an arc meets a " +
    "crossing or a cell, and a leaf at every cell vertex; sparse6 (website format mod2s6)."

/**
 * The [format] text of the arrangement, editable, with Apply and Revert (PoCoView's Data widget
 * coordinates). The draft lives in the state, so closing the widget does not lose it. [wrap]:
 * long lines wrap, else they scroll sideways (one line per circle).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DraftEditor(state: AppState, format: TextFormat, wrap: Boolean, copy: Boolean = false) {
    val draft = state.drafts[format]
    val text = draft ?: state.text(format)
    val tag = format.name
    if (wrap) {
        OutlinedTextField(
            text, onValueChange = { state.editDraft(format, it) }, textStyle = MonoStyle,
            modifier = Modifier.fillMaxWidth().textFocus(state).testTag("editor-$tag"),
        )
    } else {
        val hScroll = rememberScrollState()
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val width = maxWidth
            ScrollbarBox(null, hScroll = hScroll) {
                // unbounded width inside the scroll: the lines do not wrap
                Box(Modifier.horizontalScroll(hScroll).padding(bottom = 8.dp)) {
                    OutlinedTextField(
                        text, onValueChange = { state.editDraft(format, it) }, textStyle = MonoStyle,
                        modifier = Modifier.widthIn(min = width).textFocus(state).testTag("editor-$tag"),
                    )
                }
            }
        }
    }
    val clipboard = LocalClipboardManager.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { state.applyDraft(format) }, Modifier.testTag("apply-$tag"), enabled = draft != null) { Text("Apply") }
        OutlinedButton(onClick = { state.revertDraft(format) }, Modifier.testTag("revert-$tag"), enabled = draft != null) { Text("Revert") }
        if (copy) OutlinedButton(onClick = { clipboard.setText(AnnotatedString(text)); state.showInfo("Copied") }) { Text("Copy") }
    }
}

/** The vertices 1..n of the graph views on a circle in a [width] x [height] canvas: vertex 1 on top, then clockwise. */
internal class VertexCircle(val n: Int, width: Float, height: Float, val label: Float) {
    val radius = min(width, height) / 2 - label
    val center = Offset(width / 2, height / 2)

    fun at(c: Int, r: Float = radius): Offset {
        val a = -PI / 2 + 2 * PI * c / n
        return center + Offset((r * cos(a)).toFloat(), (r * sin(a)).toFloat())
    }
}

/** The vertex circle of a graph view canvas of [size] pixels (labels need [GRAPH_LABEL] around it). */
internal fun Density.vertexCircle(n: Int, size: Size) = VertexCircle(n, size.width, size.height, GRAPH_LABEL.toPx())

/** The vertices as dots with their labels in the circle colours. */
private fun DrawScope.drawVertices(circle: VertexCircle, measurer: TextMeasurer, scheme: ColorScheme) {
    for (c in 0 until circle.n) {
        val color = scheme.color(c, circle.n)
        drawCircle(color, 4.dp.toPx(), circle.at(c))
        val layout = measurer.measure("${c + 1}", TextStyle(color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold))
        drawText(layout, topLeft = circle.at(c, circle.radius + circle.label / 2) - Offset(layout.size.width / 2f, layout.size.height / 2f))
    }
}

/** Distance from [p] to the segment [a]-[b]. */
internal fun segmentDistance(p: Offset, a: Offset, b: Offset): Float {
    val ab = b - a
    val len2 = ab.x * ab.x + ab.y * ab.y
    val t = if (len2 > 0f) (((p - a).x * ab.x + (p - a).y * ab.y) / len2).coerceIn(0f, 1f) else 0f
    return (p - (a + ab * t)).getDistance()
}

/**
 * K_n on a circle, labelled 1..n in the circle colours; an edge where two circles cross, dashed
 * where they do not cross but can create a digon. A yellow stripe behind every pair that can
 * create or collapse a digon (twice as strong if there are several ways); a click on it does it,
 * or opens a menu of the cells.
 */
@Composable
private fun IntersectionsWidget(state: AppState) {
    val arr = state.arrangement
    val edges = remember(arr) { arr.intersectionGraph() }
    val actions = state.actions
    val striped = actions.actionPairs
    val measurer = rememberTextMeasurer()
    val edgeColor = MaterialTheme.colorScheme.outline
    var menu by remember(arr) { mutableStateOf<Pair<Pair<Int, Int>, Offset>?>(null) }
    Text("${edges.size} of ${arr.n * (arr.n - 1) / 2} pairs cross", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("intersection-graph-count"))
    Box {
        Canvas(
            Modifier.size(GRAPH_SIZE).testTag("intersection-graph").pointerInput(actions) {
                detectTapGestures { pos ->
                    val circle = vertexCircle(arr.n, size.toSize())
                    val pair = striped.map { (i, j) -> (i to j) to segmentDistance(pos, circle.at(i), circle.at(j)) }
                        .filter { it.second <= PAIR_HIT.toPx() }.minByOrNull { it.second }?.first ?: return@detectTapGestures
                    if (state.actOnPair(pair.first, pair.second)) menu = pair to pos
                }
            },
        ) {
            val circle = vertexCircle(arr.n, size)
            val stripe = STRIPE_WIDTH.toPx()
            for ((i, j) in striped) {
                // twice as strong for several ways: drawn twice
                repeat(if (actions.pairActionCount(i, j) >= 2) 2 else 1) { drawLine(STRIPE_COLOR, circle.at(i), circle.at(j), strokeWidth = stripe) }
            }
            // the pair of a hovered list entry
            state.graphHighlight?.takeIf { it.size == 2 }?.let { (i, j) ->
                if (i < arr.n && j < arr.n) drawLine(HIGHLIGHT_SIDE, circle.at(i), circle.at(j), strokeWidth = stripe)
            }
            for ((c, d) in edges) drawLine(edgeColor, circle.at(c), circle.at(d), strokeWidth = 1.5f)
            val dashes = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))
            for ((i, j) in striped) if (actions.createsOf(i, j).isNotEmpty()) {
                drawLine(edgeColor, circle.at(i), circle.at(j), strokeWidth = 1.5f, pathEffect = dashes)
            }
            drawVertices(circle, measurer, state.colorScheme)
        }
        menu?.let { (pair, at) ->
            val (i, j) = pair
            val entries = remember(arr, pair) { state.pairEntries(i, j) }
            ActionMenu(state, at, state.pairHeading(i, j), entries) { menu = null }
        }
    }
    val pairMenu: @Composable (List<Int>, Offset, () -> Unit) -> Unit = { c, at, close ->
        val (i, j) = c
        val entries = remember(arr, c) { state.pairEntries(i, j) }
        ActionMenu(state, at, state.pairHeading(i, j), entries, close)
    }
    ActionList(state, "digon creates", actions.createPairs, "possible-creates", { (i, j) -> state.actOnPair(i, j) }, pairMenu)
    ActionList(state, "digon collapses", actions.collapsePairs, "possible-collapses", { (i, j) -> state.actOnPair(i, j) }, pairMenu)
}

/**
 * A click on pair [i] < [j] (a stripe of the intersection graph or a list entry): a single create
 * or collapse is done; true if there are several ways, then the caller opens the pair's menu.
 */
private fun AppState.actOnPair(i: Int, j: Int): Boolean {
    val creates = actions.createsOf(i, j)
    val collapses = actions.collapsesOf(i, j)
    when {
        creates.size + collapses.size >= 2 -> return true
        creates.size == 1 -> createDigon(creates.single())
        collapses.size == 1 -> collapseDigon(collapses.single())
    }
    return false
}

/** The cells of the actions of a pair or a triple [c] (highlighted on the canvas while its list entry is hovered). */
private fun AppState.cellsOf(c: List<Int>): Set<Int> = when (c.size) {
    2 -> (actions.createsOf(c[0], c[1]).map { it.face } + actions.collapsesOf(c[0], c[1])).toSet()
    else -> actions.triples[c].orEmpty().toSet()
}

/**
 * A line below a graph view listing the possible actions: "possible <what> (k): " and the entries
 * ([possibleList]), each a link. A click does what a click on the pair or triple in the graph does
 * ([act] returns true when the menu of [c] has to open, shown by [menu] below the entry); pointing
 * at an entry highlights it in the graph and its cells on the canvas.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionList(
    state: AppState,
    what: String,
    entries: List<Pair<List<Int>, Int>>,
    tag: String,
    act: (List<Int>) -> Boolean,
    menu: @Composable (List<Int>, Offset, () -> Unit) -> Unit,
) {
    val style = MaterialTheme.typography.bodyMedium
    // not a remember key: the tap handlers keep the state object they saw first
    var open by remember { mutableStateOf<List<Int>?>(null) }
    LaunchedEffect(state.arrangement) { open = null }
    val text = possibleList(what, entries)
    FlowRow(Modifier.testTag(tag).semantics { this.text = AnnotatedString(text) }) {
        Text("possible $what (${entries.size}): ", style = style)
        if (entries.isEmpty()) Text("none", style = style)
        for ((k, entry) in entries.withIndex()) key(entry.first) {
            val (c, m) = entry
            // the comma stays with its entry when the line wraps
            Row {
                val label = circlesLabel(c) + if (m >= 2) " (x$m)" else ""
                val entryTag = "$tag-${c.joinToString("-") { "${it + 1}" }}"
                ActionEntry(state, c, label, entryTag, open == c, onClick = { if (act(c)) open = c }) { at ->
                    menu(c, at) { open = null }
                }
                if (k < entries.size - 1) Text(", ", style = style)
            }
        }
    }
}

/**
 * One entry of an [ActionList]: a link for the pair or triple [c]. Pointing at it highlights [c] in
 * the graph and its cells on the canvas; [menu] shows below it while [menuOpen].
 */
@Composable
private fun ActionEntry(
    state: AppState,
    c: List<Int>,
    label: String,
    tag: String,
    menuOpen: Boolean,
    onClick: () -> Unit,
    menu: @Composable (Offset) -> Unit,
) {
    val cells = Highlight(state.cellsOf(c))
    val click by rememberUpdatedState(onClick)
    val opened by rememberUpdatedState(menuOpen)
    var height by remember { mutableStateOf(0) }
    // a widget closed or scrolled away under the pointer gets no Exit
    DisposableEffect(c) {
        onDispose {
            if (state.graphHighlight == c) state.graphHighlight = null
            if (state.menuHighlight == cells) state.menuHighlight = null
        }
    }
    Box {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium.copy(textDecoration = TextDecoration.Underline),
            color = LINK_COLOR,
            modifier = Modifier
                .testTag(tag)
                .onSizeChanged { height = it.height }
                .pointerInput(c) {
                    awaitPointerEventScope {
                        while (true) {
                            when (awaitPointerEvent().type) {
                                PointerEventType.Enter -> {
                                    state.graphHighlight = c
                                    state.menuHighlight = cells
                                }
                                PointerEventType.Exit -> {
                                    if (state.graphHighlight == c) state.graphHighlight = null
                                    if (!opened && state.menuHighlight == cells) state.menuHighlight = null
                                }
                            }
                        }
                    }
                }
                // a tap, not clickable: the entry must not take the keyboard focus (it may vanish with
                // the click, and the shortcuts such as Ctrl+Z need the focus in the app)
                .pointerInput(c) { detectTapGestures { click() } }
                .pointerHoverIcon(PointerIcon.Hand)
                .semantics {
                    role = Role.Button
                    onClick { click(); true }
                },
        )
        if (menuOpen) menu(Offset(0f, height.toFloat()))
    }
}

/**
 * "possible <what> (k): 1 3 (x2), 2 4": the pairs or triples (0-based [entries] with their numbers
 * of options, already sorted) written 1-based, "(xm)" for m >= 2 options; "none" if empty.
 */
internal fun possibleList(what: String, entries: List<Pair<List<Int>, Int>>): String =
    "possible $what (${entries.size}): " +
        if (entries.isEmpty()) "none" else entries.joinToString(", ") { (c, m) -> circlesLabel(c) + if (m >= 2) " (x$m)" else "" }

/**
 * The triangle cells as a 3-uniform hypergraph on the vertices of the intersection graph: a
 * transparent triangle per triple of circles that bounds a triangle cell (overlaps add up), twice
 * as strong for a triple with several triangle cells. A click flips the triangle under the
 * pointer, or opens a menu if there are several.
 */
@Composable
private fun TrianglesWidget(state: AppState) {
    val arr = state.arrangement
    val actions = state.actions
    val triples = actions.triples
    val measurer = rememberTextMeasurer()
    var menu by remember(arr) { mutableStateOf<Pair<List<List<Int>>, Offset>?>(null) }
    Text(plural(arr.triangleCount, "triangle") + " on " + plural(triples.size, "triple"), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("triangle-hypergraph-count"))
    Box {
        Canvas(
            Modifier.size(GRAPH_SIZE).testTag("triangle-hypergraph").pointerInput(actions) {
                detectTapGestures { pos ->
                    val circle = vertexCircle(arr.n, size.toSize())
                    val hits = triples.keys.sortedWith(compareBy({ it[0] }, { it[1] }, { it[2] })).filter { t -> inTriangle(pos, circle.at(t[0]), circle.at(t[1]), circle.at(t[2])) }
                    if (state.actOnTriples(hits)) menu = hits to pos
                }
            },
        ) {
            val circle = vertexCircle(arr.n, size)
            for ((t, faces) in triples) {
                val path = Path().apply {
                    val (a, b, c) = t.map { circle.at(it) }
                    moveTo(a.x, a.y)
                    lineTo(b.x, b.y)
                    lineTo(c.x, c.y)
                    close()
                }
                repeat(if (faces.size >= 2) 2 else 1) {
                    drawPath(path, HYPEREDGE_FILL)
                    drawPath(path, HYPEREDGE_LINE, style = Stroke(1f))
                }
                // the triple of a hovered list entry
                if (state.graphHighlight == t) drawPath(path, HIGHLIGHT_SIDE, style = Stroke(3.dp.toPx()))
            }
            drawVertices(circle, measurer, state.colorScheme)
        }
        menu?.let { (hits, at) ->
            val entries = remember(arr, hits) { state.triangleEntries(hits.associateWith { triples[it].orEmpty() }) }
            ActionMenu(state, at, triangleHeading(hits), entries) { menu = null }
        }
    }
    ActionList(state, "triangle flips", actions.triangleTriples, "possible-flips", { t -> state.actOnTriples(listOf(t)) }) { t, at, close ->
        val entries = remember(arr, t) { state.triangleEntries(mapOf(t to triples[t].orEmpty())) }
        ActionMenu(state, at, triangleHeading(listOf(t)), entries, close)
    }
}

/**
 * A click on the triples [hits] (under the pointer in the hypergraph, or a list entry): one
 * triple with one triangle flips it; true if there are several, then the caller opens the menu.
 */
private fun AppState.actOnTriples(hits: List<List<Int>>): Boolean {
    val single = hits.singleOrNull()?.let { actions.triples[it]?.singleOrNull() }
    when {
        single != null -> flipTriangle(single)
        hits.isNotEmpty() -> return true
    }
    return false
}

private fun plural(k: Int, word: String) = if (k == 1) "1 $word" else "$k ${word}s"

/** Whether [p] is inside the triangle [a] [b] [c] (either orientation, boundary included). */
internal fun inTriangle(p: Offset, a: Offset, b: Offset, c: Offset): Boolean {
    fun side(u: Offset, v: Offset) = (v.x - u.x) * (p.y - u.y) - (v.y - u.y) * (p.x - u.x)
    val s1 = side(a, b)
    val s2 = side(b, c)
    val s3 = side(c, a)
    return (s1 >= 0 && s2 >= 0 && s3 >= 0) || (s1 <= 0 && s2 <= 0 && s3 <= 0)
}

/** Room for the vertex labels around the graph views. */
internal val GRAPH_LABEL = 22.dp

/** Pixels around an edge of the intersection graph that hit its pair. */
private val PAIR_HIT = 8.dp

private val STRIPE_WIDTH = 10.dp

/** The stripe behind a pair that can create or collapse a digon (drawn twice for several ways). */
internal val STRIPE_COLOR = Color(0x66FFD600)

/** A triangle of the hypergraph (drawn twice for a triple with several triangle cells). */
internal val HYPEREDGE_FILL = Color(0x2EFB8C00)
private val HYPEREDGE_LINE = Color(0x66FB8C00)

private val GRAPH_SIZE = 240.dp

/**
 * Display (colour scheme, line width), Widgets (how many are open) and Layout (visualization, max.
 * iterations), each under a heading as PoCoView's widget subgroups, and one Reset to defaults for
 * all of it. The explanations are behind (i) icons.
 */
@Composable
private fun SettingsWidget(state: AppState) {
    val settings = state.layoutSettings
    val openDialog = LocalOpenDialog.current
    Text("Display", style = MaterialTheme.typography.titleSmall)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Colour scheme", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        SelectField(null, state.colorScheme.label, ColorScheme.entries, { it.label }, Modifier.width(150.dp).testTag("color-scheme")) {
            state.colorScheme = it
        }
    }
    Text("Line width: ${oneDecimal(state.lineWidth)} px", style = MaterialTheme.typography.bodyMedium)
    Slider(state.lineWidth, { state.lineWidth = it }, valueRange = AppState.LINE_WIDTH_RANGE, modifier = Modifier.testTag("line-width"))

    Spacer(Modifier.height(12.dp))
    Text("Widgets", style = MaterialTheme.typography.titleSmall)
    // PoCoView's Max. widgets row
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Max. widgets: ${state.maxColumns}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = { state.changeMaxColumns(state.maxColumns - 1) }, enabled = state.maxColumns > 1, modifier = Modifier.testTag("max-widgets-minus")) { Text("-") }
        TextButton(onClick = { state.changeMaxColumns(state.maxColumns + 1) }, enabled = state.maxColumns < Widget.entries.size, modifier = Modifier.testTag("max-widgets-plus")) { Text("+") }
    }

    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Layout", style = MaterialTheme.typography.titleSmall)
        InfoButton(LAYOUT_INFO, "layout")
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Visualization", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        // the Help page on the three graphs and the two models
        InfoIcon("visualization") {
            state.helpPage = HelpPage.VISUALIZATION
            openDialog(DialogKind.HELP)
        }
    }
    SelectField(null, state.preferredModel.option, DrawModel.entries, { it.option }, Modifier.fillMaxWidth().testTag("visualization")) {
        state.chooseModel(it)
    }
    fun iterations(n: Int) = state.changeLayoutSettings(state.layoutSettings.copy(maxIterations = n))
    SettingRow("Max. iterations") {
        CompactNumberField(
            state, settings.maxIterations.toString(), { it.toIntOrNull()?.takeIf { n -> n >= 1 } }, "at least 1",
            ITERATION_CHARS, "max-iterations",
            Steppers({ iterations(settings.maxIterations - 1) }, settings.maxIterations > 1, { iterations(settings.maxIterations + 1) }, true),
        ) { n -> if (n != null) iterations(n) }
    }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = state::resetSettings) { Text("Reset to defaults") }
}

/** A setting: its name on the left, the control on the right (as the colour scheme row). */
@Composable
private fun SettingRow(label: String, control: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 2.dp)) {
        Text(label, Modifier.weight(1f).padding(top = 10.dp), style = MaterialTheme.typography.bodyMedium)
        control()
    }
}

/** Max. iterations has no upper bound; the field is wide enough for 6 digits (longer text scrolls). */
private const val ITERATION_CHARS = 6

/** The (i) of the Layout group of the Settings widget. */
internal const val LAYOUT_INFO = "Parameters of the drawing only; they do not change the arrangement. The drawing is the " +
    "iterated weighted Tutte embedding of Felsner and Scheucher (Arrangements of Pseudocircles: Triangles and Drawings, " +
    "DCG 2021, Section 4; On Circularizability, DCG 2020)."

/** [v] with one decimal, the same on the JVM and the web (where 3.0 prints as "3"). */
internal fun oneDecimal(v: Float): String {
    val tenths = (v * 10).roundToInt()
    return "${tenths / 10}.${tenths % 10}"
}

/** A real dropdown: read-only text field with an arrow; click opens the list (PoCoView's). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SelectField(
    label: String?, current: String, items: List<T>, itemLabel: (T) -> String, modifier: Modifier = Modifier,
    onSelect: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(open, onExpandedChange = { open = it }, modifier = modifier) {
        OutlinedTextField(
            current, onValueChange = {}, readOnly = true, singleLine = true,
            label = label?.let { { Text(it) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
            textStyle = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(open, onDismissRequest = { open = false }) {
            for (it in items) DropdownMenuItem(text = { Text(itemLabel(it)) }, onClick = { onSelect(it); open = false })
        }
    }
}

/** Counts focused text fields in [AppState.textFieldsFocused], so shortcuts do not fire while typing. */
fun Modifier.textFocus(state: AppState): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { if (focused) state.textFieldsFocused-- } }
    onFocusChanged {
        if (it.isFocused != focused) {
            focused = it.isFocused
            state.textFieldsFocused += if (focused) 1 else -1
        }
    }
}
