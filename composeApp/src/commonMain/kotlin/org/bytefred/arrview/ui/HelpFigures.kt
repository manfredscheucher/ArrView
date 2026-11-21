package org.bytefred.arrview.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.bytefred.arrview.core.ArrangementLayout
import org.bytefred.arrview.core.Cubic
import org.bytefred.arrview.core.DrawModel
import org.bytefred.arrview.core.Drawing
import org.bytefred.arrview.core.Examples
import org.bytefred.arrview.core.LayoutSettings
import org.bytefred.arrview.core.Point
import org.bytefred.arrview.core.defaultOuterFace
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin

/** The three graphs of an arrangement on the Help page Visualization, each drawn over the model 2 curves. */
enum class GraphKind(val title: String, val caption: String) {
    PRIMAL(
        "Primal graph",
        "A vertex per crossing (dots), an edge per arc (drawn along the curve). The two arcs of a digon are a double edge.",
    ),
    DUAL(
        "Dual graph",
        "A vertex per cell (dots), an edge across every arc, joining the two cells on its sides; every crossing becomes a " +
            "quadrangle. The outer cell's vertex is outside, its edges go around the drawing. The model 1 sparse6 encodes this graph.",
    ),
    PRIMAL_DUAL(
        "Primal-dual graph",
        "A vertex per crossing (black), arc (green) and cell (blue), an edge for every incidence of a crossing or a cell " +
            "with an arc. The outer cell's vertex is outside, as in the dual graph (the model 2 drawing leaves it out).",
    ),
}

/** The figures' drawing: the start example in model 2, computed once. */
internal val figureDrawing: Drawing by lazy {
    val arr = Examples.start.arrangement()
    ArrangementLayout(arr, DrawModel.MODEL2, arr.defaultOuterFace(DrawModel.MODEL2)).layout(LayoutSettings())
}

/** The outer cell's vertex and its edges, routed around the figures' drawing. */
internal val figureOuter: OuterRoutes by lazy { outerRoutes(figureDrawing) }

/** The size of a figure on the Help page. */
val FIGURE_SIZE = 280.dp

/** One graph of [GraphKind] over the faint model 2 curves of the start example, in the colours of [scheme]. */
@Composable
fun GraphFigure(kind: GraphKind, scheme: ColorScheme, modifier: Modifier = Modifier.size(FIGURE_SIZE)) {
    val d = figureDrawing
    val outer = figureOuter
    Canvas(modifier.testTag("figure-${kind.name}")) {
        // the curves, and for the dual and primal-dual graph also the outer cell's vertex and edges
        val (minX, minY, maxX, maxY) = if (kind == GraphKind.PRIMAL) d.bounds else outer.bounds(d.bounds)
        val scale = min(size.width, size.height) * (1 - 2 * FIGURE_MARGIN) / max(maxX - minX, maxY - minY)
        val cx = (minX + maxX) / 2
        val cy = (minY + maxY) / 2
        fun at(p: Point) = Offset((size.width / 2 + (p.x - cx) * scale).toFloat(), (size.height / 2 - (p.y - cy) * scale).toFloat())
        drawGraph(d, outer, kind, scheme, ::at)
    }
}

private fun DrawScope.drawGraph(d: Drawing, outer: OuterRoutes, kind: GraphKind, scheme: ColorScheme, at: (Point) -> Offset) {
    val arr = d.arrangement
    val n = arr.n
    val dot = DOT_RADIUS.toPx()
    for ((c, pieces) in d.circles.withIndex()) {
        drawPath(path(pieces, at, close = true), scheme.color(c, n).copy(alpha = CURVE_ALPHA), style = Stroke(CURVE_WIDTH.toPx(), cap = StrokeCap.Round))
    }
    val middles = d.arcs.map { at(arcMiddle(it)) }
    val cells = arr.faces.indices.map { f -> if (f == d.outerFace) at(outer.vertex) else d.interiorPoint(f)?.let(at) }
    /** The edge from arc [k]'s middle to cell [f]'s vertex; to the outer cell's vertex around the drawing. */
    fun arcCellEdge(k: Int, f: Int) {
        val route = outer.edges[k]
        if (f == d.outerFace && route != null) {
            // the corners rounded: from midpoint to midpoint, the polyline's points as control points
            val pts = route.map(at)
            drawPath(Path().apply {
                moveTo(pts[0].x, pts[0].y)
                for (i in 1 until pts.size - 1) {
                    val mid = (pts[i] + pts[i + 1]) / 2f
                    quadraticTo(pts[i].x, pts[i].y, mid.x, mid.y)
                }
                lineTo(pts.last().x, pts.last().y)
            }, EDGE_COLOR, style = Stroke(EDGE_WIDTH.toPx()))
        } else {
            drawLine(EDGE_COLOR, middles[k], cells[f] ?: return, strokeWidth = EDGE_WIDTH.toPx())
        }
    }
    when (kind) {
        GraphKind.PRIMAL -> {
            for (arc in d.arcs) drawPath(path(arc, at, close = false), CROSSING_COLOR, style = Stroke(EDGE_WIDTH.toPx(), cap = StrokeCap.Round))
            for (arc in d.arcs) drawCircle(CROSSING_COLOR, dot, at(arc.first().p0))
        }
        GraphKind.DUAL -> {
            for (k in 0 until arr.size) {
                val f = arr.faceOfDart[2 * k]
                val g = arr.faceOfDart[arr.alpha(2 * k)]
                arcCellEdge(k, f)
                arcCellEdge(k, g)
            }
            for (p in cells) if (p != null) drawCircle(CELL_COLOR, dot, p)
        }
        GraphKind.PRIMAL_DUAL -> {
            for (k in 0 until arr.size) {
                val arc = d.arcs[k]
                drawLine(EDGE_COLOR, at(arc.first().p0), middles[k], strokeWidth = EDGE_WIDTH.toPx())
                drawLine(EDGE_COLOR, at(arc.last().p3), middles[k], strokeWidth = EDGE_WIDTH.toPx())
                val f = arr.faceOfDart[2 * k]
                val g = arr.faceOfDart[arr.alpha(2 * k)]
                arcCellEdge(k, f)
                arcCellEdge(k, g)
            }
            for (arc in d.arcs) drawCircle(CROSSING_COLOR, dot, at(arc.first().p0))
            for (m in middles) drawCircle(ARC_COLOR, dot, m)
            for (p in cells) if (p != null) drawCircle(CELL_COLOR, dot, p)
        }
    }
}

/**
 * The outer cell's [vertex], outside the drawing, and its [edges]: per arc on the outer cell a
 * polyline from the arc's middle to the vertex (model coordinates).
 */
internal class OuterRoutes(val vertex: Point, val edges: Map<Int, List<Point>>) {
    /** [curves] (minX, minY, maxX, maxY) grown by the vertex and the edges. */
    fun bounds(curves: DoubleArray): DoubleArray {
        val pts = edges.values.flatten() + vertex
        return doubleArrayOf(
            min(curves[0], pts.minOf { it.x }), min(curves[1], pts.minOf { it.y }),
            max(curves[2], pts.maxOf { it.x }), max(curves[3], pts.maxOf { it.y }),
        )
    }
}

/**
 * Routes the edges of the outer cell around [d] without crossings. The vertex goes into the widest
 * angular gap between the outer arcs' middles, seen from the centre c. Each edge leaves its arc
 * radially, runs on its own circle around c towards the vertex (the farther from the vertex, the
 * larger the circle, so it passes outside the nearer edges) and ends in a straight line to the vertex.
 */
internal fun outerRoutes(d: Drawing): OuterRoutes {
    val arr = d.arrangement
    val outerArcs = (0 until arr.size).filter { k -> arr.faceOfDart[2 * k] == d.outerFace || arr.faceOfDart[arr.alpha(2 * k)] == d.outerFace }
    val (minX, minY, maxX, maxY) = d.bounds
    val c = Point((minX + maxX) / 2, (minY + maxY) / 2)
    val radius = d.circles.flatMap { Drawing.flatten(it) }.maxOf { hypot(it.x - c.x, it.y - c.y) }
    val gap = OUTER_GAP * radius
    val middle = outerArcs.associateWith { arcMiddle(d.arcs[it]) }
    val angle = middle.mapValues { (_, m) -> atan2(m.y - c.y, m.x - c.x) }
    // the vertex in the middle of the widest gap between the middles' angles
    val sorted = angle.values.sorted()
    val (from, width) = sorted.indices.map { i ->
        val next = if (i + 1 < sorted.size) sorted[i + 1] else sorted[0] + 2 * PI
        sorted[i] to next - sorted[i]
    }.maxBy { it.second }
    val toVertex = from + width / 2
    // signed angle from each middle to the vertex (-pi, pi]; per side, the rank by distance
    val delta = angle.mapValues { (_, a) -> (toVertex - a).let { x -> x - 2 * PI * ceil((x - PI) / (2 * PI)) } }
    val rank = delta.keys.groupBy { delta.getValue(it) > 0 }.values
        .flatMap { side -> side.sortedBy { abs(delta.getValue(it)) }.withIndex().map { (r, k) -> k to r } }.toMap()
    val vertexRadius = radius + gap * ((rank.values.maxOrNull() ?: 0) + 2)
    fun polar(r: Double, a: Double) = Point(c.x + r * cos(a), c.y + r * sin(a))
    val vertex = polar(vertexRadius, toVertex)
    // every edge stops its circle this far before the vertex's angle
    val stop = min(OUTER_STOP, delta.values.minOf { abs(it) } / 2)
    val edges = outerArcs.associateWith { k ->
        val r = radius + gap * (rank.getValue(k) + 1)
        val a = angle.getValue(k)
        val sweep = delta.getValue(k).let { it - sign(it) * stop }
        val steps = max(1, ceil(abs(sweep) / OUTER_STEP).toInt())
        listOf(middle.getValue(k)) + (0..steps).map { i -> polar(r, a + sweep * i / steps) } + vertex
    }
    return OuterRoutes(vertex, edges)
}

/** The point halfway along the flattened [arc]. */
private fun arcMiddle(arc: List<Cubic>): Point {
    val pts = Drawing.flatten(arc) + arc.last().p3
    return pts[pts.size / 2]
}

private fun path(pieces: List<Cubic>, at: (Point) -> Offset, close: Boolean): Path = Path().apply {
    if (pieces.isEmpty()) return@apply
    val start = at(pieces.first().p0)
    moveTo(start.x, start.y)
    for (c in pieces) {
        val a = at(c.c1)
        val b = at(c.c2)
        val e = at(c.p3)
        cubicTo(a.x, a.y, b.x, b.y, e.x, e.y)
    }
    if (close) close()
}

/** Share of the figure left free on each side. */
private const val FIGURE_MARGIN = 0.06f

private const val CURVE_ALPHA = 0.3f
private val CURVE_WIDTH = 2.dp
private val EDGE_WIDTH = 1.5.dp
private val DOT_RADIUS = 3.5.dp

/** Distance between the circles of the outer cell's edges, as a share of the drawing's radius. */
private const val OUTER_GAP = 0.07

/** Radians an outer edge's circle stops before the vertex (at most). */
private const val OUTER_STOP = 0.15

/** Radians per straight piece of an outer edge's circle. */
private const val OUTER_STEP = 0.05

internal val CROSSING_COLOR = Color(0xFF222222)
internal val ARC_COLOR = Color(0xFF2E7D32)
internal val CELL_COLOR = Color(0xFF1A4FB0)
private val EDGE_COLOR = Color(0xFF555555)
