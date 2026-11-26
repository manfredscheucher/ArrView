package org.bytefred.arrview.ui

import kotlin.time.Duration.Companion.minutes
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotSelected
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
import kotlin.test.assertNotNull
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The Examples dialog driven by clicks: the tree, a member of every set, the member chooser, Cancel. */
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

    /** Opens the supergroup of [group] and then [group] (both collapsed before). */
    private fun ComposeUiTest.openGroup(group: ExampleGroup) {
        click(group.supergroup.title)
        click(group.title)
    }

    @Test
    // loads all sets (16 s on an idle machine); the default 1 min limit is too tight under load
    fun aMemberOfEverySetLoadsByClicks() = runComposeUiTest(testTimeout = 5.minutes) {
        val s = state()
        setContent { App(s, initialDialog = DialogKind.NONE) }
        for (group in Examples.groups) {
            val sets = Examples.inGroup(group)
            for ((k, set) in sets.withIndex()) {
                openExamples()
                // supergroups and groups stay expanded between openings
                if (group == Examples.groupsIn(group.supergroup).first() && k == 0) click(group.supergroup.title)
                if (k == 0) click(group.title)
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
        val group = Examples.group("intersecting").title
        openExamples()
        openGroup(Examples.group("intersecting"))
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
        // a click on an open group collapses it and clears the selection
        click(group)
        onNodeWithText(leafLabel(set)).assertDoesNotExist()
        click(group)
        onNodeWithText(leafLabel(set)).assertIsNotSelected()
    }

    @Test
    fun supergroupsAndGroupsWithDescriptionsLinksAndTheInfoArea() = runComposeUiTest {
        val s = state()
        setContent { App(s, initialDialog = DialogKind.NONE) }
        openExamples()
        onNodeWithText("Most of the data is from the pseudocircles website").assertExists()
        // the supergroups, all collapsed: no groups, no descriptions, no leaves
        for (sg in Examples.supergroups) onNodeWithText(sg.title).assertExists()
        for (g in Examples.groups) onNodeWithText(g.title).assertDoesNotExist()
        onNodeWithTag("supergroup-info").assertDoesNotExist()
        onNodeWithTag("group-info").assertDoesNotExist()
        // an open supergroup shows what it is, then its groups (collapsed); no links above a set
        val linkTitles = Examples.sets.flatMap { it.links }.map { it.title }.toSet()
        for (sg in Examples.supergroups) {
            click(sg.title)
            sg.description?.let { onNodeWithText(it).assertExists() }
            for (g in Examples.groupsIn(sg)) {
                onNodeWithText(g.title).assertExists()
                // an open group: its description, its sets
                click(g.title)
                g.description?.let { onNodeWithText(it).assertExists() }
                for (title in linkTitles) onAllNodesWithText(title).assertCountEquals(0)
                for (set in Examples.inGroup(g)) onNodeWithText(leafLabel(set)).assertExists()
                click(g.title)
                for (set in Examples.inGroup(g)) onNodeWithText(leafLabel(set)).assertDoesNotExist()
            }
            click(sg.title)
            for (g in Examples.groupsIn(sg)) onNodeWithText(g.title).assertDoesNotExist()
        }
        // a single arrangement: the note "1 arrangement", no number selection, where its data is from
        openGroup(Examples.group("nonr-5"))
        val nonr = Examples.byName("n5-intersecting-nonr-1")!!
        click(nonr.label)
        onNodeWithText(nonr.description).assertExists()
        onNodeWithText("n = 5, 1 arrangement").assertExists()
        onNodeWithTag("example-index").assertDoesNotExist()
        onNodeWithText("Data: website/arrangements/nomorecircles/connected/n5_nonr_number1_intersecting.mod2s6").assertExists()
        // all its links, once each
        for (link in nonr.links) onAllNodesWithText(link.title).assertCountEquals(1)
        // from the computations: the Pappus one has no website page
        click(Examples.group("nonr-9-great").title)
        click("n = 9, great, non-Pappus")
        onNodeWithText("Data: computations/results/n9_nonr_pappus.enc").assertExists()
        // a list of the database: the count in the label, the chooser
        openGroup(Examples.group("intersecting"))
        click("Intersecting, n = 4 (8)")
        onNodeWithText("n = 4, 8 arrangements").assertExists()
        onNodeWithTag("example-index").assertExists()
        // the new connected digon-free sets (Table 1 of On Circularizability: 1, 3, 30)
        click(Examples.group("connected-digonfree").title)
        click("Connected digon-free, n = 5 (30)")
        onNodeWithText("n = 5, 30 arrangements").assertExists()
        // a coloured one; Koester's arrangement is with them
        openGroup(Examples.group("colorings"))
        click("n = 6, no. 2")
        for (link in Examples.byName("n6-digonfree-triangle-saturated-2")!!.links) onAllNodesWithText(link.title).assertCountEquals(1)
        click("n = 7, Koester")
        onNodeWithText("n = 7, 1 arrangement").assertExists()
        onNodeWithText("Data: computations/biconnected_results/n8_koester.enc").assertExists()
    }

    @Test
    fun expandSelectCollapseAndLoadInTheTree() = runComposeUiTest {
        val s = state()
        setContent { App(s, initialDialog = DialogKind.NONE) }
        val load = onNodeWithText("Load")
        openExamples()
        load.assertIsNotEnabled()
        val database = Examples.supergroup("database")
        val group = Examples.group("connected-digonfree")
        val set = assertNotNull(Examples.byName("n4-connected-digonfree-all"))
        // expand the supergroup, then the group, select a set
        click(database.title)
        click(group.title)
        click(leafLabel(set))
        onNodeWithText(leafLabel(set)).assertIsSelected()
        load.assertIsEnabled()
        // collapsing another group or supergroup keeps the selection
        click(Examples.group("great").title)
        click(Examples.group("great").title)
        click(Examples.supergroup("constructions").title)
        click(Examples.supergroup("constructions").title)
        load.assertIsEnabled()
        // collapsing the supergroup that holds it clears it; the group stays open inside
        click(database.title)
        onNodeWithText(group.title).assertDoesNotExist()
        load.assertIsNotEnabled()
        assertNull(s.examples.selected)
        click(database.title)
        onNodeWithText(leafLabel(set)).assertIsNotSelected()
        // select again, collapse the group: cleared too
        click(leafLabel(set))
        load.assertIsEnabled()
        click(group.title)
        load.assertIsNotEnabled()
        // select once more and load: the whole set
        click(group.title)
        click(leafLabel(set))
        load.performClick()
        waitForIdle()
        assertEquals(set.name, s.name)
        assertEquals(3, s.arrangementSet.size)
        assertEquals(set.arrangement(0), s.arrangement)
        onNodeWithTag("big-dialog").assertDoesNotExist()
        // reopened: the tree and the selection as left
        openExamples()
        onNodeWithText(leafLabel(set)).assertIsSelected()
    }

    @Test
    fun aColouredExampleLoadsWithItsColours() = runComposeUiTest {
        val s = state()
        setContent { App(s, initialDialog = DialogKind.NONE) }
        openExamples()
        openGroup(Examples.group("colorings"))
        click("n = 6, no. 2")
        onNodeWithText("Load").performClick()
        waitForIdle()
        val set = Examples.byName("n6-digonfree-triangle-saturated-2")!!
        assertEquals(set.name, s.name)
        assertEquals(set.colors(0), s.crossingColors)
        assertEquals(60, s.crossingColors!!.size)
        // an edit drops them, undo brings them back
        s.flipTriangle(s.arrangement.faces.indexOfFirst { it.size == 3 })
        assertEquals(null, s.crossingColors)
        s.undo()
        assertEquals(set.colors(0), s.crossingColors)
        // an uncoloured example has none
        s.loadSet("x", ArrangementSet(1, Examples.start.set::colors) { Examples.start.arrangement() })
        assertEquals(null, s.crossingColors)
    }

    @Test
    fun collapsingTheSelectionsGroupDisablesLoad() = runComposeUiTest {
        val s = state()
        setContent { App(s, initialDialog = DialogKind.EXAMPLES) }
        val group = Examples.group("furedi-palasti")
        val other = Examples.group("min-triangles")
        val load = onNodeWithText("Load")
        load.assertIsNotEnabled()
        openGroup(group)
        click(leafLabel(Examples.inGroup(group).first()))
        load.assertIsEnabled()
        // collapsing another group (in another supergroup) keeps the selection
        openGroup(other)
        click(other.title)
        load.assertIsEnabled()
        click(group.title)
        load.assertIsNotEnabled()
        assertNull(s.examples.selected)
    }

    @Test
    fun cancelKeepsTheArrangement() = runComposeUiTest {
        val s = state()
        val start = s.arrangement
        setContent { App(s, initialDialog = DialogKind.NONE) }
        openExamples()
        openGroup(Examples.group("connected"))
        click("Connected, n = 3 (3)")
        onNodeWithText("Cancel").performClick()
        waitForIdle()
        onNodeWithTag("big-dialog").assertDoesNotExist()
        assertEquals(start, s.arrangement)
        assertFalse(s.canUndo)
        // the selection is kept for the next opening
        openExamples()
        onNodeWithText("Connected, n = 3 (3)").assertIsSelected()
    }

    @Test
    fun setDetailsShowWhatItIsBeforeItsLinks() = runComposeUiTest {
        val s = state()
        setContent { App(s, initialDialog = DialogKind.NONE) }
        openExamples()
        openGroup(Examples.group("intersecting"))
        val set = Examples.byName("n4-intersecting-all")!!
        click(leafLabel(set))
        // description, n and count, the member chooser, then the links, then where the data is from
        fun top(node: SemanticsNodeInteraction) = node.getBoundsInRoot().top
        val order = listOf(
            onNodeWithText(set.description),
            onNodeWithText("n = 4, 8 arrangements"),
            onNodeWithTag("example-index"),
            onNodeWithTag("example-links"),
            onNodeWithText("Data: ${set.source}"),
        ).map(::top)
        assertEquals(order.sorted(), order)
        assertEquals(order.size, order.toSet().size)
        for (link in set.links) onNodeWithText(link.title).assertExists()
    }
}
