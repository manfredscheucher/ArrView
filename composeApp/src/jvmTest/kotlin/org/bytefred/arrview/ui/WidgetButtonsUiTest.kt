package org.bytefred.arrview.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
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
import org.bytefred.arrview.core.Arrangement
import org.bytefred.arrview.core.DrawModel
import org.bytefred.arrview.core.Examples
import org.bytefred.arrview.core.LayoutSettings
import org.bytefred.arrview.core.TextFormat
import org.bytefred.arrview.core.crosses
import org.bytefred.arrview.core.digonCollapse
import org.bytefred.arrview.core.isCylindrical
import org.bytefred.arrview.core.isGreat
import org.bytefred.arrview.core.kruppTriples
import org.bytefred.arrview.core.nonIntersectingPairCount
import kotlin.math.abs
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Every button and field of the widgets and the toolbar, with its effect: the Properties values,
 * Copy / Revert / Apply in Sequences and Encoding, the Settings widget's chips and fields, the widget
 * chips and the close buttons. (WidgetsUiTest has applying valid and invalid sequences, sparse6
 * apply, the intersection graph and the Layout fields with Reset.)
 */
@OptIn(ExperimentalTestApi::class)
class WidgetButtonsUiTest {
    private val start = Examples.start.arrangement()
    private val chain = Examples.byName("n3-connected-chain")!!.arrangement(0)
    private val nonKrupp = Examples.byName("n3-intersecting-nonkrupp")!!.arrangement(0)

    private fun state(vararg widgets: Widget) = AppState(CoroutineScope(SupervisorJob() + Dispatchers.Default)).apply {
        openWidgets.clear()
        openWidgets.addAll(widgets)
    }

    @BeforeTest
    fun clearPrefs() = clearInMemoryPrefs()

    /** The value in the row of [label] in the Properties widget (the label fills the row up to it). */
    private fun ComposeUiTest.valueOf(label: String): String {
        val labelNode = onNodeWithText(label).fetchSemanticsNode()
        val l = labelNode.boundsInRoot
        val texts = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)).fetchSemanticsNodes()
        val value = texts.single { it.id != labelNode.id && it.boundsInRoot.left > l.left && abs(it.boundsInRoot.center.y - l.center.y) < 2f }
        return value.config[SemanticsProperties.Text].joinToString("") { it.text }
    }

    private fun yesNo(b: Boolean) = if (b) "yes" else "no"

    private fun ComposeUiTest.assertProperties(arr: Arrangement) {
        assertEquals("${arr.n}", valueOf("n"))
        assertEquals("${arr.crossingCount}", valueOf("crossings"))
        assertEquals("${arr.faceCount}", valueOf("faces"))
        assertEquals("${arr.nonIntersectingPairCount}", valueOf("non-intersecting pairs"))
        assertEquals("${arr.kruppTriples.first}", valueOf("Krupp triples"))
        assertEquals("${arr.kruppTriples.second}", valueOf("NonKrupp triples"))
        assertEquals("yes", valueOf("connected"))
        assertEquals(yesNo(arr.isIntersecting), valueOf("intersecting"))
        assertEquals(yesNo(arr.digonCount == 0), valueOf("digon-free"))
        assertEquals(yesNo(arr.isGreat), valueOf("pseudo great circles"))
        assertEquals(yesNo(arr.isCylindrical), valueOf("cylindrical"))
        for ((k, count) in arr.pVector.withIndex()) if (k >= 2) onNodeWithText("p$k = $count").assertExists()
        // p3 and p2 large on top: the number above its name
        for ((symbol, value) in listOf("p3  triangles" to arr.triangleCount, "p2  digons" to arr.digonCount)) {
            val name = onNodeWithText(symbol).fetchSemanticsNode().boundsInRoot
            val number = onAllNodesWithText("$value").fetchSemanticsNodes().filter { abs(it.boundsInRoot.left - name.left) < 2f && it.boundsInRoot.bottom <= name.top + 1f }
            assertEquals(1, number.size, symbol)
        }
    }

    @Test
    fun propertiesShowTheNumbersAndClasses() = runComposeUiTest {
        val s = state(Widget.PROPERTIES)
        setContent { App(s, initialDialog = DialogKind.NONE) }
        // the start example by hand: 30 crossings, 32 faces (8 triangles, 24 quadrangles), 16 Krupp and 4 NonKrupp triples
        for ((label, value) in listOf("n" to "6", "crossings" to "30", "faces" to "32", "Krupp triples" to "16", "NonKrupp triples" to "4", "pseudo great circles" to "no")) {
            assertEquals(value, valueOf(label), label)
        }
        onNodeWithText("p4 = 24").assertExists()
        for (arr in listOf(start, Arrangement.krupp(), nonKrupp, chain, Arrangement.two())) {
            runOnIdle { s.load("x", arr) }
            assertProperties(arr)
        }
    }

    @Test
    fun copyPutsTheShownTextOnTheClipboard() = runComposeUiTest {
        val s = state(Widget.ENCODING)
        val clipboard = FakeClipboard()
        @Suppress("DEPRECATION")
        setContent { CompositionLocalProvider(LocalClipboardManager provides clipboard) { App(s, initialDialog = DialogKind.NONE) } }
        val copies = onAllNodesWithText("Copy")
        copies.assertCountEquals(2)
        copies[0].performScrollTo().performClick()
        waitForIdle()
        assertEquals(s.text(TextFormat.MODEL1), clipboard.copied?.text)
        assertEquals("Copied", s.status?.text)
        copies[1].performScrollTo().performClick()
        waitForIdle()
        assertEquals(s.text(TextFormat.MODEL2), clipboard.copied?.text)
        // an unapplied edit is what the field shows, so that is copied
        onNodeWithTag("editor-MODEL2").performTextReplacement(":edited")
        copies[1].performScrollTo().performClick()
        waitForIdle()
        assertEquals(":edited", clipboard.copied?.text)
    }

    @Test
    fun revertDropsTheEditOfEachEditor() = runComposeUiTest {
        val s = state(Widget.ENCODING)
        setContent { App(s, initialDialog = DialogKind.NONE) }
        for (format in TextFormat.entries) {
            val tag = format.name
            onNodeWithTag("revert-$tag").assertIsNotEnabled()
            onNodeWithTag("editor-$tag").performTextReplacement("x")
            onNodeWithTag("apply-$tag").assertIsEnabled()
            assertTrue(s.unappliedDraft, tag)
            onNodeWithTag("revert-$tag").performClick()
            waitForIdle()
            assertEquals(s.text(format), onNodeWithTag("editor-$tag").text(), tag)
            onNodeWithTag("apply-$tag").assertIsNotEnabled()
            // typing the current text again is no edit
            onNodeWithTag("editor-$tag").performTextReplacement(s.text(format))
            onNodeWithTag("apply-$tag").assertIsNotEnabled()
        }
        assertFalse(s.unsavedChanges)
    }

    @Test
    fun aDraftSurvivesClosingTheWidgetAndArrangementChanges() = runComposeUiTest {
        val s = state(Widget.ENCODING)
        setContent { App(s, initialDialog = DialogKind.NONE) }
        runOnIdle { s.load("nonkrupp", nonKrupp) }
        onNodeWithTag("editor-SEQUENCES").performTextReplacement("{1: [2, -2], 2: [1, -1]}")
        val model1 = s.text(TextFormat.MODEL1)
        onNodeWithTag("editor-MODEL1").performTextReplacement("$model1 ")
        // close Encoding with its x and open it again: the edits are still there
        onAllNodesWithText("x")[0].performClick()
        waitForIdle()
        assertTrue(s.openWidgets.isEmpty())
        onNodeWithText("Encoding").performClick()
        waitForIdle()
        assertEquals("{1: [2, -2], 2: [1, -1]}", onNodeWithTag("editor-SEQUENCES").text())
        // a digon collapse makes it non-intersecting: the model 1 edit stays, with the note
        val digon = nonKrupp.faces.indices.first { nonKrupp.faces[it].size == 2 }
        runOnIdle { s.collapseDigon(digon) }
        assertEquals(nonKrupp.digonCollapse(digon), s.arrangement)
        val (c, d) = (0 until 3).flatMap { c -> (c + 1 until 3).map { c to it } }.first { (c, d) -> !s.arrangement.crosses(c, d) }
        onNodeWithText("Only for intersecting arrangements; circles ${c + 1} and ${d + 1} do not cross.").assertExists()
        assertEquals("$model1 ", onNodeWithTag("editor-MODEL1").text())
        onNodeWithTag("revert-MODEL1").performClick()
        waitForIdle()
        onNodeWithTag("editor-MODEL1").assertDoesNotExist()
        // the stale sequences edit still applies (undoable)
        onNodeWithTag("apply-SEQUENCES").performClick()
        waitForIdle()
        assertEquals(Arrangement.two(), s.arrangement)
        runOnIdle { s.undo() }
        assertEquals(nonKrupp.digonCollapse(digon), s.arrangement)
    }

    @Test
    fun model1SparseIsOfferedForTwoCirclesWithoutTheDrawing() = runComposeUiTest {
        val s = state(Widget.ENCODING)
        setContent { App(s, initialDialog = DialogKind.NONE) }
        runOnIdle { s.load("two", Arrangement.two()) }
        assertEquals(s.text(TextFormat.MODEL1), onNodeWithTag("editor-MODEL1").text())
        onNodeWithText("Only for intersecting arrangements", substring = true).assertDoesNotExist()
        assertEquals(DrawModel.MODEL2, s.model)
        // the 3-Chain: the first pair that does not cross is named
        runOnIdle { s.load("chain", chain) }
        onNodeWithText("Only for intersecting arrangements; circles 1 and 3 do not cross.").assertExists()
        onNodeWithTag("editor-MODEL1").assertDoesNotExist()
    }

    @Test
    fun settingsWidgetDropdownFieldsAndReset() = runComposeUiTest {
        val s = state(Widget.SETTINGS)
        setContent { App(s, initialDialog = DialogKind.NONE) }
        // the model is chosen only here: no model chips in the toolbar
        onNodeWithText("Model 1").assertDoesNotExist()
        onNodeWithText(DrawModel.MODEL2.option).assertExists()
        // Model 1: the dialog, Cancel keeps model 2
        onNodeWithTag("visualization").performClick()
        waitForIdle()
        assertEquals(DrawModel.entries.map { it.option }, listOf("Model 1: primal graph (legacy)", "Model 2: primal-dual graph"))
        menuItem(DrawModel.MODEL1.option).performClick()
        waitForIdle()
        onNodeWithText(modelDialogText(ModelDialog.Confirm)).assertExists()
        onNodeWithText("Cancel").performClick()
        waitForIdle()
        assertEquals(DrawModel.MODEL2, s.model)
        // OK switches
        onNodeWithTag("visualization").performClick()
        waitForIdle()
        menuItem(DrawModel.MODEL1.option).performClick()
        waitForIdle()
        onNodeWithTag("model-dialog-ok").performClick()
        waitForIdle()
        assertEquals(DrawModel.MODEL1, s.model)
        onNodeWithText(DrawModel.MODEL1.option).assertExists()
        for (bad in listOf("0", "-1", "abc", "")) {
            onNodeWithTag("max-iterations").performTextReplacement(bad)
            onNodeWithText("at least 1").assertExists()
        }
        assertEquals(LayoutSettings(), s.layoutSettings)
        // no upper bound on max. iterations
        onNodeWithTag("max-iterations").performTextReplacement("1000")
        waitForIdle()
        assertEquals(1000, s.layoutSettings.maxIterations)
        // Reset brings back model 2 and the layout settings
        onNodeWithText("Reset to defaults").assertIsEnabled().performClick()
        waitForIdle()
        assertEquals(LayoutSettings(), s.layoutSettings)
        assertEquals(DrawModel.MODEL2, s.preferredModel)
    }

    @Test
    fun theVisualizationInfoOpensItsHelpPage() = runComposeUiTest {
        val s = state(Widget.SETTINGS)
        setContent { App(s, initialDialog = DialogKind.NONE) }
        onNodeWithTag("info-visualization").performClick()
        waitForIdle()
        assertEquals(HelpPage.VISUALIZATION, s.helpPage)
        onNodeWithTag("big-dialog").assertExists()
        for (kind in GraphKind.entries) onNodeWithTag("figure-${kind.name}").assertExists()
    }

    @Test
    fun theEncodingInfosShowTheirTexts() = runComposeUiTest {
        val s = state(Widget.ENCODING)
        setContent { App(s, initialDialog = DialogKind.NONE) }
        // sequences first, then model 1, then model 2
        val tops = TextFormat.entries.map { onNodeWithTag("info-${it.name}").fetchSemanticsNode().boundsInRoot.top }
        assertEquals(tops.sorted(), tops)
        for ((format, text) in listOf(TextFormat.SEQUENCES to SEQUENCES_INFO, TextFormat.MODEL1 to MODEL1_INFO, TextFormat.MODEL2 to MODEL2_INFO)) {
            onNodeWithText(text).assertDoesNotExist()
            onNodeWithTag("info-${format.name}").performClick()
            waitForIdle()
            assertEquals(text, onNodeWithTag("info-${format.name}-text").text())
            onNodeWithTag("info-${format.name}").performClick()
            waitForIdle()
        }
    }

    @Test
    fun atMostTwoWidgetsByDefault() = runComposeUiTest {
        val s = state(Widget.PROPERTIES)
        setContent { App(s, initialDialog = DialogKind.NONE) }
        onNodeWithText("Encoding").performClick()
        onNodeWithText("Triangles").performClick()
        waitForIdle()
        // the third closes the oldest
        assertEquals(listOf(Widget.ENCODING, Widget.TRIANGLES), s.openWidgets.toList())
        onNodeWithText("Settings").performClick()
        waitForIdle()
        assertEquals(listOf(Widget.TRIANGLES, Widget.SETTINGS), s.openWidgets.toList())
        // Max. widgets in Settings
        onNodeWithText("Max. widgets: 2").assertExists()
        onNodeWithTag("max-widgets-plus").performClick()
        waitForIdle()
        assertEquals(3, s.maxColumns)
        onNodeWithText("Properties").performClick()
        waitForIdle()
        assertEquals(listOf(Widget.TRIANGLES, Widget.SETTINGS, Widget.PROPERTIES), s.openWidgets.toList())
        // lowering it closes the oldest
        onNodeWithTag("max-widgets-minus").performClick()
        waitForIdle()
        assertEquals(listOf(Widget.SETTINGS, Widget.PROPERTIES), s.openWidgets.toList())
        onNodeWithTag("max-widgets-minus").performClick()
        waitForIdle()
        assertEquals(1, s.maxColumns)
        assertEquals(listOf(Widget.PROPERTIES), s.openWidgets.toList())
        onNodeWithText("Settings").performClick()
        waitForIdle()
        assertEquals(listOf(Widget.SETTINGS), s.openWidgets.toList())
        onNodeWithTag("max-widgets-minus").assertIsNotEnabled()
        runOnIdle { s.changeMaxColumns(4); s.saveDisplayPrefs() }
        assertEquals(4, AppState(CoroutineScope(SupervisorJob() + Dispatchers.Default)).maxColumns)
    }

    @Test
    fun toolbarWidgetChipsAndCloseButtons() = runComposeUiTest {
        val s = state()
        setContent { App(s, initialDialog = DialogKind.NONE) }
        runOnIdle { s.changeMaxColumns(Widget.entries.size) }
        for (w in Widget.entries) onNodeWithText(w.label).performClick()
        waitForIdle()
        assertEquals(Widget.entries, s.openWidgets.toList())
        for (w in Widget.entries) onAllNodesWithText(w.label)[0].assertIsSelected()
        // every column has its x; closing them one by one
        onAllNodesWithText("x").assertCountEquals(Widget.entries.size)
        repeat(Widget.entries.size) { onAllNodesWithText("x")[0].performClick() }
        waitForIdle()
        assertTrue(s.openWidgets.isEmpty())
        onNodeWithTag("canvas").assertExists()
    }
}
