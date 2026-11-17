package org.bytefred.arrview.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.bytefred.arrview.core.Arrangement
import org.bytefred.arrview.core.DrawModel
import org.bytefred.arrview.core.Examples
import org.bytefred.arrview.core.FileFormat
import org.bytefred.arrview.core.InvalidArrangementException
import org.bytefred.arrview.core.LayoutSettings
import org.bytefred.arrview.core.LinhartOrtner
import org.bytefred.arrview.core.TextFormat
import org.bytefred.arrview.core.digonCollapse
import org.bytefred.arrview.core.hasModel1
import org.bytefred.arrview.core.readArrangement
import org.bytefred.arrview.core.relabelled
import org.bytefred.arrview.core.triangleFlip
import java.io.File
import java.nio.file.Files
import kotlin.random.Random
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Longer editing sessions on the state: flip / collapse walks with undo and redo on several
 * examples, the model after edits, invalid editor input with its reason, names, remembered and
 * corrupt display settings, the desktop file helpers.
 */
class EditingSessionTest {
    private fun state() = AppState(CoroutineScope(SupervisorJob() + Dispatchers.Default)).also { it.settle() }

    private fun AppState.settle() = runBlocking { awaitLayout() }

    @BeforeTest
    fun clearPrefs() = clearInMemoryPrefs()

    private fun example(name: String, index: Int = 0) = Examples.byName(name)!!.let { it.memberName(index) to it.arrangement(index) }

    @Test
    fun flipAndCollapseWalksUndoAndRedo() {
        val random = Random(17)
        val walks = listOf(
            example("n6-digonfree-nonr-8-triangles"),
            example("n3-intersecting-nonkrupp"),
            example("n4-intersecting-all", 5),
            example("n5-connected-all", 400),
            example("n5-connected-nonr-2"),
            example("n12-great-fueredi-palasti"),
            example("n9-great-nonr-pappus"),
        )
        for ((name, loaded) in walks) {
            val s = state()
            s.load(name, loaded)
            val history = mutableListOf(loaded)
            var refused = 0
            repeat(STEPS) {
                val arr = s.arrangement
                val f = arr.faces.indices.filter { arr.faces[it].size <= 3 }.random(random)
                if (arr.faces[f].size == 3) {
                    s.flipTriangle(f)
                    assertEquals(arr.triangleFlip(f), s.arrangement, name)
                    assertEquals("Flipped a triangle", s.status?.text)
                } else {
                    val next = arr.digonCollapse(f)
                    s.collapseDigon(f)
                    if (next == null) {
                        refused++
                        assertEquals(arr, s.arrangement, name)
                        assertEquals("the arrangement would fall apart", s.status?.text)
                    } else {
                        assertEquals(next, s.arrangement, name)
                        assertEquals("Collapsed a digon", s.status?.text)
                    }
                }
                if (s.arrangement != arr) history.add(s.arrangement)
                assertNull(LinhartOrtner.check(s.arrangement.lists), name)
                assertEquals(loaded.n, s.arrangement.n)
                assertEquals(if (s.arrangement.hasModel1) DrawModel.MODEL1 else DrawModel.MODEL2, s.model, name)
                assertEquals(if (s.arrangement == loaded) name else "$name (modified)", s.title)
            }
            // where every collapse would disconnect the arrangement, most steps are refused
            assertTrue(history.size > 1 && history.size + refused > STEPS / 2, "$name: ${history.size} changes, $refused refused")
            // undo all the way back, then redo all the way forward
            for (k in history.indices.reversed()) {
                assertEquals(history[k], s.arrangement, "$name undo $k")
                if (k > 0) s.undo()
            }
            assertEquals(name, s.title)
            assertTrue(s.canUndo, "the load is undoable too")
            for (k in 1 until history.size) {
                s.redo()
                assertEquals(history[k], s.arrangement, "$name redo $k")
            }
            assertFalse(s.canRedo)
            // the drawing follows: the current arrangement in the model it allows
            s.settle()
            assertEquals(s.arrangement, s.currentDrawing?.arrangement, name)
        }
    }

    @Test
    fun theModelAfterEditsAndBackWithUndo() {
        val s = state()
        // an arrangement with a triangle whose flip keeps a digon
        val set = Examples.byName("n4-intersecting-all")!!
        val (arr, t) = (0 until set.size).map(set::arrangement).firstNotNullOf { a ->
            a.faces.indices.firstOrNull { a.faces[it].size == 3 && a.triangleFlip(it).digonCount > 0 }?.let { a to it }
        }
        s.load("n4", arr)
        s.changeModel(DrawModel.MODEL2)
        s.flipTriangle(t)
        s.settle()
        assertEquals(DrawModel.MODEL2, s.currentDrawing?.model, "the chosen model 2 stays after a flip")
        s.changeModel(DrawModel.MODEL1)
        s.settle()
        assertEquals(DrawModel.MODEL1, s.currentDrawing?.model)
        // a collapse leaves model 1 (not intersecting); the choice comes back with undo
        s.collapseDigon(s.arrangement.faces.indices.first { s.arrangement.faces[it].size == 2 })
        s.settle()
        assertEquals(DrawModel.MODEL2, s.currentDrawing?.model)
        assertEquals(DrawModel.MODEL1, s.preferredModel)
        s.undo()
        s.settle()
        assertEquals(DrawModel.MODEL1, s.currentDrawing?.model)
    }

    @Test
    fun invalidEditorInputGivesTheReason() {
        val s = state()
        val start = s.arrangement
        // the text and a part of the reason the error dialog must give
        val cases = listOf(
            Triple(TextFormat.SEQUENCES, "{1: [2, -2], 2: [1, -1}", "expected ',' or ']'"),
            Triple(TextFormat.SEQUENCES, "{1: [2, -2], 3: [1, -1]}", "1..2"),
            Triple(TextFormat.SEQUENCES, "{1: [1, -1], 2: []}", "Def. 2.4 (i)"),
            Triple(TextFormat.SEQUENCES, "{1: [2, 2], 2: [-1, -1]}", "Def. 2.4 (ii)"),
            Triple(TextFormat.SEQUENCES, "{1: [2], 2: [-1]}", "Def. 2.5 (v)"),
            Triple(TextFormat.SEQUENCES, "{1: [2, -2], 2: [1, -1], 3: [4, -4], 4: [3, -3]}", "Def. 2.4 (iv)"),
            Triple(TextFormat.SEQUENCES, "{1: [2, 3, -2, -3], 2: [1, -3, -1, 3], 3: [1, -2, 2, -1]}", "Def. 5.1, Thm. 5.4"),
            Triple(TextFormat.MODEL1, ":Fooo", ""),
            Triple(TextFormat.MODEL1, "hello", ""),
            Triple(TextFormat.MODEL2, ":Fooo", ""),
            Triple(TextFormat.MODEL2, "", ""),
        )
        for ((format, text, part) in cases) {
            val reason = assertFailsWith<InvalidArrangementException>("$format $text") { format.read(text) }.message!!
            assertTrue(part in reason && reason.isNotBlank(), "$text: $reason")
            s.editDraft(format, text)
            s.applyDraft(format)
            assertEquals("Cannot apply the ${format.label.lowercase()}: $reason", s.error)
            assertEquals(text, s.drafts[format], "the edit stays to be fixed")
            assertEquals(start, s.arrangement)
            assertFalse(s.canUndo)
            s.error = null
            s.revertDraft(format)
        }
    }

    @Test
    fun applyingTheSameArrangementInOtherWordsOnlyDropsTheDraft() {
        val s = state()
        // the one-line form of the current sequences: other text, same arrangement
        s.editDraft(TextFormat.SEQUENCES, s.arrangement.toString())
        assertTrue(s.unappliedDraft)
        s.applyDraft(TextFormat.SEQUENCES)
        assertFalse(s.unappliedDraft)
        assertFalse(s.canUndo)
        assertNull(s.error)
        // other circle numbers: a change, one undo step
        val swapped = s.arrangement.relabelled(listOf(5, 4, 3, 2, 1, 0))
        s.editDraft(TextFormat.SEQUENCES, swapped.toString())
        s.applyDraft(TextFormat.SEQUENCES)
        assertEquals(swapped, s.arrangement)
        assertEquals("Applied the sequences", s.status?.text)
        s.undo()
        assertEquals(Examples.start.arrangement(), s.arrangement)
        assertFalse(s.canUndo)
    }

    @Test
    fun nameAndSavedStateAfterEdits() {
        val s = state()
        val start = s.arrangement
        val t = start.faces.indices.first { start.faces[it].size == 3 && it != s.outerFace }
        s.flipTriangle(t)
        assertTrue(s.title.endsWith(" (modified)"))
        // saving makes it clean, but it stays modified against the loaded example
        s.beginSave(FileFormat.TXT)
        s.fileSaved("x.txt")
        assertFalse(s.unsavedChanges)
        assertTrue(s.title.endsWith(" (modified)"))
        assertEquals("Saved x.txt", s.status?.text)
        // flipping the triangle back gives the loaded arrangement: no longer modified, but unsaved
        val flipped = s.arrangement
        s.flipTriangle(flipped.faces.indices.first { flipped.faces[it].size == 3 && flipped.triangleFlip(it) == start })
        assertEquals(start, s.arrangement)
        assertEquals("n6-digonfree-nonr-8-triangles", s.title)
        assertTrue(s.arrangementUnsaved)
    }

    @Test
    fun fileNamesForSaving() {
        val s = state()
        val (name, arr) = example("n5-intersecting-all", 16)
        s.load(name, arr)
        assertEquals("n5-intersecting-all_#17", s.fileName)
        assertEquals("n5-intersecting-all_#17.json", s.saveName(FileFormat.JSON))
        s.open("a b.json", """{"name": "Krupp / great (3)", "sequences": ${Arrangement.krupp().lists}}""".encodeToByteArray())
        assertEquals("Krupp / great (3)", s.name)
        assertEquals("Krupp_great_3_.txt", s.saveName(FileFormat.TXT))
        assertEquals("Opened a b.json", s.status?.text)
        // the saved JSON reads back with its name
        val json = readArrangement(s.beginSave(FileFormat.JSON).decodeToString())
        assertEquals("Krupp / great (3)", json.name)
        assertEquals(Arrangement.krupp(), json.arrangement)
    }

    @Test
    fun widgetsDividersAndLayoutModeAreRemembered() {
        val s = state()
        s.openWidgets.clear()
        s.openWidgets.addAll(listOf(Widget.LAYOUT, Widget.SEQUENCES))
        s.layoutMode = LayoutMode.PORTRAIT
        s.wideWeights = mapOf(null to 3f, Widget.LAYOUT to 1.5f)
        s.stackedWeights = mapOf(null to 1f, Widget.SEQUENCES to 0.5f)
        s.saveDisplayPrefs()
        val t = state()
        assertEquals(listOf(Widget.LAYOUT, Widget.SEQUENCES), t.openWidgets.toList())
        assertEquals(LayoutMode.PORTRAIT, t.layoutMode)
        assertEquals(mapOf(null to 3f, Widget.LAYOUT to 1.5f), t.wideWeights)
        assertEquals(mapOf(null to 1f, Widget.SEQUENCES to 0.5f), t.stackedWeights)
        // all widgets closed is remembered too
        t.openWidgets.clear()
        t.saveDisplayPrefs()
        assertTrue(state().openWidgets.isEmpty())
    }

    @Test
    fun corruptSettingsFallBackToTheDefaults() {
        savePref(LAYOUT_PREF, "SIDEWAYS")
        savePref(OPEN_WIDGETS_PREF, "BOGUS,LAYOUT,LAYOUT,,PROPERTIES")
        savePref(WIDE_WEIGHTS_PREF, "canvas=abc,PROPERTIES=-1,LAYOUT=1e9,SEQUENCES=0.0001,junk")
        savePref(STACKED_WEIGHTS_PREF, "=")
        savePref(MODEL_PREF, "MODEL3")
        savePref(MAX_ITERATIONS_PREF, "0")
        savePref(EPS_PREF, "NaN")
        val s = state()
        assertEquals(LayoutMode.AUTO, s.layoutMode)
        assertEquals(listOf(Widget.LAYOUT, Widget.PROPERTIES), s.openWidgets.toList())
        // shares are clamped to 0.01..100, unreadable ones dropped
        assertEquals(mapOf<Widget?, Float>(Widget.LAYOUT to 100f, Widget.SEQUENCES to 0.01f), s.wideWeights)
        assertTrue(s.stackedWeights.isEmpty())
        assertEquals(DrawModel.MODEL1, s.preferredModel)
        assertEquals(LayoutSettings(), s.layoutSettings)
        assertNotNull(s.currentDrawing)
    }

    @Test
    fun desktopFileHelpers() {
        val dir = Files.createTempDirectory("arrview-test").toFile()
        try {
            val errors = mutableListOf<String>()
            var saved: String? = "none"
            val f = File(dir, "k.json")
            writeFile(f, "{}".encodeToByteArray(), { errors.add(it) }) { saved = it }
            assertEquals("k.json", saved)
            var opened: Pair<String, String>? = null
            readFile(f, { errors.add(it) }) { name, bytes -> opened = name to bytes.decodeToString() }
            assertEquals("k.json" to "{}", opened)
            assertTrue(errors.isEmpty())
            // errors are reported, not thrown
            readFile(File(dir, "missing.txt"), { errors.add(it) }) { _, _ -> error("not opened") }
            writeFile(File(dir, "no/such/dir/x.txt"), byteArrayOf(), { errors.add(it) }) { saved = it }
            assertEquals(2, errors.size)
            assertTrue(errors[0].startsWith("Cannot open missing.txt: "), errors[0])
            assertTrue(errors[1].startsWith("Cannot save x.txt: "), errors[1])
            assertNull(saved)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun infoFadesAfterThreeSecondsNotesStay() {
        val scope = TestScope()
        val s = AppState(scope, Dispatchers.Default)
        s.showInfo("Flipped a triangle")
        assertTrue(s.status!!.transient)
        scope.advanceTimeBy(AppState.INFO_DURATION_MS - 1)
        scope.runCurrent()
        assertEquals("Flipped a triangle", s.status?.text)
        scope.advanceTimeBy(2)
        assertNull(s.status)
        // a newer message is not cleared by the older one's timer
        s.showInfo("one")
        scope.advanceTimeBy(2000)
        s.showInfo("two")
        scope.advanceTimeBy(1500)
        assertEquals("two", s.status?.text)
        s.showNote("the arrangement would fall apart")
        assertFalse(s.status!!.transient)
        scope.advanceTimeBy(60_000)
        assertEquals("the arrangement would fall apart", s.status?.text)
        scope.cancel()
    }

    @Test
    fun undoRedoAndModelSwitchesUseTheDrawingCache() {
        val s = state()
        val start = s.arrangement
        s.flipTriangle(start.faces.indices.first { start.faces[it].size == 3 && it != s.outerFace })
        s.settle()
        s.changeModel(DrawModel.MODEL2)
        s.settle()
        // back to drawings computed before: there at once, no layout to wait for
        s.changeModel(DrawModel.MODEL1)
        assertEquals(DrawModel.MODEL1, s.currentDrawing?.model)
        s.undo()
        assertEquals(start, s.currentDrawing?.arrangement)
        s.redo()
        assertEquals(s.arrangement, s.currentDrawing?.arrangement)
    }

    private companion object {
        const val STEPS = 25
    }
}
