package org.bytefred.arrview.ui

import org.bytefred.arrview.core.ThreeCircles
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.test.withKeyDown
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.bytefred.arrview.core.Arrangement
import org.bytefred.arrview.core.DrawModel
import org.bytefred.arrview.core.Examples
import org.bytefred.arrview.core.FileFormat
import org.bytefred.arrview.core.TextFormat
import org.bytefred.arrview.core.arrangementFile

/**
 * Every menu item clicked at least once, and the dialogs they open: Export, Import, Save file,
 * Help pages, About, Welcome, "Save changes?" for each trigger, the error dialog.
 * (Examples...: ExamplesDialogUiTest; Open file and the save buttons open the system file
 * dialog, which a test cannot drive.)
 */
@OptIn(ExperimentalTestApi::class)
class MenusAndDialogsUiTest {
    private val start = Examples.start.arrangement()
    private val chain = ThreeCircles.chain

    private fun state() = AppState(CoroutineScope(SupervisorJob() + Dispatchers.Default))

    @BeforeTest
    fun clearPrefs() = clearInMemoryPrefs()

    /** Closes the open menu with Escape (a click on its title would block reopening it for 500 ms, see MenuButton). */
    private fun ComposeUiTest.closeMenu() {
        onNodeWithTag("menu-bar").performKeyInput { pressKey(Key.Escape) }
        waitForIdle()
    }

    /** A control of the open dialog (the toolbar has chips with the same labels). */
    private fun ComposeUiTest.inDialog(text: String) = onNode(hasText(text) and hasClickAction() and hasAnyAncestor(isDialog()))

    private fun ComposeUiTest.menu(title: String, item: String) {
        onNodeWithTag("menu-$title").performClick()
        menuItem(item).performClick()
        waitForIdle()
    }

    @Test
    // tall enough for the View menu below its title (in a shorter window it moves up over the title)
    fun theMenusHaveTheirItems() = runDesktopComposeUiTest(1200, 1000) {
        val s = state()
        setContent { App(s, initialDialog = DialogKind.NONE) }
        val items = mapOf(
            "Arrangement" to ARRANGEMENT_MENU,
            "Edit" to listOf("Undo", "Redo"),
            "View" to listOf("Fit", "Zoom in", "Zoom out") + Widget.entries.map { it.label } +
                LayoutMode.entries.map { it.name.lowercase() },
            "Help" to HelpPage.entries.map { "${it.title}..." } + "About ArrView...",
        )
        for ((title, entries) in items) {
            onNodeWithTag("menu-$title").performClick()
            waitForIdle()
            assertEquals(title, s.openMenu)
            for (e in entries) menuItem(e).assertExists()
            // a click on the open menu's title closes it
            onNodeWithTag("menu-$title").performClick()
            waitForIdle()
            assertNull(s.openMenu)
        }
    }

    @Test
    fun editUndoAndRedo() = runComposeUiTest {
        val s = state()
        setContent { App(s, initialDialog = DialogKind.NONE) }
        onNodeWithTag("menu-Edit").performClick()
        menuItem("Undo").assertIsNotEnabled()
        menuItem("Redo").assertIsNotEnabled()
        closeMenu()
        awaitDrawing(s)
        runOnIdle { s.flipSomeTriangle() }
        val flipped = s.arrangement
        menu("Edit", "Undo")
        assertEquals(start, s.arrangement)
        assertNull(s.openMenu, "a plain item closes the menu")
        onNodeWithTag("menu-Edit").performClick()
        menuItem("Undo").assertIsNotEnabled()
        menuItem("Redo").assertIsEnabled().performClick()
        waitForIdle()
        assertEquals(flipped, s.arrangement)
    }

    @Test
    fun viewZoomAndFit() = runComposeUiTest {
        val s = state()
        val view = ViewTransform()
        setContent { App(s, view, DialogKind.NONE) }
        waitUntil(timeoutMillis = 10_000) { s.drawing != null && view.fittedRequest == s.fitRequest }
        val fitted = view.scale
        menu("View", "Zoom in")
        assertEquals(fitted * ZOOM_STEP, view.scale, 1e-9)
        menu("View", "Zoom in")
        menu("View", "Zoom out")
        assertEquals(fitted * ZOOM_STEP, view.scale, 1e-9)
        view.ox += 100.0
        menu("View", "Fit")
        waitForIdle()
        assertEquals(fitted, view.scale, 1e-9)
    }

    @Test
    fun theModelIsNotInTheViewMenu() = runComposeUiTest {
        val s = state()
        setContent { App(s, initialDialog = DialogKind.NONE) }
        onNodeWithTag("menu-View").performClick()
        waitForIdle()
        menuItem("Model 1").assertDoesNotExist()
        menuItem("Model 2").assertDoesNotExist()
    }

    @Test
    fun viewWidgetItemsToggleSeveralInOneOpening() = runComposeUiTest {
        val s = state()
        setContent { App(s, initialDialog = DialogKind.NONE) }
        runOnIdle { s.changeMaxColumns(Widget.entries.size) }
        onNodeWithTag("menu-View").performClick()
        for (w in Widget.entries) menuItem(w.label).performClick()
        waitForIdle()
        // Properties was open and is closed now, the others are open in click order
        assertEquals(Widget.entries.drop(1), s.openWidgets.toList())
        assertEquals("View", s.openMenu)
        for (w in Widget.entries.drop(1)) menuItem(w.label).performClick()
        waitForIdle()
        assertTrue(s.openWidgets.isEmpty())
    }

    @Test
    fun viewLayoutItemsPutTheWidgetsBesideOrBelow() = runComposeUiTest {
        val s = state()
        setContent { App(s, initialDialog = DialogKind.NONE) }
        fun widgetBelowCanvas(): Boolean {
            val canvas = onNodeWithTag("canvas").fetchSemanticsNode().boundsInRoot
            val widget = onNodeWithText("non-intersecting pairs").fetchSemanticsNode().boundsInRoot
            return widget.top >= canvas.bottom && widget.left < canvas.right
        }
        fun widgetBesideCanvas(): Boolean {
            val canvas = onNodeWithTag("canvas").fetchSemanticsNode().boundsInRoot
            val widget = onNodeWithText("non-intersecting pairs").fetchSemanticsNode().boundsInRoot
            return widget.left >= canvas.right
        }
        for ((item, mode) in listOf("portrait" to LayoutMode.PORTRAIT, "landscape" to LayoutMode.LANDSCAPE, "auto" to LayoutMode.AUTO)) {
            onNodeWithTag("menu-View").performClick()
            menuItem(item).performClick()
            closeMenu()
            assertEquals(mode, s.layoutMode)
            // the test window is wide: auto puts the widgets beside the canvas
            if (mode == LayoutMode.PORTRAIT) assertTrue(widgetBelowCanvas(), item) else assertTrue(widgetBesideCanvas(), item)
        }
    }

    @Test
    fun exportSvgIsOfferedOnceTheDrawingIsDone() = runComposeUiTest {
        val s = state()
        setContent { App(s, initialDialog = DialogKind.NONE) }
        awaitDrawing(s)
        onNodeWithTag("menu-Arrangement").performClick()
        menuItem("Export SVG...").assertIsEnabled()
    }

    @Test
    fun exportDialogShowsEveryFormatAndCopies() = runComposeUiTest {
        val s = state()
        val clipboard = FakeClipboard()
        @Suppress("DEPRECATION")
        setContent { CompositionLocalProvider(LocalClipboardManager provides clipboard) { App(s, initialDialog = DialogKind.NONE) } }
        menu("Arrangement", "Export...")
        val text = onNodeWithTag("export-text")
        assertEquals(s.text(TextFormat.SEQUENCES), text.text())
        for (f in listOf(TextFormat.MODEL2, TextFormat.MODEL1, TextFormat.SEQUENCES)) {
            inDialog(f.label).performClick()
            waitForIdle()
            assertEquals(s.text(f), text.text(), f.label)
            assertEquals(start.canonical, f.read(text.text()!!).canonical, f.label)
        }
        inDialog(TextFormat.MODEL2.label).performClick()
        onNodeWithText("Copy").performClick()
        waitForIdle()
        assertEquals(s.text(TextFormat.MODEL2), clipboard.copied?.text)
        assertEquals("Copied", s.status?.text)
        onNodeWithText("Close").performClick()
        waitForIdle()
        onNodeWithTag("export-text").assertDoesNotExist()
        // not intersecting: no model 1 to export
        runOnIdle { s.load("chain", chain) }
        menu("Arrangement", "Export...")
        inDialog(TextFormat.MODEL1.label).assertIsNotEnabled()
        assertEquals(s.text(TextFormat.SEQUENCES), onNodeWithTag("export-text").text())
    }

    private fun ComposeUiTest.import(text: String) {
        menu("Arrangement", "Import...")
        onNodeWithTag("import-text").performTextReplacement(text)
        onNode(hasText("Import") and hasClickAction()).performClick()
        waitForIdle()
    }

    @Test
    fun importReadsSequencesAndJsonWithItsName() = runComposeUiTest {
        val s = state()
        setContent { App(s, initialDialog = DialogKind.NONE) }
        import(chain.toString())
        assertEquals(chain, s.arrangement)
        assertEquals("imported", s.title)
        onNodeWithTag("import-text").assertDoesNotExist()
        val krupp = Arrangement.krupp()
        import(arrangementFile("my krupp", krupp, FileFormat.JSON))
        assertEquals(krupp, s.arrangement)
        assertEquals("my krupp", s.title)
        assertFalse(s.unsavedChanges)
        // Cancel: nothing changes
        menu("Arrangement", "Import...")
        onNodeWithTag("import-text").performTextReplacement(chain.toString())
        onNodeWithText("Cancel").performClick()
        waitForIdle()
        assertEquals(krupp, s.arrangement)
    }

    @Test
    fun importOfInvalidTextShowsTheReason() = runComposeUiTest {
        val s = state()
        setContent { App(s, initialDialog = DialogKind.NONE) }
        for ((text, reason) in listOf(
            "{1: [2, -2], 2: [1]}" to "Def. 2.4 (iii)",
            "hello" to "neither model 1",
            "   " to "the text is empty",
            "{\"name\": \"x\"}" to "\"sequences\"",
        )) {
            import(text)
            onNodeWithText("Error").assertExists()
            val message = onNodeWithText("Cannot import the text: ", substring = true).text()!!
            assertTrue(reason in message, "$text: $message")
            onNodeWithText("OK").performClick()
            waitForIdle()
            assertNull(s.error)
            // the Import dialog stays with the text, to fix it
            assertEquals(text, onNodeWithTag("import-text").text())
            onNodeWithText("Cancel").performClick()
            waitForIdle()
        }
        assertEquals(start, s.arrangement)
    }

    @Test
    fun saveFileOffersJsonAndText() = runComposeUiTest {
        val s = state()
        setContent { App(s, initialDialog = DialogKind.NONE) }
        menu("Arrangement", "Save file...")
        onNodeWithText("Save file").assertExists()
        onNodeWithText("Save JSON").assertIsEnabled()
        onNodeWithText("Save text").assertIsEnabled()
        onNodeWithText("Cancel").performClick()
        waitForIdle()
        onNodeWithText("Save file").assertDoesNotExist()
        assertEquals("n6-digonfree-nonr-8-triangles.json", s.saveName(FileFormat.JSON))
        assertEquals("n6-digonfree-nonr-8-triangles.txt", s.saveName(FileFormat.TXT))
    }

    @Test
    fun ctrlOAndCtrlSOnTheCanvasAndInATextField() = runComposeUiTest {
        val s = state()
        setContent { App(s, initialDialog = DialogKind.NONE) }
        awaitDrawing(s)
        onNodeWithTag("canvas").performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.S) } }
        onNodeWithText("Save JSON").assertIsDisplayed()
        onNodeWithText("Cancel").performClick()
        waitForIdle()
        // Ctrl+O asks first when there are unsaved changes, as the menu does
        runOnIdle { s.flipSomeTriangle() }
        onNodeWithTag("canvas").performKeyInput { withKeyDown(Key.MetaLeft) { pressKey(Key.O) } }
        onNodeWithText("Save changes?").assertExists()
        onNodeWithText("Cancel").performClick()
        waitForIdle()
        assertNull(s.pendingLeave)
        // also while a text field has focus
        onNodeWithText("Encoding").performClick()
        onNodeWithTag("editor-SEQUENCES").performClick()
        onNodeWithTag("editor-SEQUENCES").performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.S) } }
        onNodeWithText("Save JSON").assertIsDisplayed()
    }

    @Test
    fun saveChangesForImportAndOpenFile() = runComposeUiTest {
        val s = state()
        setContent { App(s, initialDialog = DialogKind.NONE) }
        awaitDrawing(s)
        runOnIdle { s.flipSomeTriangle() }
        // Open file asks first; Cancel never opens the file dialog
        menu("Arrangement", "Open file...")
        onNodeWithText("Save changes?").assertExists()
        onNodeWithText("Your changes to \"n6-digonfree-nonr-8-triangles\" are not saved.").assertExists()
        onNodeWithText("Cancel").performClick()
        waitForIdle()
        assertNull(s.pendingLeave)
        // Import asks too; Discard imports
        import(chain.toString())
        onNodeWithText("Save changes?").assertExists()
        onNodeWithText("Discard").performClick()
        waitForIdle()
        assertEquals(chain, s.arrangement)
        onNodeWithTag("import-text").assertDoesNotExist()
    }

    @Test
    fun saveChangesForAnUnappliedDraftOnlyDiscards() = runComposeUiTest {
        val s = state()
        setContent { App(s, initialDialog = DialogKind.NONE) }
        runOnIdle { s.editDraft(TextFormat.SEQUENCES, "{1: [2, -2], 2: [1, -1]}") }
        import(chain.toString())
        onNodeWithText("Save changes?").assertExists()
        onNodeWithText("A text edited in the Encoding widget is not applied.").assertExists()
        // the arrangement itself is saved: no format buttons, a draft is never saved implicitly
        onNodeWithText("Save JSON").assertDoesNotExist()
        onNodeWithText("Discard").performClick()
        waitForIdle()
        assertEquals(chain, s.arrangement)
        assertTrue(s.drafts.isEmpty())
    }

    @Test
    fun closingWhileTheSaveDialogIsOpenReplacesIt() = runComposeUiTest {
        val s = state()
        var closes by mutableIntStateOf(0)
        var closed = 0
        setContent { App(s, initialDialog = DialogKind.NONE, closeRequests = closes, onClose = { closed++ }) }
        // nothing unsaved: closing closes at once
        closes++
        waitForIdle()
        assertEquals(1, closed)
        runOnIdle { s.flipSomeTriangle() }
        menu("Arrangement", "Save file...")
        closes++
        waitForIdle()
        onNodeWithText("Save changes?").assertExists()
        onNodeWithText("Save file").assertDoesNotExist()
        onAllNodesWithText("Save JSON").fetchSemanticsNodes().let { assertEquals(1, it.size) }
        onNodeWithText("Discard").performClick()
        waitForIdle()
        assertEquals(2, closed)
    }

    @Test
    fun everyHelpPageOpensFromTheMenu() = runComposeUiTest {
        val s = state()
        setContent { App(s, initialDialog = DialogKind.NONE) }
        for (page in HelpPage.entries) {
            menu("Help", "${page.title}...")
            assertEquals(page, s.helpPage)
            onNode(hasText(page.title) and hasAnyAncestor(isDialog())).assertExists()
            onNodeWithText(helpText(page).first().take(30), substring = true).assertExists()
            onNodeWithText("OK").performClick()
            waitForIdle()
            onNodeWithTag("big-dialog").assertDoesNotExist()
        }
    }

    @Test
    fun aboutShowsLinksAndLicenseTexts() = runComposeUiTest {
        val s = state()
        setContent { App(s, initialDialog = DialogKind.NONE) }
        menu("Help", "About ArrView...")
        onNodeWithText("ArrView on GitHub").assertExists()
        onNodeWithText("Linhart and Ortner at EJC").assertExists()
        onNodeWithText("Literature").assertExists()
        onNodeWithText("built ", substring = true).assertExists()
        onNodeWithText("ArrView (MIT)").assertDoesNotExist()
        onNodeWithText("License texts").performClick()
        waitForIdle()
        onNodeWithText("ArrView (MIT)").assertExists()
        onNodeWithText("Font: DejaVu Sans Mono (Bitstream Vera license)").assertExists()
        onNodeWithText("Hide license texts").performClick()
        waitForIdle()
        onNodeWithText("ArrView (MIT)").assertDoesNotExist()
        onNodeWithText("OK").performClick()
        waitForIdle()
        onNodeWithText("ArrView on GitHub").assertDoesNotExist()
    }

    @Test
    fun welcomeStartAndTheDontShowAgainBox() = runComposeUiTest {
        var shown by mutableIntStateOf(0)
        val states = List(3) { state() }
        setContent { key(shown) { App(states[shown]) } }
        // Start without the box: shown again next time
        onNodeWithText("Welcome to ArrView").assertExists()
        onNodeWithText("Start").performClick()
        waitForIdle()
        onNodeWithText("Welcome to ArrView").assertDoesNotExist()
        assertNull(loadPref(WELCOME_PREF))
        shown = 1
        waitForIdle()
        onNodeWithText("Welcome to ArrView").assertExists()
        // the label toggles the box, twice is off again; then the box itself
        val box = onNode(hasText("Don't show again") and hasClickAction())
        box.performClick().assertIsOn()
        box.performClick().assertIsOff()
        box.performClick()
        onNodeWithText("Start").performClick()
        waitForIdle()
        assertEquals("off", loadPref(WELCOME_PREF))
        shown = 2
        waitForIdle()
        onNodeWithText("Welcome to ArrView").assertDoesNotExist()
        onNodeWithTag("canvas").assertExists()
    }
}
