package org.bytefred.arrview.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import org.bytefred.arrview.GeneratedBuildInfo
import org.bytefred.arrview.GeneratedLicenses
import org.bytefred.arrview.core.FileFormat
import org.bytefred.arrview.core.PSEUDOCIRCLES_WEBSITE
import org.bytefred.arrview.core.TextFormat

/** Which dialog is open (the menu bar and the Welcome dialog open them); HELP shows [AppState.helpPage]. */
enum class DialogKind { NONE, ABOUT, IMPORT, EXPORT, SAVE, EXAMPLES, WELCOME, HELP }

/** The modal dialogs: the open [kind], "Save changes?" and the error dialog on top. */
@Composable
fun Dialogs(state: AppState, kind: DialogKind, open: (DialogKind) -> Unit, close: () -> Unit) {
    val saver = rememberFileSaver(onError = { state.error = it }, onSaved = state::fileSaved)
    fun save(format: FileFormat) = saver(state.saveName(format), state.beginSave(format))
    when (kind) {
        DialogKind.NONE -> {}
        DialogKind.IMPORT -> ImportDialog(state, close)
        DialogKind.EXPORT -> ExportDialog(state, close)
        // "Save changes?" replaces it (no two format dialogs on top of each other)
        DialogKind.SAVE -> if (state.pendingLeave == null) {
            SaveChoiceDialog("Save file", null, FileFormat.entries, onPick = { save(it); close() }, onDismiss = close) {
                TextButton(onClick = close) { Text("Cancel") }
            }
        }
        DialogKind.EXAMPLES -> ExamplesDialog(state, close)
        DialogKind.WELCOME -> WelcomeDialog(close = close, showExamples = { open(DialogKind.EXAMPLES) })
        DialogKind.ABOUT -> AboutDialog(close)
        DialogKind.HELP -> HelpDialog(state.helpPage, state.colorScheme, close)
    }
    LaunchedEffect(state.pendingLeave != null) { if (state.pendingLeave != null && kind == DialogKind.SAVE) close() }
    // Load / Import / Open file / closing with unsaved changes (PoCoView's); after the other
    // dialogs, so it shows on top of the one that asked. The format buttons save the arrangement;
    // a widget draft is never saved implicitly, only discarded.
    if (state.pendingLeave != null) SaveChoiceDialog(
        "Save changes?",
        unsavedText(state),
        if (state.arrangementUnsaved) FileFormat.entries else emptyList(),
        onPick = ::save,
        onDismiss = state::cancelLeave,
    ) {
        TextButton(onClick = state::discardAndLeave) { Text("Discard") }
        TextButton(onClick = state::cancelLeave) { Text("Cancel") }
    }
    // the model dialogs (Settings, or a change that model 1 cannot show) on top of what opened them
    state.modelDialog?.let { ModelDialogView(state, it) }
    state.loadedSetsNotice?.let { count -> if (loadPref(SETS_NOTICE_PREF) != "off") SetsLoadedDialog(state, count) else state.loadedSetsNotice = null }
    // last, so it shows on top of the dialog the error came from
    state.error?.let { ErrorDialog(it) { state.error = null } }
}

/**
 * The model dialogs (see [ModelDialog]): a short text, "Don't show again" where the dialog can be
 * turned off, OK, and Cancel for the model 1 choice. Closing the dialog otherwise counts as OK
 * (Cancel for the model 1 choice).
 */
@Composable
private fun ModelDialogView(state: AppState, dialog: ModelDialog) {
    var dontShow by remember(dialog) { mutableStateOf(false) }
    val ok = { state.confirmModelDialog(dontShow) }
    val confirm = dialog == ModelDialog.Confirm
    AlertDialog(
        onDismissRequest = if (confirm) state::cancelModelDialog else ok,
        confirmButton = { TextButton(onClick = ok, Modifier.testTag("model-dialog-ok")) { Text("OK") } },
        dismissButton = if (confirm) ({ TextButton(onClick = state::cancelModelDialog) { Text("Cancel") } }) else null,
        title = { Text(if (dialog is ModelDialog.Fallback) "Model 2" else "Model 1") },
        text = {
            Column {
                Text(modelDialogText(dialog), style = MaterialTheme.typography.bodyMedium)
                if (dialog != ModelDialog.NotIntersecting) DontShowAgain(dontShow) { dontShow = it }
            }
        },
    )
}

/** The text of a model dialog (also checked by the tests). */
internal fun modelDialogText(dialog: ModelDialog): String = when (dialog) {
    ModelDialog.Confirm -> "Model 1 is the legacy view. It can only show intersecting arrangements."
    ModelDialog.NotIntersecting -> "Model 1 can only show intersecting arrangements. This arrangement is not intersecting."
    is ModelDialog.Fallback -> "The arrangement is no longer intersecting. Switching to model 2."
}

/** The "Don't show again" checkbox of a dialog; the label toggles too. */
@Composable
private fun DontShowAgain(checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.padding(top = 8.dp).toggleable(checked, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked, onCheckedChange = null)
        Text("Don't show again", style = MaterialTheme.typography.bodyMedium)
    }
}

/** A load or apply error the user must see (the toolbar status is for info): one OK button. */
@Composable
private fun ErrorDialog(message: String, onDismiss: () -> Unit) = AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Error") },
    text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
    confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
)

/** What "Save changes?" says is unsaved, or null if nothing (the dialog shows no empty text). */
private fun unsavedText(state: AppState): String? = buildList {
    if (state.arrangementUnsaved) add("Your changes to \"${state.name}\" are not saved.")
    if (state.unappliedDraft) add("A text edited in the Encoding widget is not applied.")
}.joinToString(" ").takeIf { it.isNotEmpty() }

/**
 * Which format to save as (PoCoView's): [message], one full-width button per format in
 * [formats], and [buttons] below them (Cancel, or Discard / Cancel in "Save changes?").
 */
@Composable
private fun SaveChoiceDialog(
    title: String,
    message: String?,
    formats: List<FileFormat>,
    onPick: (FileFormat) -> Unit,
    onDismiss: () -> Unit,
    buttons: @Composable () -> Unit,
) = AlertDialog(
    onDismissRequest = onDismiss,
    confirmButton = { Row { buttons() } },
    title = { Text(title) },
    text = {
        Column(Modifier.widthIn(min = 300.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            message?.let { Text(it, Modifier.padding(bottom = 4.dp), style = MaterialTheme.typography.bodyMedium) }
            for (format in formats) FilledTonalButton(onClick = { onPick(format) }, Modifier.fillMaxWidth()) {
                Text(saveLabel(format))
            }
        }
    },
)

private fun saveLabel(format: FileFormat) = when (format) {
    FileFormat.JSON -> "Save JSON"
    FileFormat.TXT -> "Save text"
}

/** The current arrangement as text to copy: sequences, model 1 or model 2 sparse6. */
@Composable
private fun ExportDialog(state: AppState, close: () -> Unit) {
    var format by remember { mutableStateOf(TextFormat.SEQUENCES) }
    val clipboard = LocalClipboardManager.current
    val text = state.text(format)
    BigDialog(
        "Export",
        onDismiss = close,
        buttons = {
            TextButton(onClick = { clipboard.setText(AnnotatedString(text)); state.showInfo("Copied") }) { Text("Copy") }
            TextButton(onClick = close) { Text("Close") }
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (f in TextFormat.entries) FilterChip(
                selected = format == f,
                onClick = { format = f },
                enabled = f.availableFor(state.arrangement),
                label = { Text(f.label) },
            )
        }
        OutlinedTextField(
            text, onValueChange = {}, readOnly = true,
            textStyle = MonoStyle,
            modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp).textFocus(state).testTag("export-text"),
        )
    }
}

/** Paste an arrangement; the format is detected. */
@Composable
private fun ImportDialog(state: AppState, close: () -> Unit) {
    var text by remember { mutableStateOf("") }
    BigDialog(
        "Import",
        onDismiss = close,
        buttons = {
            TextButton(onClick = close) { Text("Cancel") }
            TextButton(onClick = {
                state.parseImport(text)?.let { read -> state.leave { state.loadImport(read); close() } }
            }) { Text("Import") }
        },
    ) {
        Text(IMPORT_FORMATS, style = MonoStyle)
        OutlinedTextField(
            text, onValueChange = { text = it },
            textStyle = MonoStyle,
            modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp).textFocus(state).testTag("import-text"),
        )
    }
}

private const val IMPORT_FORMATS = """{1: [2, -2, ...], 2: [...], ...}           sequences
:...                                       sparse6, model 1 or model 2
{"name": ..., "sequences": [[...], ...]}   JSON file"""

/** Yellow box asking for interesting arrangements and feedback, with a clickable e-mail address. */
@Composable
fun FeedbackBox(question: String) {
    Column(
        Modifier.fillMaxWidth().background(Color(0xFFFFF3B0), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFFE0C200), RoundedCornerShape(8.dp)).padding(10.dp),
    ) {
        Text(question, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF3A3000))
        LinkText(EMAIL, feedbackMailto(), MaterialTheme.typography.bodyMedium)
    }
}

private fun feedbackMailto(): String {
    fun enc(s: String) = buildString {
        for (b in s.encodeToByteArray()) {
            val c = (b.toInt() and 0xFF).toChar()
            if (c.isLetterOrDigit() && c.code < 128 || c in "-_.~") append(c)
            else append("%" + (b.toInt() and 0xFF).toString(16).uppercase().padStart(2, '0'))
        }
    }
    val subject = "ArrView: arrangement / feedback"
    val body = "Hi Manfred,\n\n(an interesting arrangement, an idea, or other feedback)\n\n" +
        "Arrangement (Arrangement > Export, sequences or sparse6):\n\n"
    return "mailto:$EMAIL?subject=${enc(subject)}&body=${enc(body)}"
}

private const val EMAIL = "manfred.scheucher@proton.me"

/** Shown at start until "Don't show again" is ticked. */
@Composable
private fun WelcomeDialog(close: () -> Unit, showExamples: () -> Unit) {
    var dontShow by remember { mutableStateOf(false) }
    fun done() { if (dontShow) savePref(WELCOME_PREF, "off"); close() }
    AlertDialog(
        onDismissRequest = ::done,
        confirmButton = { TextButton(onClick = ::done) { Text("Start") } },
        dismissButton = { TextButton(onClick = { if (dontShow) savePref(WELCOME_PREF, "off"); showExamples() }) { Text("Examples") } },
        title = { Text("Welcome to ArrView") },
        text = {
            Column {
                Text(
                    WELCOME_TEXT,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(10.dp))
                FeedbackBox("Interesting arrangements, ideas or general feedback?")
                DontShowAgain(dontShow) { dontShow = it }
            }
        },
    )
}

/** After loading a file or an example set with several arrangements (PoCoView's sets notice). */
@Composable
private fun SetsLoadedDialog(state: AppState, count: Int) {
    var dontShow by remember { mutableStateOf(false) }
    fun done() { if (dontShow) savePref(SETS_NOTICE_PREF, "off"); state.loadedSetsNotice = null }
    AlertDialog(
        onDismissRequest = ::done,
        confirmButton = { TextButton(onClick = ::done) { Text("OK") } },
        title = { Text("$count arrangements loaded") },
        text = {
            Column {
                Text(
                    "Showing ${if (state.setIndex == 0) "the first" else "arrangement ${state.setIndex + 1}"} of $count. Switch between them with the arrows < > at the top (or PageUp / PageDown).",
                    style = MaterialTheme.typography.bodyMedium,
                )
                DontShowAgain(dontShow) { dontShow = it }
            }
        },
    )
}

/** About: description, feedback box, project links and literature, build identity, license texts. */
@Composable
private fun AboutDialog(close: () -> Unit) = AlertDialog(
    onDismissRequest = close,
    confirmButton = { TextButton(onClick = close) { Text("OK") } },
    title = { Text("ArrView") },
    text = {
        ScrollColumn(Modifier.heightIn(max = 520.dp)) {
            Text(ABOUT, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            FeedbackBox("ArrView is in early development. Feedback is very welcome:")
            Spacer(Modifier.height(8.dp))
            LinkText("ArrView on GitHub", "https://github.com/manfredscheucher/ArrView", MaterialTheme.typography.bodyMedium)
            LinkText("Arrangements of pseudocircles (website, data and formats)", PSEUDOCIRCLES_WEBSITE, MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Text("Literature", style = MaterialTheme.typography.titleSmall)
            for (paper in PAPERS) Text(paper, Modifier.padding(vertical = 2.dp), style = MaterialTheme.typography.bodySmall)
            LinkText("Linhart and Ortner at EJC", LINHART_ORTNER, MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Text(buildLine(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            var showLicenses by remember { mutableStateOf(false) }
            TextButton(onClick = { showLicenses = !showLicenses }) {
                Text(if (showLicenses) "Hide license texts" else "License texts")
            }
            if (showLicenses) {
                for (notice in GeneratedLicenses.NOTICES) {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text(notice.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Text(notice.text, style = MonoStyle.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize))
                }
            }
        }
    },
)

private const val ABOUT = "ArrView shows arrangements of pseudocircles, draws them with iterated Tutte embeddings and " +
    "lets you change them by triangle flips and digon collapses. The examples come from the pseudocircles website."


private const val LINHART_ORTNER = "https://www.combinatorics.org/ojs/index.php/eljc/article/view/v11i1r30"

internal val PAPERS = listOf(
    "S. Felsner, M. Scheucher: Arrangements of Pseudocircles: Triangles and Drawings. Discrete & Computational Geometry (2021).",
    "S. Felsner, M. Scheucher: Arrangements of Pseudocircles: On Circularizability. Discrete & Computational Geometry (2020).",
    "J. Linhart, R. Ortner: On the combinatorial structure of arrangements of oriented pseudocircles. " +
        "Electronic Journal of Combinatorics 11 (2004), #R30.",
)

/** "sha | committed <date> | built <date>" (GeneratedBuildInfo, written at build time); unknown parts are left out. */
private fun buildLine(): String = buildList {
    val sha = GeneratedBuildInfo.COMMIT_SHA
    if (sha != "unknown") add(if (GeneratedBuildInfo.IS_DIRTY == "dirty") "$sha (modified)" else sha)
    if (GeneratedBuildInfo.COMMIT_DATE != "unknown") add("committed ${GeneratedBuildInfo.COMMIT_DATE}")
    add("built ${GeneratedBuildInfo.BUILD_DATE}")
}.joinToString(" | ")
