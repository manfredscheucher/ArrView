package org.bytefred.arrview.ui

import org.bytefred.arrview.core.ArrangementLayout
import org.bytefred.arrview.core.CurveCheck
import org.bytefred.arrview.core.DrawModel
import org.bytefred.arrview.core.DrawingSweep
import org.bytefred.arrview.core.Examples
import org.bytefred.arrview.core.LayoutSettings
import org.bytefred.arrview.core.Point
import org.bytefred.arrview.core.arcOfDart
import org.bytefred.arrview.core.defaultOuterFace
import org.bytefred.arrview.core.faceCircles
import java.io.File
import kotlin.test.Test

/**
 * Screenshots of drawing sweep defects (build/sweep/screenshots/): the app, and an SVG zoomed onto
 * every model 1 digon of the pair (curves only, the pair thick).
 */
class SweepScreenshotTest {
    private val cases = listOf(
        Triple("n6-intersecting-nonr-all-triangles-krupp", 0, null),
        Triple("n4-intersecting-all", 2, "flip triangle 1 2 4 (face 10)"),
        Triple("n4-connected-all", 3, "flip triangle 2 4 3 (face 10)"),
        Triple("n4-intersecting-all", 0, "flip triangle"),
        Triple("n5-intersecting-all", 180, null),
    )

    /** Self-crossing curves of the full sweep: set, member, flip, model. */
    private val selfCrossing = listOf(
        listOf("n5-intersecting-all", 137, "flip triangle 1 2 3 (face 3)", DrawModel.MODEL1),
        listOf("n5-connected-all", 216, "flip triangle 1 5 2 (face 5)", DrawModel.MODEL2),
        listOf("n5-connected-all", 947, "flip triangle 1 2 3 (face 6)", DrawModel.MODEL1),
        listOf("n5-connected-all", 442, "flip triangle 1 3 5 (face 6)", DrawModel.MODEL2),
    )

    @Test
    fun selfCrossings() {
        for ((setName, index, flip, model) in selfCrossing) {
            val set = Examples.byName(setName as String)!!
            val (name, arr) = DrawingSweep.withFlips(set.memberName(index as Int), set.arrangement(index)).first { (flip as String) in it.first }
            model as DrawModel
            val file = (name + " " + model.label).replace(Regex("[^A-Za-z0-9]+"), "_")
            SweepScreenshots.render(name, arr, model, "$file.png")
            val d = ArrangementLayout(arr, model, arr.defaultOuterFace(model)).layout(LayoutSettings())
            val check = CurveCheck(d.circles)
            for (c in 0 until arr.n) for ((k, at) in check.selfCrossingPoints(c).withIndex()) {
                for ((r, tag) in listOf(0.25 to "near", 0.03 to "close", 0.003 to "closer", 0.0003 to "closest", 0.00003 to "micro")) {
                    File("build/sweep/screenshots/${file}_curve_${c + 1}_${k}_$tag.svg").writeText(zoom(d.circles, listOf(c), at, r))
                }
            }
        }
    }

    @Test
    fun defects() {
        for ((setName, index, flip) in cases) {
            val set = Examples.byName(setName)!!
            val (name, arr) = DrawingSweep.withFlips(set.memberName(index), set.arrangement(index)).first { flip == null || flip in it.first }
            val file = name.replace(Regex("[^A-Za-z0-9]+"), "_")
            SweepScreenshots.render(name, arr, DrawModel.MODEL1, "$file.png")
            val d = ArrangementLayout(arr, DrawModel.MODEL1, arr.defaultOuterFace(DrawModel.MODEL1)).layout(LayoutSettings())
            for ((f, at) in d.touchings) {
                val pair = arr.faceCircles(f).sorted()
                val span = d.arcs[arr.arcOfDart(arr.faces[f].first())].let { arc -> (arc.last().p3 - arc.first().p0).length }
                val stem = "build/sweep/screenshots/${file}_digon_${pair[0] + 1}_${pair[1] + 1}"
                File("$stem.svg").writeText(zoom(d.circles, pair, at, 2.5 * span))
                // the two ends of the shared span, closer
                val arc = d.arcs[arr.arcOfDart(arr.faces[f].first())]
                File("${stem}_end1.svg").writeText(zoom(d.circles, pair, arc.first().p0, 0.3 * span))
                File("${stem}_end2.svg").writeText(zoom(d.circles, pair, arc.last().p3, 0.3 * span))
            }
        }
    }

    private fun zoom(circles: List<List<org.bytefred.arrview.core.Cubic>>, pair: List<Int>, at: Point, r: Double) = buildString {
        fun p(q: Point) = "${q.x} ${-q.y}"
        append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"800\" height=\"800\" viewBox=\"${at.x - r} ${-at.y - r} ${2 * r} ${2 * r}\">")
        append("<rect x=\"${at.x - r}\" y=\"${-at.y - r}\" width=\"${2 * r}\" height=\"${2 * r}\" fill=\"white\"/>")
        for ((c, pieces) in circles.withIndex()) {
            val color = ColorScheme.STANDARD.color(c, circles.size).toHex()
            val w = if (c in pair) r / 120 else r / 400
            append("<path fill=\"none\" stroke=\"$color\" stroke-opacity=\"0.8\" stroke-width=\"$w\" d=\"M${p(pieces[0].p0)}")
            for (b in pieces) append(" C${p(b.c1)} ${p(b.c2)} ${p(b.p3)}")
            append(" Z\"/>")
        }
        append("<circle cx=\"${at.x}\" cy=\"${-at.y}\" r=\"${r / 150}\" fill=\"black\"/>")
        val what = if (pair.size == 1) "curve ${pair[0] + 1}" else "curves ${pair[0] + 1} and ${pair[1] + 1}"
        append("<text x=\"${at.x - r * 0.95}\" y=\"${-at.y - r * 0.88}\" font-size=\"${r / 14}\" font-family=\"sans-serif\">$what</text>")
        append("</svg>")
    }
}
