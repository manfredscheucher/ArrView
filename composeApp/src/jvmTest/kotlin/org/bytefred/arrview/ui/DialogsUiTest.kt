package org.bytefred.arrview.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.bytefred.arrview.core.Arrangement
import org.bytefred.arrview.core.Examples
import org.bytefred.arrview.core.Model1
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Welcome, Examples (with the member chooser), Import and "Save changes?" driven by clicks. */
@OptIn(ExperimentalTestApi::class)
class DialogsUiTest {
    private fun state() = AppState(CoroutineScope(SupervisorJob() + Dispatchers.Default))

    @BeforeTest
    fun clearPrefs() = clearInMemoryPrefs()

    @Test
    fun welcomeLeadsToTheExamplesAndCanBeTurnedOff() = runComposeUiTest {
        val s = state()
        setContent { App(s) }
        onNodeWithText("Welcome to ArrView").assertIsDisplayed()
        onNodeWithText("Interesting arrangements, ideas or general feedback?").assertIsDisplayed()
        onNodeWithText("Don't show again").performClick()
        onNodeWithText("Examples").performClick()
        onNodeWithText("Database").assertIsDisplayed()
        assertEquals("off", loadPref(WELCOME_PREF))
    }

    @Test
    fun loadAListMemberWithTheIndexChooser() = runComposeUiTest {
        val s = state()
        setContent { App(s, initialDialog = DialogKind.NONE) }
        onNodeWithTag("menu-Arrangement").performClick()
        onNodeWithText("Examples...").performClick()
        onNodeWithText("Load").assertIsNotEnabled()
        onNodeWithText("Database").performClick()
        onNodeWithText("Intersecting, n = 4 (8)").performScrollTo().performClick()
        onNodeWithText("n = 4, 8 arrangements").performScrollTo().assertIsDisplayed()
        onNodeWithText("of 8").performScrollTo()
        onNodeWithTag("example-index").performTextReplacement("5")
        onNodeWithText("+").performClick()
        waitForIdle()
        assertEquals("6", onNodeWithTag("example-index").text())
        // out of range: a hint, Load waits
        onNodeWithTag("example-index").performTextReplacement("99")
        onNodeWithText("1 to 8").assertIsDisplayed()
        onNodeWithText("Load").assertIsNotEnabled()
        onNodeWithText("\u2212").performClick()
        waitForIdle()
        assertEquals("5", onNodeWithTag("example-index").text())
        onNodeWithText("Load").performClick()
        waitForIdle()
        val set = Examples.byName("n4-intersecting-all")!!
        assertEquals("n4-intersecting-all", s.name)
        assertEquals("n4-intersecting-all · set 5/8", s.title)
        assertEquals(set.arrangement(4), s.arrangement)
        // the notice after loading several arrangements; "Don't show again" turns it off
        onNodeWithText("8 arrangements loaded").assertExists()
        onNodeWithText("Don't show again").performClick()
        onNodeWithText("OK").performClick()
        waitForIdle()
        assertEquals("off", loadPref(SETS_NOTICE_PREF))
        // the dialog is as it was left
        onNodeWithTag("menu-Arrangement").performClick()
        onNodeWithText("Examples...").performClick()
        assertEquals("5", onNodeWithTag("example-index").text())
    }

    @Test
    fun loadAsksToSaveChanges() = runComposeUiTest {
        val s = state()
        setContent { App(s, initialDialog = DialogKind.NONE) }
        runOnIdle { s.flipSomeTriangle() }
        onNodeWithTag("menu-Arrangement").performClick()
        onNodeWithText("Examples...").performClick()
        onNodeWithText("Krupp, NonKrupp, 3-Chain").performScrollTo().performClick()
        onNodeWithText("Krupp").performScrollTo().performClick()
        onNodeWithText("Load").performClick()
        onNodeWithText("Save changes?").assertIsDisplayed()
        onNodeWithText("Save JSON").assertIsDisplayed()
        onNodeWithText("Save text").assertIsDisplayed()
        // Cancel (of "Save changes?", on top of the Examples dialog) keeps everything
        onAllNodesWithText("Cancel").onLast().performClick()
        waitForIdle()
        assertTrue(s.arrangementUnsaved)
        onNodeWithText("Load").performClick()
        onNodeWithText("Discard").performClick()
        waitForIdle()
        assertEquals(Arrangement.krupp(), s.arrangement)
        assertEquals("n3-great-krupp", s.name)
    }

    @Test
    fun importDetectsSparse6() = runComposeUiTest {
        val s = state()
        setContent { App(s, initialDialog = DialogKind.NONE) }
        onNodeWithTag("menu-Arrangement").performClick()
        onNodeWithText("Import...").performClick()
        onNodeWithTag("import-text").performTextReplacement(Model1.encode(Arrangement.krupp()))
        onNode(hasText("Import") and hasClickAction()).performClick()
        waitForIdle()
        assertTrue(s.arrangement.isIsomorphicTo(Arrangement.krupp()))
        assertEquals("imported", s.name)
    }

    @Test
    fun closingAsksToSaveChanges() = runComposeUiTest {
        val s = state()
        var closes by mutableIntStateOf(0)
        var closed = 0
        setContent { App(s, initialDialog = DialogKind.NONE, closeRequests = closes, onClose = { closed++ }) }
        runOnIdle { s.flipSomeTriangle() }
        closes++
        onNodeWithText("Save changes?").assertIsDisplayed()
        onNodeWithText("Cancel").performClick()
        waitForIdle()
        assertEquals(0, closed)
        closes++
        onNodeWithText("Discard").performClick()
        waitForIdle()
        assertEquals(1, closed)
    }
}
