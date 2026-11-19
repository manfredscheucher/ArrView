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
import kotlin.math.max
import kotlin.math.min

/** The three graphs of an arrangement on the Help page Visualization, each drawn over the model 2 curves. */
enum class GraphKind(val title: String, val caption: String) {
    PRIMAL(
        "Primal graph",
        "A vertex per crossing (dots), an edge per arc (drawn along the curve). The two arcs of a digon are a double edge.",
    ),
    DUAL(
        "Dual graph",
        "A vertex per cell (dots), an edge across every arc, joining the two cells on its sides; every crossing becomes a " +
            "quadrangle. The outer cell's vertex is left out, its edges end in stubs. The model 1 sparse6 encodes this graph.",
    ),
    PRIMAL_DUAL(
        "Primal-dual graph",
        "A vertex per crossing (black), arc (green) and cell (blue), an edge for every incidence of a crossing or a cell " +
            "with an arc. The outer cell's vertex is left out, as in the model 2 drawing.",
    ),
}

/** The figures' drawing: the start example in model 2, computed once. */
private val figureDrawing: Drawing by lazy {
    val arr = Examples.start.arrangement()
    ArrangementLayout(arr, DrawModel.MODEL2, arr.defaultOuterFace(DrawModel.MODEL2)).layout(LayoutSettings())
}

/** The size of a figure on the Help page. */
val FIGURE_SIZE = 280.dp

/** One graph of [GraphKind] over the faint model 2 curves of the start example, in the colours of [scheme]. */
@Composable
fun GraphFigure(kind: GraphKind, scheme: ColorScheme, modifier: Modifier = Modifier.size(FIGURE_SIZE)) {
    val d = figureDrawing
    Canvas(modifier.testTag("figure-${kind.name}")) {
        val (minX, minY, maxX, maxY) = d.bounds
        val scale = min(size.width, size.height) * (1 - 2 * FIGURE_MARGIN) / max(maxX - minX, maxY - minY)
        val cx = (minX + maxX) / 2
        val cy = (minY + maxY) / 2
        fun at(p: Point) = Offset((size.width / 2 + (p.x - cx) * scale).toFloat(), (size.height / 2 - (p.y - cy) * scale).toFloat())
        drawGraph(d, kind, scheme, ::at)
    }
}

private fun DrawScope.drawGraph(d: Drawing, kind: GraphKind, scheme: ColorScheme, at: (Point) -> Offset) {
    val arr = d.arrangement
    val n = arr.n
    val dot = DOT_RADIUS.toPx()
    val stub = STUB_LENGTH.toPx()
    for ((c, pieces) in d.circles.withIndex()) {
        drawPath(path(pieces, at, close = true), scheme.color(c, n).copy(alpha = CURVE_ALPHA), style = Stroke(CURVE_WIDTH.toPx(), cap = StrokeCap.Round))
    }
    val middles = d.arcs.map { at(arcMiddle(it)) }
    val cells = arr.faces.indices.map { f -> d.interiorPoint(f)?.let(at) }
    /** The edge from arc [k]'s middle to cell [f]'s vertex, a stub for the outer cell (away from the cell on the other side). */
    fun arcCellEdge(k: Int, f: Int, other: Int) {
        val m = middles[k]
        val to = cells[f] ?: cells[other]?.let { o -> m + (m - o) / (m - o).getDistance() * stub } ?: return
        drawLine(EDGE_COLOR, m, to, strokeWidth = EDGE_WIDTH.toPx())
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
                arcCellEdge(k, f, g)
                arcCellEdge(k, g, f)
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
                arcCellEdge(k, f, g)
                arcCellEdge(k, g, f)
            }
            for (arc in d.arcs) drawCircle(CROSSING_COLOR, dot, at(arc.first().p0))
            for (m in middles) drawCircle(ARC_COLOR, dot, m)
            for (p in cells) if (p != null) drawCircle(CELL_COLOR, dot, p)
        }
    }
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
private val STUB_LENGTH = 12.dp

internal val CROSSING_COLOR = Color(0xFF222222)
internal val ARC_COLOR = Color(0xFF2E7D32)
internal val CELL_COLOR = Color(0xFF1A4FB0)
private val EDGE_COLOR = Color(0xFF555555)
