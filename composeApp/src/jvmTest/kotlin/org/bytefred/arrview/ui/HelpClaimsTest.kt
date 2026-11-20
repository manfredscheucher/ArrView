package org.bytefred.arrview.ui

import org.bytefred.arrview.core.Arrangement
import org.bytefred.arrview.core.DrawModel
import org.bytefred.arrview.core.Examples
import org.bytefred.arrview.core.FileFormat
import org.bytefred.arrview.core.LayoutSettings
import org.bytefred.arrview.core.arrangementFile
import org.bytefred.arrview.core.cellDescription
import org.bytefred.arrview.core.digonCreates
import org.bytefred.arrview.core.hasModel1
import org.bytefred.arrview.core.parseJson
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** What the Help pages state, checked against the code (as PoCoView's HelpClaimsTest). */
class HelpClaimsTest {
    private val pages = HelpPage.entries.associateWith { helpText(it).joinToString(" ") }

    /** doc/USER_GUIDE.md (the tests run in composeApp/). */
    private val guide: String = File("../doc/USER_GUIDE.md").readText()

    private fun assertSays(page: HelpPage, claim: String) =
        assertTrue(claim in pages.getValue(page), "Help ${page.title} should say \"$claim\"")

    @Test
    fun onlyAscii() {
        for ((page, text) in pages) {
            val bad = text.filter { it.code !in 32..126 }
            assertEquals("", bad, "non-ASCII characters on the Help page ${page.title}")
        }
    }

    @Test
    fun everyWidgetIsExplained() {
        for (w in Widget.entries) assertSays(HelpPage.WIDGETS, "${w.label}: ")
    }

    @Test
    fun layoutDefaults() {
        assertSays(HelpPage.WIDGETS, "Max. iterations (default ${LayoutSettings().maxIterations})")
        assertSays(HelpPage.WIDGETS, "Max. widgets (default ${AppState.DEFAULT_MAX_WIDGETS})")
        assertTrue("eps" !in pages.getValue(HelpPage.WIDGETS), "eps is not in the GUI")
    }

    @Test
    fun modelNames() {
        assertSays(HelpPage.WIDGETS, "Visualization (default ${DrawModel.MODEL2.option};")
        assertSays(HelpPage.GENERAL, "The drawing is model 2 by default")
    }

    @Test
    fun visualizationPage() {
        for (kind in GraphKind.entries) assertSays(HelpPage.VISUALIZATION, kind.title)
        assertSays(HelpPage.VISUALIZATION, Examples.start.name)
        for (paper in PAPERS.take(2)) assertSays(HelpPage.VISUALIZATION, paper)
        assertSays(HelpPage.VISUALIZATION, "up to Max. iterations times (Settings)")
    }

    @Test
    fun signConvention() {
        // "+j in list i and -i in list j are the same crossing": every +j in list i has its -i in list j
        val krupp = Arrangement.krupp()
        for (i in 0 until krupp.n) for (e in krupp.list(i)) if (e > 0) assertTrue(-(i + 1) in krupp.list(e - 1))
        assertSays(HelpPage.FORMATS, "So +j in list i and -i in list j are the same crossing.")
    }

    @Test
    fun arrangementMenuItems() {
        // the menu itself: MenusAndDialogsUiTest.theMenusHaveTheirItems
        for (item in ARRANGEMENT_MENU) assertSays(HelpPage.GENERAL, item)
        assertTrue("Arrangement: ${ARRANGEMENT_MENU.joinToString(", ")}." in guide, "USER_GUIDE.md: Arrangement menu")
        assertTrue("Help: ${HelpPage.entries.joinToString(", ") { it.title }}, About." in guide.replace("\n  ", " "), "USER_GUIDE.md: Help menu")
    }

    @Test
    fun model1NeedsAnIntersectingArrangementOfThreeCircles() {
        assertSays(HelpPage.VISUALIZATION, "so model 1 shows intersecting arrangements (every two circles cross) of at least 3 circles only.")
        for (set in Examples.sets.filter { it.n <= 5 }) for (i in 0 until set.size) {
            val arr = set.arrangement(i)
            assertEquals(arr.isIntersecting && arr.n >= 3, arr.hasModel1, set.memberName(i))
        }
    }

    @Test
    fun faceColours() {
        assertSays(HelpPage.GENERAL, "Triangles are filled light orange, digons light blue.")
        assertTrue(helpText(HelpPage.GENERAL).first().startsWith("$PSEUDOCIRCLES_INTRO ArrView shows connected arrangements"))
        assertEquals(
            "ArrView is a viewer for arrangements of pseudocircles: closed curves where every two curves meet at most " +
                "twice and cross at each meeting point. The curves need not be circles (hence \"pseudo\"); only the " +
                "combinatorics counts. Change an arrangement by flips (click a triangle or a digon) and explore and " +
                "visualise its combinatorial properties.",
            WELCOME_TEXT,
        )
        assertTrue(WELCOME_TEXT.all { it.code in 32..126 }, "ASCII only")
        // orange: red > green > blue; blue: blue on top; both light
        assertTrue(TRIANGLE_FILL.red > TRIANGLE_FILL.green && TRIANGLE_FILL.green > TRIANGLE_FILL.blue)
        assertTrue(DIGON_FILL.blue > DIGON_FILL.red && DIGON_FILL.blue > DIGON_FILL.green)
        for (c in listOf(TRIANGLE_FILL, DIGON_FILL)) assertTrue(minOf(c.red, c.green, c.blue) > 0.6f, "$c is light")
    }

    @Test
    fun shortcutsAreTheHandledKeysAndTheUserGuideHasTheSame() {
        // the keys themselves: CanvasInteractionUiTest (undoRedoShortcuts, zoomAndFitKeys, shortcutsAreOff...)
        val lines = helpText(HelpPage.SHORTCUTS)
        for (line in listOf("Esc: close the open menu", "Ctrl+Z: undo", "Ctrl+Shift+Z or Ctrl+Y: redo", "+ / -: zoom in / out", "Home or 0: fit")) {
            assertTrue(line in lines, line)
            assertTrue("- $line" in guide, "USER_GUIDE.md: $line")
        }
        assertSays(HelpPage.SHORTCUTS, "On a Mac, Cmd works like Ctrl")
        assertTrue("On a Mac, Cmd works like Ctrl." in guide)
        val guideKeys = guide.substringAfter("## Keyboard shortcuts").lines().filter { it.startsWith("- ") }.map { it.drop(2) }
        assertEquals(lines.filter { ": " in it && !it.startsWith("Mouse") }, guideKeys)
    }

    @Test
    fun userGuideLayoutDefaults() {
        val d = LayoutSettings()
        assertTrue("Layout (Visualization: model 2 or model 1, max. iterations (${d.maxIterations}))" in guide.replace("\n  ", " "))
        assertTrue("line width 0.5 to 8, default 4" in guide.replace("\n  ", " "))
        assertEquals(0.5f..8f, AppState.LINE_WIDTH_RANGE)
        assertEquals(4f, AppState.DEFAULT_LINE_WIDTH)
        val schemes = ColorScheme.entries.joinToString(", ") { if (it == ColorScheme.DEFAULT) "${it.label} (default)" else it.label }
        assertTrue("colour scheme: $schemes;" in guide.replace("\n  ", " "), schemes)
    }

    @Test
    fun listsBelowTheGraphs() {
        // the Help examples are in the format of the lists
        assertEquals("possible digon creates (3): 1 3 (x2), 2 4, 2 5", possibleList("digon creates", listOf(listOf(0, 2) to 2, listOf(1, 3) to 1, listOf(1, 4) to 1)))
        assertSays(HelpPage.WIDGETS, "possible digon creates (3): 1 3 (x2), 2 4, 2 5. (x2) marks a pair with 2 ways.")
        assertEquals("possible triangle flips (3): 1 2 3 (x2), 1 2 4, 3 4 6", possibleList("triangle flips", listOf(listOf(0, 1, 2) to 2, listOf(0, 1, 3) to 1, listOf(2, 3, 5) to 1)))
        assertSays(HelpPage.WIDGETS, "possible triangle flips (3): 1 2 3 (x2), 1 2 4, 3 4 6; click an entry to flip it")
        assertSays(HelpPage.WIDGETS, "The entries are links: a click does what a click on the pair in the graph does")
        assertTrue("Click an entry to do it, like a click on the pair" in guide.replace("\n  ", " "))
        assertEquals("possible digon collapses (0): none", possibleList("digon collapses", emptyList()))
        assertTrue("\"possible digon creates (k): 1 3 (x2), 2 4\"" in guide.replace("\n  ", " "))
    }

    @Test
    fun triplesAscendingFacesOriented() {
        assertSays(HelpPage.WIDGETS, "A triple is always written ascending, as in the menu title Flip a triangle of 1 2 3;")
        assertEquals("Flip a triangle of 1 2 3", triangleHeading(listOf(listOf(2, 0, 1))))
        assertEquals("Flip a triangle", triangleHeading(listOf(listOf(0, 1, 2), listOf(0, 1, 3))))
    }

    @Test
    fun examplesByPurpose() {
        assertSays(HelpPage.GENERAL, "Examples... lists the bundled arrangements by purpose, as on the pseudocircles website; an open group says what it is, a selected set shows its description.")
    }

    @Test
    fun settingsWidget() {
        assertSays(HelpPage.WIDGETS, "colour scheme of the circles (Standard (default), rainbow, rainbow2;")
        assertEquals("Standard", ColorScheme.DEFAULT.label)
        assertSays(HelpPage.WIDGETS, "line width (default 4 px, also in Export SVG)")
        assertSays(HelpPage.WIDGETS, "Reset to defaults resets everything in the widget.")
    }

    @Test
    fun savedFileFormat() {
        assertSays(HelpPage.FORMATS, "Save file writes JSON {\"name\": ..., \"n\": ..., \"sequences\": [[...], ...]} (list i for circle i")
        val json = arrangementFile("x", Arrangement.krupp(), FileFormat.JSON)
        val root = parseJson(json) as Map<*, *>
        assertEquals(listOf("name", "n", "sequences"), root.keys.toList())
        assertEquals(Arrangement.krupp().lists.map { l -> l.map { it.toLong() } }, root["sequences"])
        // the sequences example of the Formats page is a real arrangement (the NonKrupp)
        val example = "{1: [2, -2, 3, -3], 2: [...], ...}"
        assertSays(HelpPage.FORMATS, example)
        val nonKrupp = Examples.byName("n3-intersecting-nonkrupp")!!.arrangement(0)
        assertTrue(nonKrupp.toString().startsWith(example.substringBefore(" 2:")))
    }

    @Test
    fun flipsInTheGraphViewsAndTheMenuNotation() {
        assertSays(HelpPage.GENERAL, "Click a face where two circles that do not cross both have a side to create a digon")
        assertSays(HelpPage.GENERAL, "A \"!\" appears in the face under the mouse")
        assertSays(HelpPage.GENERAL, "Pointing at a menu entry highlights its face in yellow")
        assertSays(HelpPage.GENERAL, "the face with a star after the two sides that are pushed together. The outer face works the same.")
        assertSays(HelpPage.GENERAL, "(for the outer face in a corner of the canvas)")
        assertSays(HelpPage.GENERAL, "one Create digon entry per pair of circles that can create a digon there (several ways: a submenu)")
        assertSays(HelpPage.GENERAL, "they are listed along circle i (the smaller number), in the order of its sequence, then along circle j.")
        assertTrue("\"Create digon i j\" per pair" in guide && "listed along circle i" in guide, "USER_GUIDE.md: right-click creates, order")
        assertSays(HelpPage.GENERAL, "the two sides of a digon create in orange")
        // orange: red > green > blue
        assertTrue(HIGHLIGHT_SIDE.red > HIGHLIGHT_SIDE.green && HIGHLIGHT_SIDE.green > HIGHLIGHT_SIDE.blue)
        assertSays(HelpPage.WIDGETS, "A dashed edge: the two circles do not cross but can create a digon.")
        assertSays(HelpPage.WIDGETS, "A yellow stripe marks every pair")
        assertSays(HelpPage.WIDGETS, "A triple with two or more triangle faces is twice as strong.")
        // yellow: red and green high, blue low; transparent so overlaps add up
        for (c in listOf(STRIPE_COLOR, HIGHLIGHT_FILL)) assertTrue(c.red > 0.9f && c.green > 0.8f && c.blue < 0.2f && c.alpha < 1f, "$c is yellow")
        assertTrue(HYPEREDGE_FILL.alpha < 0.5f)
        // the example of the user guide is a real starred option: the chain 1-2-3 (1 and 3 do not cross)
        val chain = Arrangement.parse("{1: [2, -2], 2: [1, -1, 3, -3], 3: [2, -2]}")
        val entries = chain.digonCreates().map { op -> chain.cellDescription(op.face, setOf(op.dartI, op.dartJ)) }
        val example = "1* 2 3* 2"
        assertTrue(example in entries, "$entries")
        assertTrue("`$example`" in guide, "USER_GUIDE.md: create menu example")
        assertTrue("- Triangles (the triangle hypergraph): " in guide && "dashed for a non-crossing pair" in guide, "USER_GUIDE.md: graph views")
    }
}
