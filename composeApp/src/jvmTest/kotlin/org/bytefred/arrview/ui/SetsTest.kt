package org.bytefred.arrview.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.bytefred.arrview.core.Arrangement
import org.bytefred.arrview.core.Examples
import org.bytefred.arrview.core.FileFormat
import org.bytefred.arrview.core.Model1
import org.bytefred.arrview.core.Model2
import org.bytefred.arrview.core.ArrangementFile
import org.bytefred.arrview.core.TextFormat
import org.bytefred.arrview.core.arrangementsFile
import org.bytefred.arrview.core.readArrangements
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Files and example sets with several arrangements, as PoCoView's sets (Manfred, 2026-11-19). */
@OptIn(ExperimentalTestApi::class)
class SetsTest {
    private fun state() = AppState(CoroutineScope(SupervisorJob() + Dispatchers.Default)).also { it.settle() }

    private fun AppState.settle() = runBlocking { awaitLayout() }

    @BeforeTest
    fun clearPrefs() = clearInMemoryPrefs()

    private val n4 = Examples.byName("n4-intersecting-all")!!

    private fun AppState.flipFirstTriangle() = flipTriangle(arrangement.faces.indices.first { arrangement.faces[it].size == 3 })

    @Test
    fun aFileWithSeveralLinesKeepsAllArrangements() {
        val s = state()
        val lines = (0 until n4.size).joinToString("\n") { n4.text(it) }
        s.open("all4.enc", lines.encodeToByteArray())
        assertNull(s.error)
        assertEquals(n4.size, s.arrangementSet.size)
        assertEquals(0, s.setIndex)
        assertEquals(n4.arrangement(0), s.arrangement)
        assertEquals("all4 · set 1/${n4.size}", s.title)
        s.showSet(3)
        assertEquals(n4.arrangement(3), s.arrangement)
        assertEquals("all4 · set 4/${n4.size}", s.title)
        // out of range: nothing happens
        s.showSet(n4.size)
        s.showSet(-1)
        assertEquals(3, s.setIndex)
    }

    @Test
    fun importAndTheFormatsOfSeveralArrangements() {
        val arrs = listOf(Arrangement.krupp(), Arrangement.two(), n4.arrangement(2))
        // sparse6 lines (model 1 and model 2 mixed), sequences one after the other, a JSON list
        val texts = listOf(
            arrs.joinToString("\n") { if (it.isIntersecting && it.n > 2) Model1.encode(it) else Model2.encode(it) },
            arrs.joinToString("\n\n") { it.toString() },
            arrangementsFile("x", arrs, FileFormat.JSON),
            arrangementsFile("x", arrs, FileFormat.TXT),
        )
        for (text in texts) {
            val read = readArrangements(text)
            assertEquals(3, read.size, text)
            for ((a, b) in arrs.zip(read)) assertTrue(a.isIsomorphicTo(b.arrangement), text)
        }
        val s = state()
        s.loadImport(assertNotNullList(s.parseImport(texts[1])))
        assertEquals(3, s.arrangementSet.size)
        assertEquals("imported · set 1/3", s.title)
        // an invalid member names its number
        assertNull(s.parseImport(texts[1] + "\n{1: [2], 2: [1]}"))
        assertTrue(s.error!!.contains("arrangement 4 of 4"), s.error)
    }

    private fun assertNotNullList(f: ArrangementFile?) = f ?: error("not read")

    @Test
    fun editsOfMembersAreKeptAndSavedTogether() {
        val s = state()
        s.loadSet(n4.name, ArrangementSet(n4.size, member = n4::arrangement), 2)
        assertEquals("n4-intersecting-all · set 3/8", s.title)
        assertFalse(s.unsavedChanges)
        s.flipFirstTriangle()
        val edited = s.arrangement
        assertEquals("n4-intersecting-all · set 3/8 (modified)", s.title)
        assertTrue(s.canUndo)
        s.showSet(3)
        // the history belongs to one member: cleared; the edit is kept and still unsaved
        assertFalse(s.canUndo)
        assertEquals(n4.arrangement(3), s.arrangement)
        assertTrue(s.arrangementUnsaved)
        s.showSet(2)
        assertEquals(edited, s.arrangement)
        // leaving asks; Save writes every member, the edited one included
        var left = false
        s.leave { left = true }
        assertFalse(left)
        val text = s.beginSave(FileFormat.TXT).decodeToString()
        val members = readArrangements(text).map { it.arrangement }
        assertEquals(n4.size, members.size)
        assertEquals(edited, members[2])
        assertEquals(n4.arrangement(5), members[5])
        s.fileSaved("x.txt")
        assertTrue(left, "the pending action goes on after the save")
        assertFalse(s.unsavedChanges)
        // undo of the load brings the start example back
        s.loadSet(n4.name, ArrangementSet(n4.size, member = n4::arrangement), 0)
        s.undo()
        assertEquals(edited, s.arrangement)
    }

    @Test
    fun anUnappliedDraftAsksBeforeTheSwitch() {
        val s = state()
        s.loadSet(n4.name, ArrangementSet(n4.size, member = n4::arrangement), 0)
        s.editDraft(TextFormat.SEQUENCES, "{1: [2, -2], 2: [1, -1]}")
        s.showSet(1)
        assertEquals(0, s.setIndex)
        assertTrue(s.pendingLeave != null)
        s.discardAndLeave()
        assertEquals(1, s.setIndex)
        assertTrue(s.drafts.isEmpty())
    }

    @Test
    fun makeOuterFaceIsUndoable() {
        val s = state()
        val first = s.outerFace!!
        val other = s.arrangement.faces.indices.first { it != first && s.arrangement.faces[it].size >= 3 }
        s.makeOuterFace(other)
        s.settle()
        assertEquals(other, s.outerFace)
        assertTrue(s.canUndo)
        s.undo()
        s.settle()
        assertEquals(first, s.outerFace)
        s.redo()
        s.settle()
        assertEquals(other, s.outerFace)
        // the same face again is no step
        s.makeOuterFace(other)
        s.undo()
        s.settle()
        assertEquals(first, s.outerFace)
        assertFalse(s.canUndo)
    }

    @Test
    fun exampleSetNavigationInTheToolbarAndByKeys() = runComposeUiTest {
        val s = AppState(CoroutineScope(SupervisorJob() + Dispatchers.Default))
        setContent { App(s, initialDialog = DialogKind.NONE) }
        // a single arrangement: no navigation
        onNodeWithTag("set-position").assertDoesNotExist()
        // the Examples dialog loads the whole set at the chosen member
        onNodeWithTag("menu-Arrangement").performClick()
        onNodeWithText("Examples...").performClick()
        onNodeWithText("Database").performClick()
        onNodeWithText("Intersecting, n = 4 (8)").performScrollTo().performClick()
        // below the database's other sets: scrolled into view first
        onNodeWithText("+").performScrollTo().performClick()
        onNodeWithText("+").performClick()
        onNodeWithText("Load").performClick()
        waitForIdle()
        // the notice after loading several arrangements (PoCoView's)
        onNodeWithText("Showing arrangement 3 of 8.", substring = true).assertExists()
        onNodeWithText("OK").performClick()
        waitForIdle()
        assertEquals(n4.name, s.name)
        assertEquals(2, s.setIndex)
        assertEquals(n4.arrangement(2), s.arrangement)
        onNodeWithText("3 / 8").assertExists()
        onNodeWithTag("set-next").performClick()
        waitForIdle()
        assertEquals(n4.arrangement(3), s.arrangement)
        onNodeWithText("4 / 8").assertExists()
        onNodeWithTag("set-previous").performClick()
        onNodeWithTag("set-previous").performClick()
        onNodeWithTag("set-previous").performClick()
        waitForIdle()
        assertEquals(0, s.setIndex)
        onNodeWithTag("set-previous").assertIsNotEnabled()
        // after the toolbar button the keys still work
        onNodeWithTag("set-next").performClick()
        waitForIdle()
        onNodeWithTag("canvas").performKeyInput { pressKey(Key.PageDown) }
        waitForIdle()
        assertEquals(2, s.setIndex)
    }

    @Test
    fun setNavigationByKeys() = runComposeUiTest {
        val s = AppState(CoroutineScope(SupervisorJob() + Dispatchers.Default))
        setContent { App(s, initialDialog = DialogKind.NONE) }
        runOnIdle { s.loadSet(n4.name, ArrangementSet(n4.size, member = n4::arrangement)) }
        waitForIdle()
        onNodeWithText("8 arrangements loaded").assertExists()
        onNodeWithText("Showing the first of 8.", substring = true).assertExists()
        onNodeWithText("OK").performClick()
        waitForIdle()
        // keys: PageDown / . next, PageUp / , previous
        onNodeWithTag("canvas").performKeyInput { pressKey(Key.PageDown) }
        onNodeWithTag("canvas").performKeyInput { pressKey(Key.Period) }
        waitForIdle()
        assertEquals(2, s.setIndex)
        onNodeWithTag("canvas").performKeyInput { pressKey(Key.PageUp) }
        waitForIdle()
        assertEquals(1, s.setIndex)
        onNodeWithTag("canvas").performKeyInput { pressKey(Key.Comma) }
        waitForIdle()
        assertEquals(0, s.setIndex)
        assertEquals(n4.arrangement(0), s.arrangement)
    }
}
