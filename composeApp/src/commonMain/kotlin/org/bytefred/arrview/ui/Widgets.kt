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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.bytefred.arrview.core.LayoutSettings
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
import kotlin.math.sin

/** The content of a widget (without scrolling; the caller scrolls). */
@Composable
fun WidgetContent(state: AppState, w: Widget) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when (w) {
            Widget.PROPERTIES -> PropertiesWidget(state)
            Widget.SEQUENCES -> SequencesWidget(state)
            Widget.ENCODING -> EncodingWidget(state)
            Widget.INTERSECTION_GRAPH -> IntersectionGraphWidget(state)
            Widget.TRIANGLE_HYPERGRAPH -> TriangleHypergraphWidget(state)
            Widget.LAYOUT -> LayoutWidget(state)
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

/** The intersection sequences, one line per circle, editable. */
@Composable
private fun SequencesWidget(state: AppState) {
    DraftEditor(state, TextFormat.SEQUENCES, wrap = false)
}

/** Model 1 (if intersecting) and model 2 sparse6, editable, with Copy. */
@Composable
private fun EncodingWidget(state: AppState) {
    val arr = state.arrangement
    Heading(TextFormat.MODEL1.label)
    if (!arr.isIntersecting) {
        val (c, d) = remember(arr) { (0 until arr.n).flatMap { c -> (c + 1 until arr.n).map { c to it } }.first { (c, d) -> !arr.crosses(c, d) } }
        Text(
            "Only for intersecting arrangements; circles ${c + 1} and ${d + 1} do not cross.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
    // an edit made while it was intersecting stays until applied or reverted
    if (arr.isIntersecting || TextFormat.MODEL1 in state.drafts) DraftEditor(state, TextFormat.MODEL1, wrap = true, copy = true)
    Heading(TextFormat.MODEL2.label)
    DraftEditor(state, TextFormat.MODEL2, wrap = true, copy = true)
}

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
private fun DrawScope.drawVertices(circle: VertexCircle, measurer: TextMeasurer) {
    for (c in 0 until circle.n) {
        drawCircle(circleColor(c), 4.dp.toPx(), circle.at(c))
        val layout = measurer.measure("${c + 1}", TextStyle(color = circleColor(c), fontSize = 11.sp, fontWeight = FontWeight.Bold))
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
private fun IntersectionGraphWidget(state: AppState) {
    val arr = state.arrangement
    val edges = remember(arr) { arr.intersectionGraph() }
    val actions = state.actions
    val striped = remember(actions) {
        (0 until arr.n).flatMap { i -> (i + 1 until arr.n).map { j -> i to j } }.filter { (i, j) -> actions.pairActionCount(i, j) > 0 }
    }
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
                    val (i, j) = pair
                    val creates = actions.createsOf(i, j)
                    val collapses = actions.collapsesOf(i, j)
                    when {
                        creates.size + collapses.size >= 2 -> menu = pair to pos
                        creates.size == 1 -> state.createDigon(creates.single())
                        collapses.size == 1 -> state.collapseDigon(collapses.single())
                    }
                }
            },
        ) {
            val circle = vertexCircle(arr.n, size)
            val stripe = STRIPE_WIDTH.toPx()
            for ((i, j) in striped) {
                // twice as strong for several ways: drawn twice
                repeat(if (actions.pairActionCount(i, j) >= 2) 2 else 1) { drawLine(STRIPE_COLOR, circle.at(i), circle.at(j), strokeWidth = stripe) }
            }
            for ((c, d) in edges) drawLine(edgeColor, circle.at(c), circle.at(d), strokeWidth = 1.5f)
            val dashes = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))
            for ((i, j) in striped) if (actions.createsOf(i, j).isNotEmpty()) {
                drawLine(edgeColor, circle.at(i), circle.at(j), strokeWidth = 1.5f, pathEffect = dashes)
            }
            drawVertices(circle, measurer)
        }
        menu?.let { (pair, at) ->
            val (i, j) = pair
            ActionMenu(state, at, state.pairHeading(i, j), state.pairEntries(i, j)) { menu = null }
        }
    }
}

/**
 * The triangle cells as a 3-uniform hypergraph on the vertices of the intersection graph: a
 * transparent triangle per triple of circles that bounds a triangle cell (overlaps add up), twice
 * as strong for a triple with several triangle cells. A click flips the triangle under the
 * pointer, or opens a menu if there are several.
 */
@Composable
private fun TriangleHypergraphWidget(state: AppState) {
    val arr = state.arrangement
    val actions = state.actions
    val triples = actions.triples
    val measurer = rememberTextMeasurer()
    var menu by remember(arr) { mutableStateOf<Pair<List<List<Int>>, Offset>?>(null) }
    Text("${arr.triangleCount} triangles on ${triples.size} triples", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("triangle-hypergraph-count"))
    Box {
        Canvas(
            Modifier.size(GRAPH_SIZE).testTag("triangle-hypergraph").pointerInput(actions) {
                detectTapGestures { pos ->
                    val circle = vertexCircle(arr.n, size.toSize())
                    val hits = triples.keys.filter { t -> inTriangle(pos, circle.at(t[0]), circle.at(t[1]), circle.at(t[2])) }
                    val single = hits.singleOrNull()?.let { triples.getValue(it).singleOrNull() }
                    when {
                        single != null -> state.flipTriangle(single)
                        hits.isNotEmpty() -> menu = hits to pos
                    }
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
            }
            drawVertices(circle, measurer)
        }
        menu?.let { (hits, at) ->
            ActionMenu(state, at, "Flip a triangle", hits.flatMap { t -> state.triangleEntries(t, triples[t].orEmpty()) }) { menu = null }
        }
    }
}

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

/** Model and the settings of the iterated Tutte embedding. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LayoutWidget(state: AppState) {
    val settings = state.layoutSettings
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) { ModelChips(state) }
    ParsedField(
        state, "Max. iterations", settings.maxIterations.toString(), { it.toIntOrNull()?.takeIf { n -> n >= 1 } },
        "at least 1", Modifier.width(160.dp).testTag("max-iterations"),
    ) { n -> if (n != null) state.changeLayoutSettings(state.layoutSettings.copy(maxIterations = n)) }
    ParsedField(
        state, "eps", epsText(settings.eps), { it.toDoubleOrNull()?.takeIf { e -> e.isFinite() && e > 0.0 } },
        "a positive number", Modifier.width(160.dp).testTag("eps"),
    ) { e -> if (e != null) state.changeLayoutSettings(state.layoutSettings.copy(eps = e)) }
    OutlinedButton(onClick = { state.changeLayoutSettings(LayoutSettings()) }, enabled = settings != LayoutSettings()) {
        Text("Reset to defaults")
    }
}

/**
 * A text field for a value: [onText] gets the [parse]d text, null while it is invalid (then
 * [error] shows below). A new [value] from outside (Reset, < >) replaces the text unless the
 * text already means it, so typing `1e-5` is not rewritten on the way.
 */
@Composable
fun <T : Any> ParsedField(
    state: AppState,
    label: String,
    value: String,
    parse: (String) -> T?,
    error: String,
    modifier: Modifier = Modifier,
    onText: (T?) -> Unit,
) {
    var text by remember { mutableStateOf(value) }
    LaunchedEffect(value) { if (parse(text) != parse(value)) text = value }
    val invalid = parse(text) == null
    OutlinedTextField(
        text,
        onValueChange = { v ->
            text = v.take(MAX_FIELD_LENGTH)
            onText(parse(text))
        },
        label = { Text(label) },
        singleLine = true,
        isError = invalid,
        supportingText = if (invalid) ({ Text(error) }) else null,
        modifier = modifier.textFocus(state),
    )
}

private const val MAX_FIELD_LENGTH = 12

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
