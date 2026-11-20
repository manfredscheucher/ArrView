package org.bytefred.arrview.ui

import org.bytefred.arrview.core.ArrangementLayout
import org.bytefred.arrview.core.DrawModel
import org.bytefred.arrview.core.Examples
import org.bytefred.arrview.core.LayoutSettings
import org.bytefred.arrview.core.defaultOuterFace
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Export SVG: well-formed, one curve and one label per circle, the triangles filled, the crossing colours. */
class ExportTest {
    @Test
    fun svgOfTheStartExample() {
        val arr = Examples.start.arrangement()
        for (model in DrawModel.entries) {
            val d = ArrangementLayout(arr, model, arr.defaultOuterFace(model)).layout(LayoutSettings())
            val svg = drawingSvg(d, ColorScheme.DEFAULT, AppState.DEFAULT_LINE_WIDTH)
            File("build/screenshots").apply { mkdirs() }.resolve("export-${model.name.lowercase()}.svg").writeText(svg)
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(svg.byteInputStream())
            val paths = doc.getElementsByTagName("path")
            val strokes = (0 until paths.length).count { paths.item(it).attributes.getNamedItem("stroke") != null }
            val fills = (0 until paths.length).count { paths.item(it).attributes.getNamedItem("fill")?.nodeValue == TRIANGLE_FILL.toHex() }
            assertEquals(arr.n, strokes)
            assertEquals(arr.triangleCount, fills + if (arr.faces[d.outerFace].size == 3) 1 else 0)
            assertEquals(arr.n, doc.getElementsByTagName("text").length)
        }
    }

    @Test
    fun svgShowsTheCrossingColours() {
        val sets = Examples.inGroup(Examples.group("colorings")).filter { it.hasColors }
        assertEquals(6, sets.size)
        for (s in sets) {
            val arr = s.arrangement(0)
            val colors = assertNotNull(s.colors(0))
            val d = ArrangementLayout(arr, DrawModel.MODEL2, arr.defaultOuterFace(DrawModel.MODEL2)).layout(LayoutSettings())
            val svg = drawingSvg(d, ColorScheme.DEFAULT, AppState.DEFAULT_LINE_WIDTH, colors)
            if (s.name == "n7-digonfree-triangle-saturated-1") File("build/screenshots").apply { mkdirs() }.resolve("export-colored-n7.svg").writeText(svg)
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(svg.byteInputStream())
            val circles = doc.getElementsByTagName("circle")
            val fills = (0 until circles.length).map { circles.item(it).attributes.getNamedItem("fill").nodeValue }
            // one disc per crossing (a crossing is two entries of the sequences), each on a black rim
            val expected = (0 until arr.size).filter { arr.partners[it] > it }.map { tokenColor(colors[it])!!.toHex() }
            assertEquals(expected.sorted(), fills.filter { it != "black" && it != DIGON_MARK.toHex() }.sorted(), s.name)
            assertEquals(expected.size, fills.count { it == "black" }, s.name)
            assertTrue(setOf("R", "G", "B").map { tokenColor(it)!!.toHex() }.all { it in fills }, s.name)
            // without colours: no discs
            assertTrue("fill=\"black\"" !in drawingSvg(d, ColorScheme.DEFAULT, AppState.DEFAULT_LINE_WIDTH), s.name)
        }
    }
}
