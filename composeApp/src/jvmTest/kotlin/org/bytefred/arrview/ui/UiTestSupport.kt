package org.bytefred.arrview.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.text.AnnotatedString
import org.bytefred.arrview.core.Drawing
import org.bytefred.arrview.core.Point

// Helpers shared by the UI tests.

/** The text of a node: the field's text for a text field, else its (merged) text. */
fun SemanticsNodeInteraction.text(): String? =
    fetchSemanticsNode().config.let { c ->
        c.getOrNull(SemanticsProperties.EditableText)?.text ?: c.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }
    }

/** A point inside face [f]: the middle of the longest horizontal chord through the middle of its height. */
fun Drawing.inside(f: Int): Point {
    val poly = polygons[f]
    val y = (poly.minOf { it.y } + poly.maxOf { it.y }) / 2 + 1e-7
    val xs = poly.indices.mapNotNull { i ->
        val a = poly[i]
        val b = poly[(i + 1) % poly.size]
        if ((a.y > y) != (b.y > y)) a.x + (b.x - a.x) * (y - a.y) / (b.y - a.y) else null
    }.sorted()
    val (l, r) = xs.chunked(2).filter { it.size == 2 }.maxBy { it[1] - it[0] }
    return Point((l + r) / 2, y)
}

/** The screen position (canvas coordinates) inside face [f] of the drawing [d]. */
fun ViewTransform.at(d: Drawing, f: Int): Offset = toScreen(d.inside(f))

/** A face with [sides] sides that is not the outer face. */
fun AppState.bounded(sides: Int): Int = arrangement.faces.indices.first { arrangement.faces[it].size == sides && it != outerFace }

/** A bounded triangle flipped: an unsaved change. */
fun AppState.flipSomeTriangle() = flipTriangle(bounded(3))

/** An entry of the open menu (the toolbar has chips with the same labels). */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.menuItem(text: String) = onNode(hasText(text) and hasClickAction() and hasAnyAncestor(isPopup()))

/** Waits until the drawing shows the current arrangement and model. */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.awaitDrawing(s: AppState) = waitUntil(timeoutMillis = 10_000) { s.currentDrawing != null }

/** A clipboard for the tests, so Copy never touches the real one. */
@Suppress("DEPRECATION")
class FakeClipboard : ClipboardManager {
    var copied: AnnotatedString? = null

    override fun getText(): AnnotatedString? = copied

    override fun setText(annotatedString: AnnotatedString) {
        copied = annotatedString
    }
}

/** The items of the Arrangement menu, in menu order (checked in the app and against the Help texts). */
val ARRANGEMENT_MENU = listOf("Examples...", "Import...", "Export...", "Open file...", "Save file...", "Export SVG")
