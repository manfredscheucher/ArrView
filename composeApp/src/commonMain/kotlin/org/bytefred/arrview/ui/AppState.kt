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
import org.bytefred.arrview.core.ArrangementFile
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
import org.bytefred.arrview.core.arrangementsFile
import org.bytefred.arrview.core.canBeOuter
import org.bytefred.arrview.core.defaultOuterFace
import org.bytefred.arrview.core.digonCollapse
import org.bytefred.arrview.core.digonCreate
import org.bytefred.arrview.core.faceKey
import org.bytefred.arrview.core.faceWithKey
import org.bytefred.arrview.core.hasModel1
import org.bytefred.arrview.core.readArrangementFile
import org.bytefred.arrview.core.triangleFlip
import kotlin.coroutines.CoroutineContext
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** The panels next to the canvas; each one is a column that can be shown or hidden. */
enum class Widget(val label: String) {
    PROPERTIES("Properties"),
    ENCODING("Encoding"),
    INTERSECTIONS("Intersections"),
    TRIANGLES("Triangles"),
    SETTINGS("Settings"),
}

/** The widget stored under [id]; the ids of renamed or merged widgets ("LAYOUT", "SEQUENCES", ...) still work. */
internal fun widgetById(id: String): Widget? = when (id) {
    "LAYOUT" -> Widget.SETTINGS
    "SEQUENCES" -> Widget.ENCODING
    "INTERSECTION_GRAPH" -> Widget.INTERSECTIONS
    "TRIANGLE_HYPERGRAPH" -> Widget.TRIANGLES
    else -> Widget.entries.firstOrNull { it.name == id }
}

/**
 * The dialogs around model 1 (Settings, Visualization). [Confirm]: model 1 was chosen, OK switches.
 * [NotIntersecting]: model 1 was chosen but cannot show the arrangement, OK only. [Fallback]: the
 * next arrangement has no model 1, OK switches to model 2 and runs [Fallback.action].
 */
sealed class ModelDialog {
    data object Confirm : ModelDialog()
    data object NotIntersecting : ModelDialog()
    class Fallback(val action: () -> Unit) : ModelDialog()
}

enum class LayoutMode { AUTO, PORTRAIT, LANDSCAPE }

/**
 * A status message (shown in the toolbar). A [transient] one (pure info such as "Flipped a
 * triangle") disappears after [AppState.INFO_DURATION_MS]; the others stay until the next message.
 */
class StatusMessage(val text: String, val transient: Boolean)

/**
 * The arrangements of a loaded file or example set (PoCoView's sets), decoded when
 * first shown (an example set has up to 2131 members).
 */
class ArrangementSet(
    val size: Int,
    /** The colours of the crossings of member i as stored (JSON `"crossingColors"`), or null. */
    val colors: (Int) -> List<String>? = { null },
    private val member: (Int) -> Arrangement,
) {
    private val decoded = HashMap<Int, Arrangement>()

    operator fun get(i: Int): Arrangement = decoded.getOrPut(i) { member(i) }

    companion object {
        fun of(arrangements: List<Arrangement>) = ArrangementSet(arrangements.size) { arrangements[it] }

        /** What a file or an import holds, with the crossing colours its JSON gives. */
        fun read(read: List<NamedArrangement>) = ArrangementSet(read.size, { read[it].colors }) { read[it].arrangement }
    }
}

/**
 * One step of the undo history: the arrangement, its name, the chosen outer face, and the set it
 * belongs to with the shown member and the edits of the other members.
 */
private class HistoryEntry(
    val arrangement: Arrangement,
    val name: String,
    val outerKey: String?,
    val set: ArrangementSet,
    val setIndex: Int,
    val edits: Map<Int, Arrangement>,
)

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

    /** Name of the loaded arrangement or set (an example name or a file name). */
    var name by mutableStateOf(Examples.start.name)
        private set

    /** All arrangements of the loaded file or example set (PoCoView's sets); [arrangement] is member [setIndex], maybe edited. */
    var arrangementSet by mutableStateOf(ArrangementSet.of(listOf(arrangement)))
        private set

    var setIndex by mutableIntStateOf(0)
        private set

    /** Edited members of the set other than the shown one (kept while another one is shown, as in PoCoView). */
    private var edits by mutableStateOf<Map<Int, Arrangement>>(emptyMap())

    /** Members as last saved where they differ from the loaded ones; anything else is an unsaved change. */
    private var savedEdits by mutableStateOf<Map<Int, Arrangement>>(emptyMap())

    /** The arrangement as loaded; anything else is shown as modified. */
    private val loaded: Arrangement get() = arrangementSet[setIndex]

    /** The name for the toolbar status, with "set i/n" when the file or example set holds several (PoCoView's). */
    val title: String
        get() {
            val base = if (arrangementSet.size > 1) "$name · set ${setIndex + 1}/${arrangementSet.size}" else name
            return if (arrangement == loaded) base else "$base (modified)"
        }

    /** Member [i] of the set with its edits. */
    private fun member(i: Int): Arrangement = if (i == setIndex) arrangement else edits[i] ?: arrangementSet[i]

    /**
     * The colours of the crossings of the shown arrangement (one colour word per entry of its
     * sequences): those stored with the set member while it is unedited, else null.
     */
    val crossingColors: List<String>?
        get() = arrangementSet.colors(setIndex)?.takeIf { arrangement == arrangementSet[setIndex] }

    /** Member [i] as last saved. */
    private fun savedMember(i: Int): Arrangement = savedEdits[i] ?: arrangementSet[i]

    /** The model chosen in Settings (Visualization); model 1 falls back to model 2 where it does not exist. */
    var preferredModel by mutableStateOf(DrawModel.MODEL2)
        private set

    /**
     * The number of arrangements when a load holds more than one (null otherwise), so the UI can tell
     * the user (PoCoView's); the UI clears it after showing it.
     */
    var loadedSetsNotice by mutableStateOf<Int?>(null)

    /** The open model dialog (see [ModelDialog]), or null. */
    var modelDialog by mutableStateOf<ModelDialog?>(null)
        private set

    /** "Do not show again" of the model 1 dialog ([ModelDialog.Confirm]). */
    var model1ConfirmOff by mutableStateOf(false)

    /** "Do not show again" of the switch to model 2 ([ModelDialog.Fallback]). */
    var model1FallbackOff by mutableStateOf(false)

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
        get() = colorScheme == ColorScheme.DEFAULT && lineWidth == DEFAULT_LINE_WIDTH && maxColumns == DEFAULT_MAX_WIDGETS &&
            preferredModel == DrawModel.MODEL2 && layoutSettings == LayoutSettings()

    /** Settings widget "Reset to defaults": colour scheme, line width, max. widgets, visualization, max. iterations. */
    fun resetSettings() {
        colorScheme = ColorScheme.DEFAULT
        lineWidth = DEFAULT_LINE_WIDTH
        changeMaxColumns(DEFAULT_MAX_WIDGETS)
        preferredModel = DrawModel.MODEL2
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

    /** At most this many widget columns are open; opening one more closes the least recently clicked (PoCoView's). */
    var maxColumns by mutableIntStateOf(DEFAULT_MAX_WIDGETS)
        private set

    private val lastClicked = mutableMapOf<Widget, Long>()
    private var clickCounter = 0L

    init {
        for (w in openWidgets) touchWidget(w)
    }

    /** A click into a widget column (or opening it): it becomes the most recently used one. */
    fun touchWidget(w: Widget) {
        lastClicked[w] = ++clickCounter
    }

    fun toggleWidget(w: Widget) {
        if (w in openWidgets) {
            openWidgets.remove(w)
        } else {
            openWidgets.add(w)
            touchWidget(w)
            closeExtraWidgets()
        }
    }

    fun changeMaxColumns(k: Int) {
        maxColumns = k.coerceIn(1, Widget.entries.size)
        closeExtraWidgets()
    }

    private fun closeExtraWidgets() {
        while (openWidgets.size > maxColumns) openWidgets.remove(openWidgets.minBy { lastClicked[it] ?: 0L })
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

    /** Unapplied edits of the Encoding widget, per format; text equal to the current arrangement's is no draft. */
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
        MAX_WIDGETS_PREF to maxColumns.toString(),
        MODEL_PREF to preferredModel.name,
        MODEL1_CONFIRM_PREF to onOff(model1ConfirmOff),
        MODEL1_FALLBACK_PREF to onOff(model1FallbackOff),
        MAX_ITERATIONS_PREF to layoutSettings.maxIterations.toString(),
        COLOR_SCHEME_PREF to colorScheme.name,
        LINE_WIDTH_PREF to lineWidth.toString(),
    )

    /** A "Do not show again" flag as stored: "off" once ticked (as the Welcome dialog's), else "on". */
    private fun onOff(off: Boolean) = if (off) "off" else "on"

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
            for (w in open) touchWidget(w)
        }
        loadPref(WIDE_WEIGHTS_PREF)?.let { wideWeights = decodeWeights(it) }
        loadPref(STACKED_WEIGHTS_PREF)?.let { stackedWeights = decodeWeights(it) }
        loadPref(MAX_WIDGETS_PREF)?.toIntOrNull()?.let { changeMaxColumns(it) }
        closeExtraWidgets()
        enumPref<DrawModel>(MODEL_PREF)?.let { preferredModel = it }
        model1ConfirmOff = loadPref(MODEL1_CONFIRM_PREF) == "off"
        model1FallbackOff = loadPref(MODEL1_FALLBACK_PREF) == "off"
        loadPref(MAX_ITERATIONS_PREF)?.toIntOrNull()?.takeIf { it >= 1 }?.let { layoutSettings = layoutSettings.copy(maxIterations = it) }
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

    /** Switches to [m] (tests, the model dialogs); the Settings dropdown goes through [chooseModel]. */
    fun changeModel(m: DrawModel) {
        preferredModel = m
        relayout()
    }

    /**
     * The Visualization dropdown: model 2 switches at once; model 1 asks first ([ModelDialog.Confirm],
     * unless turned off), or says that it cannot show this arrangement ([ModelDialog.NotIntersecting]).
     */
    fun chooseModel(m: DrawModel) {
        when {
            m == DrawModel.MODEL2 -> changeModel(m)
            !arrangement.hasModel1 -> modelDialog = ModelDialog.NotIntersecting
            model1ConfirmOff -> changeModel(m)
            else -> modelDialog = ModelDialog.Confirm
        }
    }

    /** OK in the open model dialog; [dontShowAgain] is its checkbox. */
    fun confirmModelDialog(dontShowAgain: Boolean = false) {
        val dialog = modelDialog ?: return
        modelDialog = null
        when (dialog) {
            ModelDialog.Confirm -> {
                if (dontShowAgain) model1ConfirmOff = true
                changeModel(DrawModel.MODEL1)
            }
            ModelDialog.NotIntersecting -> {}
            is ModelDialog.Fallback -> {
                if (dontShowAgain) model1FallbackOff = true
                preferredModel = DrawModel.MODEL2
                dialog.action()
            }
        }
    }

    /** Cancel in the model 1 dialog: the model stays. */
    fun cancelModelDialog() {
        modelDialog = null
    }

    /**
     * Runs [action], which shows [next]. If model 1 is chosen but cannot show it, model 2 is chosen
     * first: at once if the notice is turned off, else after OK in [ModelDialog.Fallback].
     */
    private fun showing(next: Arrangement, action: () -> Unit) {
        when {
            preferredModel != DrawModel.MODEL1 || next.hasModel1 -> action()
            model1FallbackOff -> {
                preferredModel = DrawModel.MODEL2
                action()
            }
            else -> modelDialog = ModelDialog.Fallback(action)
        }
    }

    fun changeLayoutSettings(settings: LayoutSettings) {
        layoutSettings = settings
        relayout()
    }

    /** Draws face [f] (of the current arrangement) as the outer face (undoable). */
    fun makeOuterFace(f: Int) {
        if (!arrangement.canBeOuter(f, model) || f == outerFace) return
        undoStack.addLast(snapshot())
        if (undoStack.size > MAX_UNDO) undoStack.removeFirst()
        redoStack.clear()
        updateUndoFlags()
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

    private fun snapshot() = HistoryEntry(arrangement, name, outerKey, arrangementSet, setIndex, edits)

    private fun restore(e: HistoryEntry) {
        arrangement = e.arrangement
        name = e.name
        outerKey = e.outerKey
        arrangementSet = e.set
        setIndex = e.setIndex
        edits = e.edits
        hoverFace = null
        menuHighlight = null
        graphHighlight = null
        relayout()
    }

    /**
     * Replaces the arrangement (undoable) and shows [info]; the outer face is kept if it still
     * exists. Deferred while [ModelDialog.Fallback] asks (see [showing]).
     */
    private fun change(next: Arrangement, info: String) = showing(next) {
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
        showInfo(info)
    }

    private fun updateUndoFlags() {
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
    }

    fun undo() {
        val prev = undoStack.lastOrNull() ?: return
        showing(prev.arrangement) {
            undoStack.removeLast()
            redoStack.addLast(snapshot())
            updateUndoFlags()
            restore(prev)
            status = null
        }
    }

    fun redo() {
        val next = redoStack.lastOrNull() ?: return
        showing(next.arrangement) {
            redoStack.removeLast()
            undoStack.addLast(snapshot())
            updateUndoFlags()
            restore(next)
            status = null
        }
    }

    /** Shows [arr] under [name] (undoable), with the default outer face and a fitted view. */
    fun load(name: String, arr: Arrangement) = loadSet(name, ArrangementSet.of(listOf(arr)))

    /**
     * Loads all arrangements of a file or an example set (undoable) and shows member [start]; the
     * others are a click (or PageUp / PageDown) away, as PoCoView's sets.
     */
    fun loadSet(name: String, arrangements: ArrangementSet, start: Int = 0) {
        require(start in 0 until arrangements.size) { "no member ${start + 1} in ${arrangements.size}" }
        showing(arrangements[start]) {
            undoStack.addLast(snapshot())
            redoStack.clear()
            updateUndoFlags()
            restore(HistoryEntry(arrangements[start], name, null, arrangements, start, emptyMap()))
            savedEdits = emptyMap()
            drafts.clear()
            status = null
            requestFit()
            loadedSetsNotice = arrangements.size.takeIf { it > 1 }
        }
    }

    /**
     * Shows member [index] of the set (PoCoView's showSet): the shown member's edits are kept, and
     * the undo history, which belongs to one member, is cleared. An unapplied widget edit would be
     * lost: "Save changes?" asks first.
     */
    fun showSet(index: Int) {
        if (index !in 0 until arrangementSet.size || index == setIndex) return
        if (unappliedDraft) pendingLeave = { switchToSet(index) } else switchToSet(index)
    }

    private fun switchToSet(index: Int) = showing(member(index)) {
        val next = member(index)
        edits = storedEdits()
        setIndex = index
        arrangement = next
        outerKey = null
        hoverFace = null
        menuHighlight = null
        graphHighlight = null
        drafts.clear()
        undoStack.clear()
        redoStack.clear()
        updateUndoFlags()
        status = null
        relayout()
        requestFit()
    }

    /** [edits] with the shown member's. */
    private fun storedEdits(): Map<Int, Arrangement> =
        if (arrangement == arrangementSet[setIndex]) edits - setIndex else edits + (setIndex to arrangement)

    /** Opens a file: JSON, intersection sequences or sparse6 lines, every arrangement of it; errors go to the error dialog. */
    fun open(fileName: String, bytes: ByteArray) {
        try {
            val file = readArrangementFile(bytes.decodeToString())
            val read = file.arrangements
            loadSet(file.name ?: read.singleOrNull()?.name ?: fileName.substringBeforeLast('.'), ArrangementSet.read(read))
            showInfo("Opened $fileName" + if (read.size > 1) ": ${read.size} arrangements" else "")
        } catch (e: InvalidArrangementException) {
            error = "Cannot open $fileName: ${e.message}"
        }
    }

    /** The arrangements in pasted [text] (Import, format detected), or null after setting [error]. */
    fun parseImport(text: String): ArrangementFile? =
        try {
            readArrangementFile(text)
        } catch (e: InvalidArrangementException) {
            error = "Cannot import the text: ${e.message}"
            null
        }

    /** Loads what [parseImport] read (undoable). */
    fun loadImport(read: ArrangementFile) =
        loadSet(read.name ?: read.arrangements.singleOrNull()?.name ?: "imported", ArrangementSet.read(read.arrangements))

    /** The file name for saving, without extension. */
    val fileName: String get() = name.replace(Regex("[^A-Za-z0-9._#-]+"), "_")

    /** The suggested file name for saving as [format]. */
    fun saveName(format: FileFormat): String = "$fileName.${format.extension}"

    // ---- unsaved changes ---------------------------------------------------------------

    /** A member of the set differs from the last load or save (PoCoView's sets). */
    val arrangementUnsaved: Boolean
        get() = (edits.keys + savedEdits.keys + setIndex).any { member(it) != savedMember(it) }

    /**
     * The Encoding widget shows an edit that differs from the current arrangement's text (compared
     * live, as in PoCoView: after an undo a draft may equal it again). A model 1 edit is hidden, and
     * does not count, while the arrangement is not intersecting.
     */
    val unappliedDraft: Boolean
        get() = drafts.any { (format, text) -> format.availableFor(arrangement) && text != text(format) }

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

    /** The members being written by the file save in progress ([beginSave] .. [fileSaved]). */
    private var saving: List<Arrangement>? = null

    /** The file content to save as [format], every member of the set; [fileSaved] reports how the save ended. */
    fun beginSave(format: FileFormat): ByteArray {
        val all = (0 until arrangementSet.size).map(::member)
        saving = all
        return arrangementsFile(name, all, format) { i -> arrangementSet.colors(i)?.takeIf { all[i] == arrangementSet[i] } }.encodeToByteArray()
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
        if (done != null) savedEdits = done.withIndex().filter { (i, arr) -> arr != arrangementSet[i] }.associate { it.index to it.value }
        showInfo("Saved $fileName")
        if (!unsavedChanges) discardAndLeave()
    }

    // ---- text forms ----------------------------------------------------------------------

    /** The texts of the current arrangement, computed once per arrangement (model 2 of n = 42 is long). */
    private var texts: Pair<Arrangement, MutableMap<TextFormat, String>>? = null

    /** The current arrangement in [format] (Export, the Encoding widget); see [TextFormat.availableFor]. */
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
     * Applies the edited [format] text (undoable) and fits the view. Invalid text goes to the error dialog with the
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
        change(next, "Applied the ${format.label.lowercase()}")
        // as PoCoView's Apply: a fresh fit (and the automatic fit on resize again)
        requestFit()
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
        change(next, "Created a digon of ${op.i + 1} and ${op.j + 1}")
    }

    /** Triangle flip of face [f] (undoable). */
    fun flipTriangle(f: Int) {
        if (arrangement.faces.getOrNull(f)?.size != 3) return
        try {
            change(arrangement.triangleFlip(f), "Flipped a triangle")
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
        change(next, "Collapsed a digon")
    }

    companion object {
        /** How long a [StatusMessage.transient] message stays. */
        const val INFO_DURATION_MS = 3000L

        /** Display settings are stored this long after their last change. */
        private const val PREF_SAVE_DELAY_MS = 500L

        private const val MAX_UNDO = 500

        const val DEFAULT_LINE_WIDTH = 5f
        val LINE_WIDTH_RANGE = 0.5f..8f

        /** Widgets open at a time by default (PoCoView's). */
        const val DEFAULT_MAX_WIDGETS = 2

        /** Drawings kept for undo / redo and switching back and forth. */
        private const val CACHE_SIZE = 32
    }
}
