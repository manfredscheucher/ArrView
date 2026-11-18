package org.bytefred.arrview.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import org.bytefred.arrview.core.Cubic
import org.bytefred.arrview.core.Drawing
import org.bytefred.arrview.core.Point
import org.bytefred.arrview.core.canBeOuter
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/** World (drawing coordinates, y up) <-> screen (pixels, y down): screen = origin + (x, -y) * scale. */
class ViewTransform {
    var scale by mutableDoubleStateOf(1.0)
    var ox by mutableDoubleStateOf(0.0)
    var oy by mutableDoubleStateOf(0.0)
    var size by mutableStateOf(IntSize.Zero)

    /** The [AppState.fitRequest] this view was last fitted for: a re-created canvas keeps the zoom. */
    var fittedRequest = -1

    fun toScreen(p: Point) = Offset((ox + p.x * scale).toFloat(), (oy - p.y * scale).toFloat())
    fun toWorld(s: Offset) = Point((s.x - ox) / scale, (oy - s.y) / scale)

    fun zoomAt(anchor: Offset, factor: Double) {
        val f = factor.coerceIn(0.02, 50.0)
        val newScale = (scale * f).coerceIn(1e-3, 1e7)
        val k = newScale / scale
        ox = anchor.x - (anchor.x - ox) * k
        oy = anchor.y - (anchor.y - oy) * k
        scale = newScale
    }

    /** New canvas size (e.g. a widget column opened or closed): the view keeps its centre. */
    fun resize(newSize: IntSize) {
        // keep the last real size while the canvas is squeezed to nothing
        if (newSize.width == 0 || newSize.height == 0) return
        if (size != IntSize.Zero) {
            ox += (newSize.width - size.width) / 2.0
            oy += (newSize.height - size.height) / 2.0
        }
        size = newSize
    }

    fun zoomCenter(factor: Double) = zoomAt(Offset(size.width / 2f, size.height / 2f), factor)

    /** Shows the box (minX, minY, maxX, maxY) in the middle, with room for the labels around it. */
    fun fit(bounds: DoubleArray) {
        if (size.width == 0 || size.height == 0) return
        val (minX, minY, maxX, maxY) = bounds.toList()
        val w = maxOf(1e-9, maxX - minX)
        val h = maxOf(1e-9, maxY - minY)
        scale = min((size.width - 2 * FIT_MARGIN) / w, (size.height - 2 * FIT_MARGIN) / h).coerceAtLeast(1e-3)
        ox = size.width / 2.0 - (minX + maxX) / 2 * scale
        oy = size.height / 2.0 + (minY + maxY) / 2 * scale
    }

    private companion object {
        /** Pixels left free around the drawing (labels). */
        const val FIT_MARGIN = 36.0
    }
}

/** Distinct colours of the circles (circle i gets colour i mod size). */
private val CIRCLE_COLORS = listOf(
    Color(0xFFE53935), Color(0xFF1E88E5), Color(0xFF43A047), Color(0xFFFB8C00), Color(0xFF8E24AA),
    Color(0xFF00ACC1), Color(0xFF6D4C41), Color(0xFFD81B60), Color(0xFF7CB342), Color(0xFF3949AB),
    Color(0xFFFFB300), Color(0xFF546E7A),
)

fun circleColor(c: Int): Color = CIRCLE_COLORS[c % CIRCLE_COLORS.size]

internal val TRIANGLE_FILL = Color(0xFFFFE3A3)
internal val DIGON_FILL = Color(0xFFC9D8F7)
internal val DIGON_MARK = Color(0xFF5577CC)
private val HOVER_FILL = Color(0x332F5D62)

/** Pixels a press may move and still be a click. */
private const val TOUCH_SLOP = 4f

/** Pixels around a model 1 touching point that hit its digon. */
private const val TOUCH_RADIUS = 10.0

private const val CURVE_WIDTH = 2.5f

/**
 * The drawing of the arrangement: faces (triangles and digons filled), the curves and their labels.
 * Click a triangle to flip it, a digon to collapse it; right-click a face for its menu (also
 * "Make outer face"); drag to pan, wheel to zoom.
 */
@Composable
fun ArrangementCanvas(state: AppState, view: ViewTransform, modifier: Modifier = Modifier, onInteract: () -> Unit = {}) {
    val st by rememberUpdatedState(state)
    val textMeasurer = rememberTextMeasurer()
    val drawing = state.drawing

    // fit on request and once the canvas has a size and a drawing; a later resize keeps the zoom
    val ready = view.size.width > 0 && view.size.height > 0 && drawing != null
    LaunchedEffect(state.fitRequest, ready) {
        val d = st.drawing
        if (ready && d != null && view.fittedRequest != st.fitRequest) {
            view.fit(d.bounds)
            view.fittedRequest = st.fitRequest
        }
    }

    // the face whose context menu is open and where it opens
    var faceMenu by remember { mutableStateOf<Pair<Int, Offset>?>(null) }
    // the face with several digon creates whose menu is open and where it opens
    var createMenu by remember { mutableStateOf<Pair<Int, Offset>?>(null) }

    /** The face at a screen position, if the drawing shows the current arrangement. */
    fun hit(pos: Offset): Int? {
        val d = st.drawing ?: return null
        if (d.arrangement != st.arrangement) return null
        return d.faceAt(view.toWorld(pos), TOUCH_RADIUS / view.scale)
    }

    Box(modifier.fillMaxSize()) {
        Canvas(
            Modifier
                .fillMaxSize()
                .testTag("canvas")
                .clipToBounds()
                .background(MaterialTheme.colorScheme.surface)
                .onSizeChanged { view.resize(it) }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        var down: Offset? = null
                        var last = Offset.Zero
                        var moved = false
                        var pinchDist = 0f
                        var pinchCenter = Offset.Zero
                        while (true) {
                            val ev = awaitPointerEvent()
                            val pressed = ev.changes.filter { it.pressed }
                            when (ev.type) {
                                PointerEventType.Scroll -> {
                                    val c = ev.changes.first()
                                    // mouse wheels report about 1 per notch; some browsers report pixels
                                    val dy = c.scrollDelta.y.toDouble()
                                    val notches = (if (abs(dy) >= 10) dy / 100 else dy).coerceIn(-5.0, 5.0)
                                    view.zoomAt(c.position, 1.15.pow(-notches))
                                    c.consume()
                                }
                                PointerEventType.Press -> {
                                    if (st.consumeMenuPress()) {
                                        // the press only closes the open menu (its popup is not focusable)
                                        st.openMenu = null
                                        down = null
                                    } else {
                                        onInteract()
                                        if (ev.buttons.isSecondaryPressed || ctrlClickIsRightClick && ev.keyboardModifiers.isCtrlPressed) {
                                            down = null
                                            if (pressed.size == 1) hit(pressed[0].position)?.let { faceMenu = it to pressed[0].position }
                                        } else if (pressed.size == 1) {
                                            down = pressed[0].position
                                            last = pressed[0].position
                                            moved = false
                                        } else if (pressed.size == 2) {
                                            // second finger: pinch zoom + two-finger pan
                                            moved = true
                                            pinchDist = (pressed[0].position - pressed[1].position).getDistance()
                                            pinchCenter = (pressed[0].position + pressed[1].position) / 2f
                                        }
                                    }
                                    ev.changes.forEach { it.consume() }
                                }
                                PointerEventType.Move -> {
                                    if (pressed.size >= 2) {
                                        val d = (pressed[0].position - pressed[1].position).getDistance()
                                        val center = (pressed[0].position + pressed[1].position) / 2f
                                        view.ox += (center.x - pinchCenter.x).toDouble()
                                        view.oy += (center.y - pinchCenter.y).toDouble()
                                        if (pinchDist > 0f && d > 0f) view.zoomAt(center, (d / pinchDist).toDouble())
                                        pinchDist = d
                                        pinchCenter = center
                                    } else if (pressed.size == 1 && down != null) {
                                        val pos = pressed[0].position
                                        if (!moved && (pos - down).getDistance() > TOUCH_SLOP) moved = true
                                        if (moved) {
                                            view.ox += (pos.x - last.x).toDouble()
                                            view.oy += (pos.y - last.y).toDouble()
                                        }
                                        last = pos
                                    } else if (pressed.isEmpty()) {
                                        st.hoverFace = ev.changes.firstOrNull()?.let { hit(it.position) }
                                    }
                                    ev.changes.forEach { it.consume() }
                                }
                                PointerEventType.Release -> {
                                    if (pressed.isEmpty()) {
                                        val start = down
                                        if (start != null && !moved) hit(start)?.let { f ->
                                            if (f != st.outerFace && st.actions.createsIn(f).size >= 2) createMenu = f to start
                                            else st.clickFace(f)
                                        }
                                        down = null
                                        pinchDist = 0f
                                    } else {
                                        // back from pinch to one finger: continue panning from here
                                        last = pressed[0].position
                                        pinchDist = 0f
                                    }
                                    ev.changes.forEach { it.consume() }
                                }
                                PointerEventType.Exit -> st.hoverFace = null
                            }
                        }
                    }
                },
        ) {
            if (drawing != null) {
                val current = drawing.arrangement == state.arrangement
                val hover = state.hoverFace.takeIf { current }
                drawArrangement(drawing, view, hover, state.menuHighlight.takeIf { current })
                // "!" in the hovered cell when a click does something there (not the outer face)
                if (hover != null && hover != drawing.outerFace && state.actions.hasAction(hover)) {
                    drawing.interiorPoint(hover)?.let { p ->
                        val shift = if (hover in drawing.touchings) Offset(0f, -MARK_LIFT) else Offset.Zero
                        drawActionMark(view.toScreen(p) + shift)
                    }
                }
            }
            drawing?.labels?.forEachIndexed { c, (at, outward) ->
                val s = view.toScreen(at) + Offset((outward.x * LABEL_OFFSET).toFloat(), (-outward.y * LABEL_OFFSET).toFloat())
                val layout = textMeasurer.measure("${c + 1}", TextStyle(color = circleColor(c), fontSize = 14.sp, fontWeight = FontWeight.Bold))
                val topLeft = s - Offset(layout.size.width / 2f, layout.size.height / 2f)
                // only labels inside the canvas: drawText needs room right of / below its position
                if (topLeft.x >= 0f && topLeft.y >= 0f && topLeft.x + layout.size.width < size.width && topLeft.y + layout.size.height < size.height)
                    drawText(layout, topLeft = topLeft)
            }
        }
        faceMenu?.let { (f, at) ->
            // a zero-size anchor at the pointer: the menu opens right there
            Box(Modifier.offset { IntOffset(at.x.roundToInt(), at.y.roundToInt()) }) {
                FaceMenu(state, f) { faceMenu = null }
            }
        }
        createMenu?.let { (f, at) ->
            val entries = state.createEntries(f)
            if (entries.isEmpty()) createMenu = null
            else ActionMenu(state, at, "Create a digon", entries) { createMenu = null }
        }
    }
}

/** Pixels between a curve and its label. */
private const val LABEL_OFFSET = 14.0

/** The context menu of face [f]: what can be done with it. */
@Composable
private fun FaceMenu(state: AppState, f: Int, close: () -> Unit) {
    val arr = state.arrangement
    // the arrangement may have changed meanwhile: only a face that still exists
    val size = arr.faces.getOrNull(f)?.size ?: return close()
    val outer = f == state.outerFace
    DropdownMenu(expanded = true, onDismissRequest = close) {
        MenuHeading(faceName(size) + if (outer) " (outer face)" else "")
        if (size == 3) DropdownMenuItem(text = { Text("Flip triangle") }, onClick = { close(); state.flipTriangle(f) })
        if (size == 2) DropdownMenuItem(text = { Text("Collapse digon") }, onClick = { close(); state.collapseDigon(f) })
        if (!outer && arr.canBeOuter(f, state.model)) {
            DropdownMenuItem(text = { Text("Make outer face") }, onClick = { close(); state.makeOuterFace(f) })
        }
    }
}

private fun faceName(sides: Int) = when (sides) {
    2 -> "Digon"
    3 -> "Triangle"
    else -> "Face with $sides sides"
}

/** The curve pieces as a path in screen coordinates. */
private fun path(pieces: List<Cubic>, view: ViewTransform, close: Boolean): Path = Path().apply {
    if (pieces.isEmpty()) return@apply
    val start = view.toScreen(pieces.first().p0)
    moveTo(start.x, start.y)
    for (c in pieces) {
        val a = view.toScreen(c.c1)
        val b = view.toScreen(c.c2)
        val e = view.toScreen(c.p3)
        cubicTo(a.x, a.y, b.x, b.y, e.x, e.y)
    }
    if (close) close()
}

/** Pixels a "!" is lifted above a model 1 touching point, so the dot stays visible. */
private const val MARK_LIFT = 16f

internal val MARK_FILL = Color(0xFF2F5D62)

/** The "!" of a cell with an action: a round badge with a bar and a dot, drawn (no font needed). */
private fun DrawScope.drawActionMark(at: Offset) {
    drawCircle(MARK_FILL, 9f, at)
    drawLine(Color.White, at + Offset(0f, -5f), at + Offset(0f, 1.5f), strokeWidth = 2.6f, cap = StrokeCap.Round)
    drawCircle(Color.White, 1.5f, at + Offset(0f, 5f))
}

/** A face highlighted from a menu entry: yellow, stronger than the hover. */
internal val HIGHLIGHT_FILL = Color(0x88FFD600)

private fun DrawScope.drawArrangement(d: Drawing, view: ViewTransform, hover: Int?, highlight: Int?) {
    val faces = d.arrangement.faces
    for ((f, pieces) in d.faces.withIndex()) {
        if (pieces.isEmpty()) continue
        val fill = when (faces[f].size) {
            3 -> TRIANGLE_FILL
            2 -> DIGON_FILL
            else -> null
        }
        if (fill != null) drawPath(path(pieces, view, close = true), fill)
    }
    if (hover != null && hover != d.outerFace) d.faces.getOrNull(hover)?.takeIf { it.isNotEmpty() }?.let {
        drawPath(path(it, view, close = true), HOVER_FILL)
    }
    if (highlight != null) {
        val pieces = d.faces.getOrNull(highlight).orEmpty()
        when {
            highlight == d.outerFace -> drawPath(path(d.outerBoundary, view, close = true), HIGHLIGHT_FILL, style = Stroke(12f, cap = StrokeCap.Round))
            pieces.isNotEmpty() -> drawPath(path(pieces, view, close = true), HIGHLIGHT_FILL)
        }
    }
    for ((c, pieces) in d.circles.withIndex()) {
        drawPath(path(pieces, view, close = true), circleColor(c), style = Stroke(CURVE_WIDTH, cap = StrokeCap.Round))
    }
    // model 1: a digon is the point where its two circles touch
    for ((f, at) in d.touchings) {
        val s = view.toScreen(at)
        if (f == highlight) drawCircle(HIGHLIGHT_FILL, 14f, s)
        drawCircle(DIGON_MARK, if (f == hover) 7f else 4.5f, s)
    }
}
