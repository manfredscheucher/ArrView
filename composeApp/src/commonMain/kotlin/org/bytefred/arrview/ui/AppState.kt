package org.bytefred.arrview.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.bytefred.arrview.core.Arrangement
import org.bytefred.arrview.core.ArrangementLayout
import org.bytefred.arrview.core.CellActions
import org.bytefred.arrview.core.DigonCreate
import org.bytefred.arrview.core.DrawModel
import org.bytefred.arrview.core.Drawing
import org.bytefred.arrview.core.Examples
import org.bytefred.arrview.core.FileFormat
import org.bytefred.arrview.core.InvalidArrangementException
import org.bytefred.arrview.core.IteratedTutte
import org.bytefred.arrview.core.LayoutSettings
import org.bytefred.arrview.core.NamedArrangement
import org.bytefred.arrview.core.TextFormat
import org.bytefred.arrview.core.arrangementFile
import org.bytefred.arrview.core.canBeOuter
import org.bytefred.arrview.core.defaultOuterFace
import org.bytefred.arrview.core.digonCollapse
import org.bytefred.arrview.core.digonCreate
import org.bytefred.arrview.core.faceKey
import org.bytefred.arrview.core.faceWithKey
import org.bytefred.arrview.core.hasModel1
import org.bytefred.arrview.core.readArrangement
import org.bytefred.arrview.core.triangleFlip
import kotlin.coroutines.CoroutineContext
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** The panels next to the canvas; each one is a column that can be shown or hidden. */
enum class Widget(val label: String) {
    PROPERTIES("Properties"),
    SEQUENCES("Sequences"),
    ENCODING("Encoding"),
    INTERSECTION_GRAPH("Intersection graph"),
    TRIANGLE_HYPERGRAPH("Triangle hypergraph"),
    SETTINGS("Settings"),
}

/** The widget stored under [id] (an old "LAYOUT" is the Settings widget, which replaced it). */
internal fun widgetById(id: String): Widget? = if (id == "LAYOUT") Widget.SETTINGS else Widget.entries.firstOrNull { it.name == id }

enum class LayoutMode { AUTO, PORTRAIT, LANDSCAPE }

/**
 * A status message (shown in the toolbar). A [transient] one (pure info such as "Flipped a
 * triangle") disappears after [AppState.INFO_DURATION_MS]; the others stay until the next message.
 */
class StatusMessage(val text: String, val transient: Boolean)

/** One step of the undo history: the arrangement, its name and the chosen outer face. */
private class HistoryEntry(val arrangement: Arrangement, val name: String, val loaded: Arrangement, val outerKey: String?)

/** What a drawing is computed from; equal keys give equal drawings (the layout cache). */
private data class LayoutKey(val arrangement: Arrangement, val model: DrawModel, val outerKey: String?, val settings: LayoutSettings)

/**
 * All GUI state and actions. Plain Compose state + a coroutine scope. The drawing is computed in
 * a cancellable background job ([layoutContext]) whenever the arrangement, the model, the outer
 * face or the layout settings change; until it is done the previous drawing stays.
 */
class AppState(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
    private val layoutContext: CoroutineContext = Dispatchers.Default,
) {
    var arrangement by mutableStateOf(Examples.start.arrangement())
        private set

    /** Name of the loaded arrangement (an example name or a file name). */
    var name by mutableStateOf(Examples.start.name)
        private set

    /** The arrangement as loaded; anything else is shown as modified. */
    private var loaded = arrangement

    /** The arrangement at the last load or save; anything else is an unsaved change. */
    private var saved by mutableStateOf(arrangement)

    /** The name for the toolbar status. */
    val title: String get() = if (arrangement == loaded) name else "$name (modified)"

    /** The model chosen in the menu; model 1 falls back to model 2 where it does not exist. */
    var preferredModel by mutableStateOf(DrawModel.MODEL1)
        private set

    /** The model drawn. */
    val model: DrawModel get() = if (preferredModel == DrawModel.MODEL1 && arrangement.hasModel1) DrawModel.MODEL1 else DrawModel.MODEL2

    /** The chosen outer face ([faceKey]), null for the default. */
    private var outerKey by mutableStateOf<String?>(null)

    /** The outer face of the drawing, if it shows the current arrangement and model. */
    val outerFace: Int?
        get() = currentDrawing?.outerFace

    /** The colours of the circles (Settings widget). */
    var colorScheme by mutableStateOf(ColorScheme.DEFAULT)

    /** Width of the curves in pixels, on the canvas and in the SVG export (Settings widget). */
    var lineWidth by mutableFloatStateOf(DEFAULT_LINE_WIDTH)

    /** Everything of the Settings widget is at its default. */
    val settingsAreDefault: Boolean
        get() = colorScheme == ColorScheme.DEFAULT && lineWidth == DEFAULT_LINE_WIDTH &&
            preferredModel == DrawModel.MODEL1 && layoutSettings == LayoutSettings()

    /** Settings widget "Reset to defaults": colour scheme, line width, model, max. iterations, eps. */
    fun resetSettings() {
        colorScheme = ColorScheme.DEFAULT
        lineWidth = DEFAULT_LINE_WIDTH
        preferredModel = DrawModel.MODEL1
        layoutSettings = LayoutSettings()
        relayout()
    }

    /** Settings of the iterated Tutte embedding (edited in a widget). */
    var layoutSettings by mutableStateOf(LayoutSettings())
        private set

    /** The drawing if it shows the current arrangement and model (null while the next one is computed). */
    val currentDrawing: Drawing? get() = drawing?.takeIf { it.arrangement == arrangement && it.model == model }

    /** The latest finished drawing; it may still show the previous arrangement while the next one is computed. */
    var drawing by mutableStateOf<Drawing?>(null)
        private set

    /** The face under the mouse (indices of the drawing's arrangement). */
    var hoverFace by mutableStateOf<Int?>(null)

    /** The faces and sides (of the current arrangement) highlighted in the canvas while the pointer is on a menu entry for them. */
    var menuHighlight by mutableStateOf<Highlight?>(null)

    /** The pair or triple (circles) of a hovered list entry below a graph view, highlighted in the graph. */
    var graphHighlight by mutableStateOf<List<Int>?>(null)

    private var actionsCache: CellActions? = null

    /** What can be flipped, collapsed or created in the current arrangement (computed once per arrangement). */
    val actions: CellActions
        get() = actionsCache?.takeIf { it.arrangement == arrangement } ?: CellActions(arrangement).also { actionsCache = it }

    /** Number of focused text fields: keyboard shortcuts are off while one has focus. */
    var textFieldsFocused by mutableIntStateOf(0)

    /** The open menu's title (one for all menus), or null; keyboard shortcuts are off while a menu is open. */
    var openMenu by mutableStateOf<String?>(null)

    /** The keyboard shortcuts apply: no text field has focus and no menu is open. */
    val shortcutsActive: Boolean get() = textFieldsFocused == 0 && openMenu == null

    /** When an outside click last closed a menu (see [closeMenuByOutsideClick]). */
    private var menuClosedAt: TimeMark? = null

    /** An outside click closed the open menu; that click must not also act on the canvas. */
    fun closeMenuByOutsideClick() {
        menuClosedAt = TimeSource.Monotonic.markNow()
        openMenu = null
    }

    /**
     * Whether a canvas press only closes the menu: one is open, or it was just closed by this
     * same press (the menu's outside-click dismissal comes before the canvas sees the press).
     * The mark is used up, so only the closing press is swallowed.
     */
    fun consumeMenuPress(): Boolean {
        val justClosed = menuClosedAt?.let { it.elapsedNow() < MENU_CLICK_GUARD } == true
        menuClosedAt = null
        return openMenu != null || justClosed
    }

    var layoutMode by mutableStateOf(LayoutMode.AUTO)

    /**
     * Shares of the canvas (key null) and the widget columns, changed by dragging the dividers;
     * kept apart for side by side ([wideWeights]) and stacked ([stackedWeights]).
     */
    var wideWeights by mutableStateOf<Map<Widget?, Float>>(emptyMap())
    var stackedWeights by mutableStateOf<Map<Widget?, Float>>(emptyMap())

    /** Open widget columns, in opening order. */
    val openWidgets = mutableStateListOf(Widget.PROPERTIES)

    fun toggleWidget(w: Widget) {
        if (w in openWidgets) openWidgets.remove(w) else openWidgets.add(w)
    }

    /** The status message (see [showInfo] and [showNote]). */
    var status by mutableStateOf<StatusMessage?>(null)
        private set

    /** A load error, shown in a modal error dialog until the user clears it with OK. */
    var error by mutableStateOf<String?>(null)

    /** The Help page the Help dialog shows. */
    var helpPage by mutableStateOf(HelpPage.GENERAL)

    /** What the Examples dialog shows, kept while it is closed. */
    val examples = ExamplesState()

    /**
     * Unapplied edits of the Sequences and Encoding widgets, per format; text equal to the
     * current arrangement's is no draft.
     */
    val drafts = mutableStateMapOf<TextFormat, String>()

    /** Incremented to ask the canvas to fit the view to the drawing. */
    var fitRequest by mutableIntStateOf(0)
        private set

    private val undoStack = ArrayDeque<HistoryEntry>()
    private val redoStack = ArrayDeque<HistoryEntry>()
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    private val cache = LinkedHashMap<LayoutKey, Drawing>()
    private var layoutJob: Job? = null

    init {
        loadDisplayPrefs()
        relayout()
    }

    // ---- remembered display settings ---------------------------------------------------

    /** The remembered display settings, encoded as pref key -> value. */
    private fun displayPrefs(): Map<String, String> = mapOf(
        LAYOUT_PREF to layoutMode.name,
        OPEN_WIDGETS_PREF to openWidgets.joinToString(",") { it.name },
        WIDE_WEIGHTS_PREF to encodeWeights(wideWeights),
        STACKED_WEIGHTS_PREF to encodeWeights(stackedWeights),
        MODEL_PREF to preferredModel.name,
        MAX_ITERATIONS_PREF to layoutSettings.maxIterations.toString(),
        EPS_PREF to layoutSettings.eps.toString(),
        COLOR_SCHEME_PREF to colorScheme.name,
        LINE_WIDTH_PREF to lineWidth.toString(),
    )

    /** What [displayPrefs] was at the last load or save: only changed settings are written. */
    private var savedDisplay: Map<String, String> = emptyMap()

    /** Stores the display settings that changed since the last save. */
    fun saveDisplayPrefs() {
        val now = displayPrefs()
        for ((key, value) in now) if (savedDisplay[key] != value) savePref(key, value)
        savedDisplay = now
    }

    /**
     * Stores the display settings whenever they change, a moment after the last change (a divider
     * drag is written once). Runs while the app is shown (App starts it).
     */
    suspend fun keepDisplayPrefs() {
        snapshotFlow { displayPrefs() }.collectLatest {
            delay(PREF_SAVE_DELAY_MS)
            saveDisplayPrefs()
        }
    }

    private fun loadDisplayPrefs() {
        enumPref<LayoutMode>(LAYOUT_PREF)?.let { layoutMode = it }
        loadPref(OPEN_WIDGETS_PREF)?.let { stored ->
            val open = stored.split(',').mapNotNull(::widgetById).distinct()
            openWidgets.clear()
            openWidgets.addAll(open)
        }
        loadPref(WIDE_WEIGHTS_PREF)?.let { wideWeights = decodeWeights(it) }
        loadPref(STACKED_WEIGHTS_PREF)?.let { stackedWeights = decodeWeights(it) }
        enumPref<DrawModel>(MODEL_PREF)?.let { preferredModel = it }
        loadPref(MAX_ITERATIONS_PREF)?.toIntOrNull()?.takeIf { it >= 1 }?.let { layoutSettings = layoutSettings.copy(maxIterations = it) }
        loadPref(EPS_PREF)?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }?.let { layoutSettings = layoutSettings.copy(eps = it) }
        enumPref<ColorScheme>(COLOR_SCHEME_PREF)?.let { colorScheme = it }
        loadPref(LINE_WIDTH_PREF)?.toFloatOrNull()?.takeIf { it.isFinite() }?.let { lineWidth = it.coerceIn(LINE_WIDTH_RANGE) }
        savedDisplay = displayPrefs()
    }

    // ---- status ------------------------------------------------------------------------

    /** Pure info ("Flipped a triangle"): disappears after [INFO_DURATION_MS]. */
    fun showInfo(text: String) {
        val m = StatusMessage(text, transient = true)
        status = m
        scope.launch {
            delay(INFO_DURATION_MS)
            if (status === m) status = null
        }
    }

    /** A result or warning: stays until the next message. */
    fun showNote(text: String?) {
        status = text?.let { StatusMessage(it, transient = false) }
    }

    // ---- view --------------------------------------------------------------------------

    fun requestFit() {
        fitRequest++
    }

    fun changeModel(m: DrawModel) {
        preferredModel = m
        relayout()
    }

    fun changeLayoutSettings(settings: LayoutSettings) {
        layoutSettings = settings
        relayout()
    }

    /** Draws face [f] (of the current arrangement) as the outer face. */
    fun makeOuterFace(f: Int) {
        if (!arrangement.canBeOuter(f, model)) return
        outerKey = arrangement.faceKey(f)
        relayout()
    }

    /**
     * Starts computing the drawing of the current state (cancelling a running one), or takes it
     * from the cache. The previous drawing stays until the new one is done.
     */
    private fun relayout() {
        layoutJob?.cancel()
        val key = LayoutKey(arrangement, model, outerKey, layoutSettings)
        cache[key]?.let { drawing = it; return }
        layoutJob = scope.launch {
            val result = try {
                withContext(layoutContext) { computeDrawing(key) }
            } catch (e: IllegalArgumentException) {
                showNote("Cannot draw: ${e.message}")
                return@launch
            }
            cache[key] = result
            while (cache.size > CACHE_SIZE) cache.remove(cache.keys.first())
            drawing = result
        }
    }

    private suspend fun computeDrawing(key: LayoutKey): Drawing {
        val arr = key.arrangement
        // the chosen outer face if it exists and fits the model, else the default one
        val outer = key.outerKey?.let { arr.faceWithKey(it) }?.takeIf { arr.canBeOuter(it, key.model) }
            ?: arr.defaultOuterFace(key.model)
        val layout = ArrangementLayout(arr, key.model, outer)
        val tutte = IteratedTutte(layout.problem, key.settings)
        // one embedding per step; in between the UI runs (the web has one thread) or the job is cancelled
        while (!tutte.step()) yield()
        return layout.drawing(tutte.positions)
    }

    /** Waits until the drawing of the current state is done (tests). */
    suspend fun awaitLayout() {
        layoutJob?.join()
    }

    // ---- editing -----------------------------------------------------------------------

    private fun snapshot() = HistoryEntry(arrangement, name, loaded, outerKey)

    private fun restore(e: HistoryEntry) {
        arrangement = e.arrangement
        name = e.name
        loaded = e.loaded
        outerKey = e.outerKey
        hoverFace = null
        menuHighlight = null
        graphHighlight = null
        relayout()
    }

    /** Replaces the arrangement (undoable); the outer face is kept if it still exists. */
    private fun change(next: Arrangement) {
        // the face drawn outside now stays outside if the change keeps it
        val keep = outerFace?.let { arrangement.faceKey(it) } ?: outerKey
        undoStack.addLast(snapshot())
        if (undoStack.size > MAX_UNDO) undoStack.removeFirst()
        redoStack.clear()
        updateUndoFlags()
        arrangement = next
        outerKey = keep
        hoverFace = null
        menuHighlight = null
        graphHighlight = null
        relayout()
    }

    private fun updateUndoFlags() {
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
    }

    fun undo() {
        val prev = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(snapshot())
        updateUndoFlags()
        restore(prev)
        status = null
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(snapshot())
        updateUndoFlags()
        restore(next)
        status = null
    }

    /** Shows [arr] under [name] (undoable), with the default outer face and a fitted view. */
    fun load(name: String, arr: Arrangement) {
        undoStack.addLast(snapshot())
        redoStack.clear()
        updateUndoFlags()
        restore(HistoryEntry(arr, name, arr, null))
        saved = arr
        drafts.clear()
        status = null
        requestFit()
    }

    /** Opens a file: JSON, intersection sequences or a sparse6 line; errors go to the error dialog. */
    fun open(fileName: String, bytes: ByteArray) {
        try {
            val read = readArrangement(bytes.decodeToString())
            load(read.name ?: fileName.substringBeforeLast('.'), read.arrangement)
            showInfo("Opened $fileName")
        } catch (e: InvalidArrangementException) {
            error = "Cannot open $fileName: ${e.message}"
        }
    }

    /** The arrangement in pasted [text] (Import, format detected), or null after setting [error]. */
    fun parseImport(text: String): NamedArrangement? =
        try {
            readArrangement(text)
        } catch (e: InvalidArrangementException) {
            error = "Cannot import the text: ${e.message}"
            null
        }

    /** The file name for saving, without extension. */
    val fileName: String get() = name.replace(Regex("[^A-Za-z0-9._#-]+"), "_")

    /** The suggested file name for saving as [format]. */
    fun saveName(format: FileFormat): String = "$fileName.${format.extension}"

    // ---- unsaved changes ---------------------------------------------------------------

    /** The arrangement differs from the last load or save. */
    val arrangementUnsaved: Boolean get() = arrangement != saved

    /**
     * A Sequences or Encoding widget holds an edit that differs from the current arrangement's
     * text (compared live, as in PoCoView: after an undo a draft may equal it again).
     */
    val unappliedDraft: Boolean
        get() = drafts.any { (format, text) -> !format.availableFor(arrangement) || text != text(format) }

    /** The arrangement is unsaved or a widget has an unapplied edit. */
    val unsavedChanges: Boolean get() = arrangementUnsaved || unappliedDraft

    /**
     * What Load / Import / Open file or closing the app wants to do once the user has answered
     * "Save changes?" (PoCoView's); the dialog shows while it is set.
     */
    var pendingLeave by mutableStateOf<(() -> Unit)?>(null)
        private set

    /** Runs [action] (which replaces the arrangement or quits), asking "Save changes?" first if there are unsaved changes. */
    fun leave(action: () -> Unit) {
        if (unsavedChanges) pendingLeave = action else action()
    }

    /** "Discard": drops the changes and goes on with the pending action. */
    fun discardAndLeave() {
        val action = pendingLeave ?: return
        pendingLeave = null
        action()
    }

    fun cancelLeave() {
        pendingLeave = null
    }

    /** The arrangement being written by the file save in progress ([beginSave] .. [fileSaved]). */
    private var saving: Arrangement? = null

    /** The file content to save as [format]; [fileSaved] reports how the save ended. */
    fun beginSave(format: FileFormat): ByteArray {
        saving = arrangement
        return arrangementFile(name, arrangement, format).encodeToByteArray()
    }

    /**
     * A file save finished as [fileName], or was cancelled or failed (null). The saved arrangement
     * is the new clean state; a pending action goes on once nothing is unsaved (else "Save
     * changes?" stays), a cancelled or failed save cancels it.
     */
    fun fileSaved(fileName: String?) {
        val done = saving
        saving = null
        if (fileName == null) {
            cancelLeave()
            return
        }
        if (done != null) saved = done
        showInfo("Saved $fileName")
        if (!unsavedChanges) discardAndLeave()
    }

    // ---- text forms ----------------------------------------------------------------------

    /** The texts of the current arrangement, computed once per arrangement (model 2 of n = 42 is long). */
    private var texts: Pair<Arrangement, MutableMap<TextFormat, String>>? = null

    /** The current arrangement in [format] (Export, the Sequences and Encoding widgets); see [TextFormat.availableFor]. */
    fun text(format: TextFormat): String {
        val cache = texts?.takeIf { it.first == arrangement }?.second ?: mutableMapOf<TextFormat, String>().also { texts = arrangement to it }
        return cache.getOrPut(format) { format.write(arrangement) }
    }

    /** A widget edit of the [format] text; text equal to the current one is no draft. */
    fun editDraft(format: TextFormat, text: String) {
        if (format.availableFor(arrangement) && text == text(format)) drafts.remove(format) else drafts[format] = text
    }

    fun revertDraft(format: TextFormat) {
        drafts.remove(format)
    }

    /**
     * Applies the edited [format] text (undoable). Invalid text goes to the error dialog with the
     * reason (sequences: the Linhart-Ortner condition that fails) and the draft stays.
     */
    fun applyDraft(format: TextFormat) {
        val text = drafts[format] ?: return
        val next = try {
            format.read(text)
        } catch (e: InvalidArrangementException) {
            error = "Cannot apply the ${format.label.lowercase()}: ${e.message}"
            return
        }
        drafts.remove(format)
        if (next == arrangement) return
        change(next)
        showInfo("Applied the ${format.label.lowercase()}")
    }

    /**
     * A click on face [f] of the [drawing] (the outer face too): a triangle flips, a digon
     * collapses, a face with exactly one digon create does it (with several, the canvas opens a
     * menu instead); other faces do nothing. Ignored while the drawing still shows a previous
     * arrangement.
     */
    fun clickFace(f: Int) {
        if (outerFace == null) return
        when (arrangement.faces.getOrNull(f)?.size) {
            null -> return
            3 -> flipTriangle(f)
            2 -> collapseDigon(f)
            else -> actions.createsIn(f).singleOrNull()?.let(::createDigon)
        }
    }

    /** Digon create (undoable): circles i and j get two crossings and a digon in the face. */
    fun createDigon(op: DigonCreate) {
        val next = try {
            arrangement.digonCreate(op)
        } catch (e: IllegalArgumentException) {
            showNote("Cannot create the digon: ${e.message}")
            return
        }
        change(next)
        showInfo("Created a digon of ${op.i + 1} and ${op.j + 1}")
    }

    /** Triangle flip of face [f] (undoable). */
    fun flipTriangle(f: Int) {
        if (arrangement.faces.getOrNull(f)?.size != 3) return
        try {
            change(arrangement.triangleFlip(f))
            showInfo("Flipped a triangle")
        } catch (e: InvalidArrangementException) {
            showNote("Cannot flip: ${e.message}")
        }
    }

    /** Digon collapse of face [f] (undoable); refused if the arrangement would fall apart. */
    fun collapseDigon(f: Int) {
        if (arrangement.faces.getOrNull(f)?.size != 2) return
        val next = try {
            arrangement.digonCollapse(f)
        } catch (e: InvalidArrangementException) {
            showNote("Cannot collapse: ${e.message}")
            return
        }
        if (next == null) {
            showNote("the arrangement would fall apart")
            return
        }
        change(next)
        showInfo("Collapsed a digon")
    }

    companion object {
        /** How long a [StatusMessage.transient] message stays. */
        const val INFO_DURATION_MS = 3000L

        /** Display settings are stored this long after their last change. */
        private const val PREF_SAVE_DELAY_MS = 500L

        private const val MAX_UNDO = 500

        const val DEFAULT_LINE_WIDTH = 4f
        val LINE_WIDTH_RANGE = 0.5f..8f

        /** Drawings kept for undo / redo and switching back and forth. */
        private const val CACHE_SIZE = 32
    }
}
