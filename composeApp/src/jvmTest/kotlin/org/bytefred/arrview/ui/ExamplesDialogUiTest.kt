package org.bytefred.arrview.ui

import kotlin.time.Duration.Companion.minutes
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.bytefred.arrview.core.ExampleGroup
import org.bytefred.arrview.core.Examples
import org.bytefred.arrview.core.Paper
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The Examples dialog driven by clicks: a member of every set, the member chooser, Cancel. */
@OptIn(ExperimentalTestApi::class)
class ExamplesDialogUiTest {
    private fun state() = AppState(CoroutineScope(SupervisorJob() + Dispatchers.Default))

    @BeforeTest
    fun clearPrefs() = clearInMemoryPrefs()

    private fun ComposeUiTest.openExamples() {
        onNodeWithTag("menu-Arrangement").performClick()
        onNodeWithText("Examples...").performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.click(text: String) = onNodeWithText(text).performScrollTo().performClick()

    @Test
    // loads all sets (16 s on an idle machine); the default 1 min limit is too tight under load
    fun aMemberOfEverySetLoadsByClicks() = runComposeUiTest(testTimeout = 5.minutes) {
        val s = state()
        setContent { App(s, initialDialog = DialogKind.NONE) }
        for ((g, group) in ExampleGroup.entries.withIndex()) {
            val sets = Examples.inGroup(group)
            for ((k, set) in sets.withIndex()) {
                openExamples()
                // the groups stay expanded between openings; the sets are below "Sets"
                if (g == 0 && k == 0) click("Sets")
                if (k == 0) click("${group.title} (${sets.size})")
                click(set.name)
                onNodeWithText(set.name).assertIsSelected()
                onNodeWithText(set.description).assertExists()
                val index = if (set.size == 1) {
                    // a single arrangement: only the note, no number selection
                    onNodeWithText("n = ${set.n}, 1 arrangement").assertExists()
                    onNodeWithTag("example-index").assertDoesNotExist()
                    0
                } else {
                    onNodeWithText("n = ${set.n}, ${set.size} arrangements").assertExists()
                    onNodeWithText("of ${set.size}").assertExists()
                    val field = onNodeWithTag("example-index").performScrollTo()
                    // out of range: a hint and no Load
                    field.performTextReplacement("${set.size + 1}")
                    onNodeWithText("1 to ${set.size}").assertExists()
                    onNodeWithText("Load").assertIsNotEnabled()
                    // the first member: no previous one
                    field.performTextReplacement("1")
                    onNodeWithText("\u2212").assertIsNotEnabled()
                    onNodeWithText("Load").assertIsEnabled()
                    // the last member: no next one; < goes one back
                    field.performTextReplacement("${set.size}")
                    onNodeWithText("+").assertIsNotEnabled()
                    onNodeWithText("\u2212").performClick()
                    waitForIdle()
                    assertEquals("${set.size - 1}", field.text())
                    set.size - 2
                }
                onNodeWithText("Load").performClick()
                waitForIdle()
                assertEquals(set.memberName(index), s.name)
                assertEquals(set.arrangement(index), s.arrangement)
                assertFalse(s.unsavedChanges, set.name)
                onNodeWithTag("big-dialog").assertDoesNotExist()
            }
        }
        // every load is one undo step
        assertTrue(s.canUndo)
    }

    @Test
    fun theMemberFieldTakesOnlyNumbersAndEachSetKeepsItsOwn() = runComposeUiTest {
        val s = state()
        setContent { App(s, initialDialog = DialogKind.NONE) }
        val set = Examples.byName("n5-intersecting-all")!!
        val group = "${ExampleGroup.INTERSECTING.title} (${Examples.inGroup(ExampleGroup.INTERSECTING).size})"
        openExamples()
        click("Sets")
        click(group)
        click(set.name)
        val field = onNodeWithTag("example-index")
        for (bad in listOf("0", "-3", "abc", "")) {
            field.performTextReplacement(bad)
            onNodeWithText("1 to ${set.size}").assertExists()
            onNodeWithText("Load").assertIsNotEnabled()
        }
        field.performTextReplacement("19")
        // another leaf and back: the number of each set is kept
        click("n4-intersecting-all")
        assertEquals("1", onNodeWithTag("example-index").text())
        click(set.name)
        assertEquals("19", onNodeWithTag("example-index").text())
        // a click on an open group collapses it
        click(group)
        onNodeWithText(set.name).assertDoesNotExist()
        click(group)
        onNodeWithText(set.name).assertIsSelected()
    }

    @Test
    fun twoLevelsAndTheInfoArea() = runComposeUiTest {
        val s = state()
        setContent { App(s, initialDialog = DialogKind.NONE) }
        openExamples()
        onNodeWithText("Most of the data is from the pseudocircles website").assertExists()
        // two levels: the groups only below "Sets"; the singletons directly below "Singletons"
        val intersecting = "${ExampleGroup.INTERSECTING.title} (${Examples.inGroup(ExampleGroup.INTERSECTING).size})"
        onNodeWithText(intersecting).assertDoesNotExist()
        click("Sets")
        onNodeWithText(intersecting).assertExists()
        onNodeWithText(ExampleGroup.SINGLETONS.title, substring = true).assertExists()
        click("${ExampleGroup.SINGLETONS.title} (${Examples.inGroup(ExampleGroup.SINGLETONS).size})")
        // a singleton: the note "1 arrangement", no number selection, the website page and the paper
        click("n5-intersecting-nonr-1")
        val info = Examples.byName("n5-intersecting-nonr-1")!!
        onNodeWithText(info.description).assertExists()
        onNodeWithText("n = 5, 1 arrangement").assertExists()
        onNodeWithTag("example-index").assertDoesNotExist()
        onNodeWithText("Website: No more circle representations").assertExists()
        onNodeWithText("Paper: ${Paper.CIRCULARIZABILITY.title}").assertExists()
        // typed in: no links, where it comes from
        click("n3-great-krupp")
        onNodeWithText("n = 3, 1 arrangement").assertExists()
        onNodeWithText("Typed in as intersection sequences").assertExists()
        onNodeWithText("Website:", substring = true).assertDoesNotExist()
        // the Edelsbrunner-Ramos arrangement is on the website too (isomorphic file), with its source
        click("n6-intersecting-nonr-edelsbrunner-ramos")
        onNodeWithText("Website: No more circle representations").assertExists()
        onNodeWithText("Data: computations/results/n6_nonr_edelsbrunnerramos.enc").assertExists()
        // a list of the database: the chooser and the links
        click(intersecting)
        click("n4-intersecting-all")
        onNodeWithText("n = 4, 8 arrangements").assertExists()
        onNodeWithTag("example-index").assertExists()
        onNodeWithText("Website: The database of pseudocircles").assertExists()
        // the great pseudocircles: the paper, the data from the computations
        click("${ExampleGroup.GREAT.title} (${Examples.inGroup(ExampleGroup.GREAT).size})")
        click("n5-great-all")
        onNodeWithText("Data: computations/results/great/all5.enc.greatcircles.enc").assertExists()
    }

    @Test
    fun cancelKeepsTheArrangement() = runComposeUiTest {
        val s = state()
        val start = s.arrangement
        setContent { App(s, initialDialog = DialogKind.NONE) }
        openExamples()
        click("${ExampleGroup.SINGLETONS.title} (${Examples.inGroup(ExampleGroup.SINGLETONS).size})")
        click("n3-great-krupp")
        onNodeWithText("Cancel").performClick()
        waitForIdle()
        onNodeWithTag("big-dialog").assertDoesNotExist()
        assertEquals(start, s.arrangement)
        assertFalse(s.canUndo)
        // the selection is kept for the next opening
        openExamples()
        onNodeWithText("n3-great-krupp").assertIsSelected()
    }
}
