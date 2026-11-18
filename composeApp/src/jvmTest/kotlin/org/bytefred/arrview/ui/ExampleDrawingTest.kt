package org.bytefred.arrview.ui

import org.bytefred.arrview.core.ArrangementLayout
import org.bytefred.arrview.core.DrawModel
import org.bytefred.arrview.core.Drawing
import org.bytefred.arrview.core.ExampleSet
import org.bytefred.arrview.core.Examples
import org.bytefred.arrview.core.LayoutSettings
import org.bytefred.arrview.core.Point
import org.bytefred.arrview.core.defaultOuterFace
import org.bytefred.arrview.core.hasModel1
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The drawings (default settings, default outer face) of the examples with 6 or more circles, in
 * both models where model 1 exists, and their SVG export. LayoutTest draws every example with up to
 * 5 circles; here the long lists are sampled.
 */
class ExampleDrawingTest {
    /** All members of a short list, else the first, the last and three in between. */
    private fun sample(s: ExampleSet): List<Int> =
        if (s.size <= SAMPLE_ALL) (0 until s.size).toList()
        else listOf(0, s.size / 4, s.size / 2, 3 * s.size / 4, s.size - 1)

    private fun assertFinite(p: Point, what: String) = assertTrue(p.x.isFinite() && p.y.isFinite(), "$what: $p")

    private fun check(d: Drawing, what: String) {
        val arr = d.arrangement
        assertEquals(arr.n, d.circles.size, what)
        assertEquals(arr.n, d.labels.size, what)
        assertEquals(arr.faceCount, d.faces.size, what)
        assertTrue(d.outerFace in arr.faces.indices, what)
        for (c in d.circles) {
            assertTrue(c.isNotEmpty(), what)
            for (piece in c) for (p in listOf(piece.p0, piece.c1, piece.c2, piece.p3)) assertFinite(p, what)
        }
        for ((at, outward) in d.labels) {
            assertFinite(at, what)
            assertFinite(outward, what)
        }
        // model 1 draws every digon as a touching point, model 2 as a region
        assertEquals(if (d.model == DrawModel.MODEL1) arr.digonCount else 0, d.touchings.size, what)
        d.touchings.values.forEach { assertFinite(it, what) }
        val (minX, minY, maxX, maxY) = d.bounds.toList()
        assertTrue(listOf(minX, minY, maxX, maxY).all { it.isFinite() } && maxX > minX && maxY > minY, what)
    }

    private fun checkSvg(d: Drawing, what: String) {
        val svg = drawingSvg(d, ColorScheme.DEFAULT, AppState.DEFAULT_LINE_WIDTH)
        assertTrue("NaN" !in svg && "Infinity" !in svg, what)
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(svg.byteInputStream())
        val paths = doc.getElementsByTagName("path")
        assertEquals(d.arrangement.n, (0 until paths.length).count { paths.item(it).attributes.getNamedItem("stroke") != null }, what)
        assertEquals(d.arrangement.n, doc.getElementsByTagName("text").length, what)
        assertEquals(d.touchings.size, doc.getElementsByTagName("circle").length, what)
    }

    @Test
    fun largerExamplesDrawInBothModels() {
        var drawings = 0
        for (s in Examples.sets.filter { it.n >= 6 }) for (i in sample(s)) {
            val arr = s.arrangement(i)
            for (model in DrawModel.entries) {
                if (model == DrawModel.MODEL1 && !arr.hasModel1) continue
                val what = "${s.memberName(i)} $model"
                val d = ArrangementLayout(arr, model, arr.defaultOuterFace(model)).layout(LayoutSettings())
                check(d, what)
                checkSvg(d, what)
                drawings++
            }
        }
        assertTrue(drawings > 100, "$drawings drawings")
    }

    private companion object {
        const val SAMPLE_ALL = 25
    }
}
