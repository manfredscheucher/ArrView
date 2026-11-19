package org.bytefred.arrview.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.bytefred.arrview.core.Arrangement
import org.bytefred.arrview.core.Examples
import org.bytefred.arrview.core.LayoutSettings
import org.bytefred.arrview.core.Model2
import org.bytefred.arrview.core.intersectionGraph
import org.bytefred.arrview.core.relabelled
import org.bytefred.arrview.core.sequencesText
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The widgets driven by clicks and typing in the real app. */
@OptIn(ExperimentalTestApi::class)
class WidgetsUiTest {
    private val start = Examples.start.arrangement()

    private fun state() = AppState(CoroutineScope(SupervisorJob() + Dispatchers.Default))

    @BeforeTest
    fun clearPrefs() = clearInMemoryPrefs()

    @Test
    fun editSequencesApplyValidAndInvalid() = runComposeUiTest {
        val s = state()
        setContent { App(s, initialDialog = DialogKind.NONE) }
        onNodeWithText("Sequences").performClick()
        val editor = onNodeWithTag("editor-SEQUENCES")
        assertEquals(start.n, editor.text()!!.lines().size)
        onNodeWithTag("apply-SEQUENCES").assertIsNotEnabled()
        // valid: circles 1 and 2 swapped
        val swapped = start.relabelled(listOf(1, 0, 2, 3, 4, 5))
        editor.performTextReplacement(swapped.toString())
        onNodeWithTag("apply-SEQUENCES").performClick()
        waitForIdle()
        assertEquals(swapped, s.arrangement)
        assertNull(s.error)
        // invalid: the error dialog names the Linhart-Ortner condition, nothing changes
        editor.performTextReplacement("{1: [2, -2], 2: [1]}")
        onNodeWithTag("apply-SEQUENCES").performClick()
        onNodeWithText("Error").assertIsDisplayed()
        onNodeWithText("Linhart", substring = true).assertIsDisplayed()
        onNodeWithText("OK").performClick()
        waitForIdle()
        assertNull(s.error)
        assertEquals(swapped, s.arrangement)
        assertTrue(s.unappliedDraft)
        onNodeWithTag("revert-SEQUENCES").performClick()
        waitForIdle()
        assertEquals(sequencesText(swapped), editor.text())
    }

    @Test
    fun editSparse6() = runComposeUiTest {
        val s = state()
        setContent { App(s, initialDialog = DialogKind.NONE) }
        onNodeWithText("Encoding").performClick()
        onNodeWithTag("editor-MODEL2").performTextReplacement(Model2.encode(Arrangement.krupp()))
        onNodeWithTag("apply-MODEL2").performClick()
        waitForIdle()
        assertTrue(s.arrangement.isIsomorphicTo(Arrangement.krupp()))
        // a broken model 1 string: decode error in the error dialog
        onNodeWithTag("editor-MODEL1").performTextReplacement(":Fooo")
        onNodeWithTag("apply-MODEL1").performClick()
        onNodeWithText("Cannot apply the model 1 sparse6", substring = true).assertIsDisplayed()
        onNodeWithText("OK").performClick()
        // not intersecting: no model 1, a note why
        val set = Examples.byName("n3-connected-chain")!!
        runOnIdle { s.load(set.name, set.arrangement(0)) }
        onNodeWithText("Only for intersecting arrangements", substring = true).assertIsDisplayed()
    }

    @Test
    fun intersectionGraphEdgeCount() = runComposeUiTest {
        val s = state()
        setContent { App(s, initialDialog = DialogKind.NONE) }
        onNodeWithText("Intersection graph").performClick()
        onNodeWithTag("intersection-graph").assertIsDisplayed()
        assertEquals("15 of 15 pairs cross", onNodeWithTag("intersection-graph-count").text())
        val set = Examples.byName("n5-connected-all")!!
        val sparse = (0 until set.size).map(set::arrangement).minBy { it.intersectionGraph().size }
        runOnIdle { s.load("sparse", sparse) }
        assertEquals("${sparse.intersectionGraph().size} of 10 pairs cross", onNodeWithTag("intersection-graph-count").text())
    }

    @Test
    fun settingsLayoutFieldsAndReset() = runComposeUiTest {
        val s = state()
        setContent { App(s, initialDialog = DialogKind.NONE) }
        onNodeWithText("Settings").performClick()
        onNodeWithTag("max-iterations").performTextReplacement("25")
        onNodeWithTag("eps").performTextReplacement("1e-5")
        waitForIdle()
        assertEquals(LayoutSettings(25, 1e-5), s.layoutSettings)
        // invalid input is not taken over
        onNodeWithTag("max-iterations").performTextReplacement("0")
        onNodeWithText("at least 1").assertIsDisplayed()
        assertEquals(25, s.layoutSettings.maxIterations)
        onNodeWithText("Reset to defaults").performClick()
        waitForIdle()
        assertEquals(LayoutSettings(), s.layoutSettings)
        assertEquals("50", onNodeWithTag("max-iterations").text())
        assertEquals("1e-8", onNodeWithTag("eps").text())
    }
}
