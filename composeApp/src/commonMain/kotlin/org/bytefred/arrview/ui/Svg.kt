package org.bytefred.arrview.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.bytefred.arrview.core.Cubic
import org.bytefred.arrview.core.Drawing
import org.bytefred.arrview.core.Point
import kotlin.math.abs
import kotlin.math.round

/**
 * Maps the drawing's [bounds] (minX, minY, maxX, maxY, y up) into a [size] x [size] box with
 * [margin] on all sides, keeping the aspect ratio, y down (PoCoView's ExportFrame).
 */
private class ExportFrame(bounds: DoubleArray, size: Double, margin: Double) {
    private val minX = bounds[0]
    private val maxY = bounds[3]
    private val scale = (size - 2 * margin) / maxOf(1e-9, bounds[2] - bounds[0], bounds[3] - bounds[1])
    private val left = (size - (bounds[2] - bounds[0]) * scale) / 2
    private val top = (size - (bounds[3] - bounds[1]) * scale) / 2

    fun x(p: Point) = left + (p.x - minX) * scale
    fun y(p: Point) = top + (maxY - p.y) * scale
}

/** [decimals] decimals (rounded), no exponent notation. */
private fun fmt(v: Double, decimals: Int = 1): String {
    var f = 1L
    repeat(decimals) { f *= 10 }
    val r = round(v * f).toLong()
    val sign = if (r < 0) "-" else ""
    val a = abs(r)
    return if (decimals == 0) "$sign$a" else "$sign${a / f}.${(a % f).toString().padStart(decimals, '0')}"
}

fun Color.toHex(): String = "#" + (toArgb() and 0xFFFFFF).toString(16).padStart(6, '0').uppercase()

/**
 * SVG of [d] as the canvas draws it (triangles and digons filled, the curves in their colours,
 * model 1 touching points, the coloured crossings of [crossingColors], the labels), in a 1000 x 1000 box.
 */
fun drawingSvg(d: Drawing, scheme: ColorScheme, lineWidth: Float, crossingColors: List<String>? = null): String {
    val fr = ExportFrame(d.bounds, SVG_SIZE, SVG_MARGIN)
    fun pt(p: Point) = "${fmt(fr.x(p))},${fmt(fr.y(p))}"
    fun path(pieces: List<Cubic>) = buildString {
        append("M${pt(pieces.first().p0)}")
        for (c in pieces) append(" C${pt(c.c1)} ${pt(c.c2)} ${pt(c.p3)}")
        append(" Z")
    }
    val size = fmt(SVG_SIZE, 0)
    return buildString {
        append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 $size $size\" width=\"$size\" height=\"$size\">\n")
        append("<rect width=\"$size\" height=\"$size\" fill=\"white\"/>\n")
        val faces = d.arrangement.faces
        for ((f, pieces) in d.faces.withIndex()) {
            if (pieces.isEmpty()) continue
            val fill = when (faces[f].size) {
                3 -> TRIANGLE_FILL
                2 -> DIGON_FILL
                else -> continue
            }
            append("<path d=\"${path(pieces)}\" fill=\"${fill.toHex()}\"/>\n")
        }
        for ((c, pieces) in d.circles.withIndex()) {
            if (pieces.isEmpty()) continue
            append("<path d=\"${path(pieces)}\" fill=\"none\" stroke=\"${scheme.color(c, d.arrangement.n).toHex()}\" stroke-width=\"${fmt(lineWidth.toDouble())}\"/>\n")
        }
        for (at in d.touchings.values) {
            append("<circle cx=\"${fmt(fr.x(at))}\" cy=\"${fmt(fr.y(at))}\" r=\"5\" fill=\"${DIGON_MARK.toHex()}\"/>\n")
        }
        for ((at, color) in crossingColors?.let { coloredCrossings(d, it) }.orEmpty()) {
            val c = "cx=\"${fmt(fr.x(at))}\" cy=\"${fmt(fr.y(at))}\""
            append("<circle $c r=\"${fmt(CROSSING_RIM.toDouble(), 2)}\" fill=\"black\"/>\n")
            append("<circle $c r=\"${fmt(CROSSING_DISC.toDouble(), 2)}\" fill=\"${color.toHex()}\"/>\n")
        }
        for ((c, label) in d.labels.withIndex()) {
            val (at, outward) = label
            val x = fr.x(at) + outward.x * SVG_LABEL_OFFSET
            val y = fr.y(at) - outward.y * SVG_LABEL_OFFSET
            append(
                "<text x=\"${fmt(x)}\" y=\"${fmt(y)}\" fill=\"${scheme.color(c, d.arrangement.n).toHex()}\" font-size=\"20\" " +
                    "font-weight=\"bold\" font-family=\"sans-serif\" text-anchor=\"middle\" dominant-baseline=\"central\">${c + 1}</text>\n",
            )
        }
        append("</svg>\n")
    }
}

private const val SVG_SIZE = 1000.0

/** Room around the drawing for the labels. */
private const val SVG_MARGIN = 50.0

/** Distance between a curve and its label. */
private const val SVG_LABEL_OFFSET = 18.0
