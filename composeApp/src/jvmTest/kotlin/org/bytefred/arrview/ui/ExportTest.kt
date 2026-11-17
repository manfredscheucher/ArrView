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

/** Export SVG: well-formed, one curve and one label per circle, the triangles filled. */
class ExportTest {
    @Test
    fun svgOfTheStartExample() {
        val arr = Examples.start.arrangement()
        for (model in DrawModel.entries) {
            val d = ArrangementLayout(arr, model, arr.defaultOuterFace(model)).layout(LayoutSettings())
            val svg = drawingSvg(d)
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
}
