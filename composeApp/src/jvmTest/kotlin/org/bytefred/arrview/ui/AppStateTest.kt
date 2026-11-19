package org.bytefred.arrview.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.bytefred.arrview.core.Arrangement
import org.bytefred.arrview.core.DrawModel
import org.bytefred.arrview.core.Examples
import org.bytefred.arrview.core.Model1
import org.bytefred.arrview.core.PrimalGraph
import org.bytefred.arrview.core.digonCollapse
import org.bytefred.arrview.core.faceKey
import org.bytefred.arrview.core.faceWithKey
import org.bytefred.arrview.core.triangleFlip
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppStateTest {
    private val start = Examples.start.arrangement()

    private fun state() = AppState(CoroutineScope(SupervisorJob() + Dispatchers.Default)).also { it.settle() }

    private fun AppState.settle() = runBlocking { awaitLayout() }

    @BeforeTest
    fun clearPrefs() = clearInMemoryPrefs()

    /** A triangle that is not the outer face. */
    private fun AppState.innerTriangle(): Int = arrangement.faces.indices.first { arrangement.faces[it].size == 3 && it != outerFace }

    @Test
    fun startsWithTheStartExampleInModel1() {
        val s = state()
        assertEquals(start, s.arrangement)
        assertEquals("n6-digonfree-nonr-8-triangles", s.title)
        assertEquals(DrawModel.MODEL1, s.model)
        val d = assertNotNull(s.drawing)
        assertEquals(start, d.arrangement)
        assertEquals(DrawModel.MODEL1, d.model)
    }

    @Test
    fun clickFlipsATriangleAndUndoRedo() {
        val s = state()
        val t = s.innerTriangle()
        s.clickFace(t)
        s.settle()
        assertEquals(start.triangleFlip(t), s.arrangement)
        assertEquals("n6-digonfree-nonr-8-triangles (modified)", s.title)
        assertEquals(s.arrangement, s.drawing?.arrangement)
        assertTrue(s.canUndo)
        assertFalse(s.canRedo)
        s.undo()
        s.settle()
        assertEquals(start, s.arrangement)
        assertEquals("n6-digonfree-nonr-8-triangles", s.title)
        assertTrue(s.canRedo)
        s.redo()
        assertEquals(start.triangleFlip(t), s.arrangement)
    }

    @Test
    fun clickOnTheOuterFaceOrAQuadrangleDoesNothing() {
        val s = state()
        // a quadrangle drawn outside (the default outer face, the most symmetric one, is a triangle)
        s.makeOuterFace(start.faces.indices.first { start.faces[it].size == 4 })
        s.settle()
        s.clickFace(assertNotNull(s.outerFace))
        s.clickFace(start.faces.indices.first { start.faces[it].size == 4 && it != s.outerFace })
        assertEquals(start, s.arrangement)
        // only Make outer face is undoable
        s.undo()
        assertFalse(s.canUndo)
    }

    @Test
    fun digonCollapseIsRefusedWhenTheArrangementFallsApart() {
        val s = state()
        s.load("two", Arrangement.two())
        s.settle()
        assertEquals(DrawModel.MODEL2, s.model, "two circles have no model 1")
        val digon = s.arrangement.faces.indices.first { it != s.outerFace }
        s.clickFace(digon)
        assertEquals(Arrangement.two(), s.arrangement)
        assertEquals("the arrangement would fall apart", s.status?.text)
        assertTrue(s.canUndo, "only the load is undoable")
        s.undo()
        assertEquals(start, s.arrangement)
    }

    @Test
    fun digonCollapseSwitchesToModel2() {
        val s = state()
        val arr = Examples.byName("n3-intersecting-all")!!.let { e -> (0 until e.size).map(e::arrangement).first { it.digonCount > 0 } }
        s.load("n3", arr)
        s.settle()
        assertEquals(DrawModel.MODEL1, s.model)
        val digon = arr.faces.indices.first { arr.faces[it].size == 2 }
        s.clickFace(digon)
        s.settle()
        assertEquals(arr.digonCollapse(digon), s.arrangement)
        assertEquals(DrawModel.MODEL2, s.model, "no longer intersecting")
        assertEquals(DrawModel.MODEL2, s.drawing?.model)
        s.undo()
        s.settle()
        assertEquals(DrawModel.MODEL1, s.model, "the chosen model comes back")
    }

    @Test
    fun theOuterFaceIsKeptByAFlipElsewhere() {
        val s = state()
        // a quadrangle as the outer face, and a triangle without a corner on it
        val vertexOf = PrimalGraph(start).vertexOfCrossing
        fun corners(f: Int) = start.faces[f].map { vertexOf[it / 2] }.toSet()
        val outer = start.faces.indices.first { f -> start.faces[f].size == 4 && f != s.outerFace }
        val t = start.faces.indices.first { start.faces[it].size == 3 && corners(it).none { v -> v in corners(outer) } }
        s.makeOuterFace(outer)
        s.settle()
        assertEquals(outer, s.outerFace)
        s.flipTriangle(t)
        s.settle()
        assertEquals(s.arrangement.faceWithKey(start.faceKey(outer)), s.outerFace)
        s.undo()
        s.settle()
        assertEquals(outer, s.outerFace)
    }

    @Test
    fun openReadsSequencesAndSparse6AndReportsErrors() {
        val s = state()
        s.open("two.txt", "{1: [2, -2], 2: [1, -1]}".encodeToByteArray())
        assertEquals(Arrangement.two(), s.arrangement)
        assertEquals("two", s.title)
        assertNull(s.error)
        s.open("krupp.mod1s6", Model1.encode(Arrangement.krupp()).encodeToByteArray())
        assertTrue(s.arrangement.isIsomorphicTo(Arrangement.krupp()))
        s.open("bad.txt", "{1: [2], 2: [1]}".encodeToByteArray())
        assertTrue(s.error!!.startsWith("Cannot open bad.txt"), s.error)
        assertTrue(s.arrangement.isIsomorphicTo(Arrangement.krupp()))
    }

    @Test
    fun modelPreferenceIsRemembered() {
        val s = state()
        s.changeModel(DrawModel.MODEL2)
        s.saveDisplayPrefs()
        assertEquals(DrawModel.MODEL2, state().model)
    }
}
