package org.bytefred.arrview.ui

import kotlin.time.Duration.Companion.minutes
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onAllNodesWithText
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
        // a title click right after a menu dismissal (MENU_CLICK_GUARD) is ignored: once more after it
        if (onAllNodesWithText("Examples...").fetchSemanticsNodes().isEmpty()) {
            Thread.sleep(MENU_CLICK_GUARD.inWholeMilliseconds + 100)
            onNodeWithTag("menu-Arrangement").performClick()
        }
        onNodeWithText("Examples...").performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.click(text: String) = onNodeWithText(text).performScrollTo().performClick()

    private fun header(group: ExampleGroup) = "${group.title} (${Examples.inGroup(group).size})"

    @Test
    // loads all sets (16 s on an idle machine); the default 1 min limit is too tight under load
    fun aMemberOfEverySetLoadsByClicks() = runComposeUiTest(testTimeout = 5.minutes) {
        val s = state()
        setContent { App(s, initialDialog = DialogKind.NONE) }
        for (group in ExampleGroup.entries) {
            val sets = Examples.inGroup(group)
            for ((k, set) in sets.withIndex()) {
                openExamples()
                // the groups stay expanded between openings
                if (k == 0) click(header(group))
                click(leafLabel(set))
                onNodeWithText(leafLabel(set)).assertIsSelected()
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
                // the whole set, at the chosen member
                assertEquals(set.name, s.name)
                assertEquals(set.size, s.arrangementSet.size)
                assertEquals(index, s.setIndex)
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
        val group = header(ExampleGroup.DATABASE)
        openExamples()
        click(group)
        click(leafLabel(set))
        val field = onNodeWithTag("example-index")
        for (bad in listOf("0", "-3", "abc", "")) {
            field.performTextReplacement(bad)
            onNodeWithText("1 to ${set.size}").assertExists()
            onNodeWithText("Load").assertIsNotEnabled()
        }
        field.performTextReplacement("19")
        // another leaf and back: the number of each set is kept
        click(leafLabel(Examples.byName("n4-intersecting-all")!!))
        assertEquals("1", onNodeWithTag("example-index").text())
        click(leafLabel(set))
        assertEquals("19", onNodeWithTag("example-index").text())
        // a click on an open group collapses it
        click(group)
        onNodeWithText(leafLabel(set)).assertDoesNotExist()
        click(group)
        onNodeWithText(leafLabel(set)).assertIsSelected()
    }

    @Test
    fun groupsByPurposeWithDescriptionsLinksAndTheInfoArea() = runComposeUiTest {
        val s = state()
        setContent { App(s, initialDialog = DialogKind.NONE) }
        openExamples()
        onNodeWithText("Most of the data is from the pseudocircles website").assertExists()
        // the groups, all collapsed: no descriptions, no leaves
        for (group in ExampleGroup.entries) onNodeWithText(header(group)).assertExists()
        onNodeWithTag("group-info").assertDoesNotExist()
        // an open group shows what it is and where it comes from, then its sets
        for (group in ExampleGroup.entries) {
            click(header(group))
            onNodeWithText(group.description).assertExists()
            group.page?.let { onNodeWithText("Website: ${it.title}").assertExists() }
            // the sets repeat the links in their info area
            group.paper?.let { onAllNodesWithText("Paper: ${it.title}").assertCountEquals(1) }
            for (set in Examples.inGroup(group)) onNodeWithText(leafLabel(set)).assertExists()
            click(header(group))
            onNodeWithText(group.description).assertDoesNotExist()
        }
        // a single arrangement: the note "1 arrangement", no number selection, the website page and the paper
        click(header(ExampleGroup.NON_CIRCULARIZABLE))
        val nonr = Examples.byName("n5-intersecting-nonr-1")!!
        click(nonr.label)
        onNodeWithText(nonr.description).assertExists()
        onNodeWithText("n = 5, 1 arrangement").assertExists()
        onNodeWithTag("example-index").assertDoesNotExist()
        onNodeWithText("Data: website/arrangements/nomorecircles/connected/n5_nonr_number1_intersecting.mod2s6").assertExists()
        // from the computations: the Pappus one has no website page
        click("n = 9 great, non-Pappus")
        onNodeWithText("Data: computations/results/n9_nonr_pappus.enc").assertExists()
        // typed in: where it comes from
        click(header(ExampleGroup.THREE))
        click("Krupp")
        onNodeWithText("n = 3, 1 arrangement").assertExists()
        onNodeWithText("Typed in as intersection sequences").assertExists()
        // a list of the database: the count in the label, the chooser
        click(header(ExampleGroup.DATABASE))
        click("Intersecting, n = 4 (8)")
        onNodeWithText("n = 4, 8 arrangements").assertExists()
        onNodeWithTag("example-index").assertExists()
        // a coloured one says so; Koester's arrangement is with them
        click(header(ExampleGroup.COLORINGS))
        click("n = 6, no. 2")
        onNodeWithText("The crossings are shown in their colours.").assertExists()
        onAllNodesWithText("Paper: ${Paper.COLORING.title}").assertCountEquals(2)
        click("Koester's arrangement")
        onNodeWithText("n = 7, 1 arrangement").assertExists()
        onNodeWithText("The crossings are shown in their colours.").assertDoesNotExist()
    }

    @Test
    fun aColouredExampleLoadsWithItsColours() = runComposeUiTest {
        val s = state()
        setContent { App(s, initialDialog = DialogKind.NONE) }
        openExamples()
        click(header(ExampleGroup.COLORINGS))
        click("n = 6, no. 2")
        onNodeWithText("Load").performClick()
        waitForIdle()
        val set = Examples.byName("n6-digonfree-triangle-saturated-2")!!
        assertEquals(set.name, s.name)
        assertTrue(Examples.crossingColours(s.arrangement) != null)
    }

    @Test
    fun cancelKeepsTheArrangement() = runComposeUiTest {
        val s = state()
        val start = s.arrangement
        setContent { App(s, initialDialog = DialogKind.NONE) }
        openExamples()
        click(header(ExampleGroup.THREE))
        click("Krupp")
        onNodeWithText("Cancel").performClick()
        waitForIdle()
        onNodeWithTag("big-dialog").assertDoesNotExist()
        assertEquals(start, s.arrangement)
        assertFalse(s.canUndo)
        // the selection is kept for the next opening
        openExamples()
        onNodeWithText("Krupp").assertIsSelected()
    }
}
