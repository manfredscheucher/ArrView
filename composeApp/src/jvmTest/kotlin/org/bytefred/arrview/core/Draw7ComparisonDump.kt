package org.bytefred.arrview.core

import java.io.File
import kotlin.test.Test

/**
 * ArrView's B-spline control points for the comparison with the original draw7.sage
 * (scripts/probe/draw7_orig, doc/SPEC.md 2026-11-19), with sage's 100 iterations and eps 1e-10, for
 * every most symmetric outer face. Excluded from `jvmTest`; run with `-Pdraw7Dump`.
 */
class Draw7ComparisonDump {
    @Test
    fun dump() {
        val set = Examples.byName("n5-intersecting-all")!!
        val out = File("../scripts/probe/draw7_orig/cases")
        for (i in listOf(181, 6, 48, 2, 1)) {
            val arr = set.arrangement(i - 1)
            val sym = arr.outerFaceSymmetries(DrawModel.MODEL1)
            val max = sym.values.max()
            val text = StringBuilder()
            for (f in sym.keys.filter { sym.getValue(it) == max }) {
                val layout = ArrangementLayout(arr, DrawModel.MODEL1, f)
                val pos = IteratedTutte(layout.problem, LayoutSettings(maxIterations = 100, eps = 1e-10)).run()
                text.append("face $f default=${f == arr.defaultOuterFace(DrawModel.MODEL1)}\n")
                for ((c, ctrl) in layout.controlPoints(pos).withIndex()) text.append("circle ${c + 1} " + ctrl.joinToString(" ") { "${it.x},${it.y}" } + "\n")
            }
            File(out, "n5_$i.arrview.txt").writeText(text.toString())
        }
    }
}
