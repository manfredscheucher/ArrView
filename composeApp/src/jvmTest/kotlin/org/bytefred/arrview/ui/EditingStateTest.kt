package org.bytefred.arrview.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.bytefred.arrview.core.Arrangement
import org.bytefred.arrview.core.Examples
import org.bytefred.arrview.core.FileFormat
import org.bytefred.arrview.core.LayoutSettings
import org.bytefred.arrview.core.Model2
import org.bytefred.arrview.core.TextFormat
import org.bytefred.arrview.core.readArrangement
import org.bytefred.arrview.core.relabelled
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Widget drafts (Apply / Revert), "Save changes?", saving and remembered layout settings. */
class EditingStateTest {
    private val start = Examples.start.arrangement()

    private fun state() = AppState(CoroutineScope(SupervisorJob() + Dispatchers.Default)).also { runBlocking { it.awaitLayout() } }

    @BeforeTest
    fun clearPrefs() = clearInMemoryPrefs()

    @Test
    fun applyingEditedSequencesIsUndoable() {
        val s = state()
        val relabelled = start.relabelled(listOf(1, 0, 2, 3, 4, 5))
        s.editDraft(TextFormat.SEQUENCES, relabelled.toString())
        assertTrue(s.unappliedDraft)
        assertTrue(s.unsavedChanges)
        s.applyDraft(TextFormat.SEQUENCES)
        assertEquals(relabelled, s.arrangement)
        assertFalse(s.unappliedDraft)
        assertNull(s.error)
        s.undo()
        assertEquals(start, s.arrangement)
    }

    @Test
    fun invalidSequencesGiveTheReasonAndKeepTheDraft() {
        val s = state()
        s.editDraft(TextFormat.SEQUENCES, "{1: [2, -2], 2: [1]}")
        s.applyDraft(TextFormat.SEQUENCES)
        val error = assertNotNull(s.error)
        assertTrue("Linhart" in error, error)
        assertEquals(start, s.arrangement)
        assertTrue(s.unappliedDraft)
        s.revertDraft(TextFormat.SEQUENCES)
        assertFalse(s.unappliedDraft)
    }

    @Test
    fun textEqualToTheCurrentOneIsNoDraft() {
        val s = state()
        s.editDraft(TextFormat.MODEL2, s.text(TextFormat.MODEL2) + "x")
        assertTrue(s.unappliedDraft)
        s.editDraft(TextFormat.MODEL2, s.text(TextFormat.MODEL2))
        assertFalse(s.unappliedDraft)
    }

    @Test
    fun applyingSparse6() {
        val s = state()
        s.editDraft(TextFormat.MODEL2, Model2.encode(Arrangement.krupp()))
        s.applyDraft(TextFormat.MODEL2)
        assertTrue(s.arrangement.isIsomorphicTo(Arrangement.krupp()))
        s.editDraft(TextFormat.MODEL1, ":Fooo")
        s.applyDraft(TextFormat.MODEL1)
        assertNotNull(s.error)
    }

    @Test
    fun leaveAsksOnlyWithUnsavedChanges() {
        val s = state()
        var left = 0
        s.leave { left++ }
        assertEquals(1, left)
        s.flipTriangle(s.arrangement.faces.indices.first { s.arrangement.faces[it].size == 3 && it != s.outerFace })
        s.leave { left++ }
        assertEquals(1, left)
        assertNotNull(s.pendingLeave)
        s.cancelLeave()
        assertNull(s.pendingLeave)
        s.leave { left++ }
        s.discardAndLeave()
        assertEquals(2, left)
    }

    @Test
    fun savingGoesOnWithThePendingAction() {
        val s = state()
        s.flipTriangle(s.arrangement.faces.indices.first { s.arrangement.faces[it].size == 3 && it != s.outerFace })
        val flipped = s.arrangement
        var left = false
        s.leave { left = true }
        val bytes = s.beginSave(FileFormat.JSON)
        assertEquals(flipped, readArrangement(bytes.decodeToString()).arrangement)
        s.fileSaved("x.json")
        assertTrue(left)
        assertFalse(s.arrangementUnsaved)
        // a cancelled save cancels the pending action
        s.flipTriangle(s.arrangement.faces.indices.first { s.arrangement.faces[it].size == 3 && it != s.outerFace })
        left = false
        s.leave { left = true }
        s.beginSave(FileFormat.TXT)
        s.fileSaved(null)
        assertFalse(left)
        assertNull(s.pendingLeave)
        assertTrue(s.arrangementUnsaved)
    }

    @Test
    fun loadDropsDraftsAndIsClean() {
        val s = state()
        s.editDraft(TextFormat.SEQUENCES, "garbage")
        val set = Examples.byName("n4-intersecting-all")!!
        s.load(set.memberName(3), set.arrangement(3))
        assertFalse(s.unsavedChanges)
        assertEquals("n4-intersecting-all #4", s.title)
    }

    @Test
    fun layoutSettingsAreRemembered() {
        val s = state()
        s.changeLayoutSettings(LayoutSettings(maxIterations = 30, eps = 1e-5))
        s.saveDisplayPrefs()
        assertEquals(LayoutSettings(30, 1e-5), state().layoutSettings)
        assertEquals("1e-5", epsText(1e-5))
        assertEquals("1e-8", epsText(LayoutSettings().eps))
    }
}
