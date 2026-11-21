package org.bytefred.arrview.ui

import androidx.compose.runtime.Composable
import org.bytefred.arrview.core.LOAD_EXTENSIONS
import java.io.File
import java.io.IOException
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import javax.swing.filechooser.FileNameExtensionFilter

private var lastDir: File? = null

@Composable
actual fun rememberFileOpener(
    onError: (message: String) -> Unit,
    onOpened: (name: String, bytes: ByteArray) -> Unit,
): () -> Unit = {
    val chooser = JFileChooser(lastDir).apply {
        dialogTitle = "Open arrangement"
        fileFilter = FileNameExtensionFilter(
            "Arrangements (${LOAD_EXTENSIONS.joinToString(", ") { "*.$it" }})",
            *LOAD_EXTENSIONS.toTypedArray(),
        )
    }
    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        val f = chooser.selectedFile
        lastDir = f.parentFile
        readFile(f, onError, onOpened)
    }
}

@Composable
actual fun rememberFileSaver(
    onError: (message: String) -> Unit,
    onSaved: (name: String?) -> Unit,
): (fileName: String, bytes: ByteArray) -> Unit = { fileName, bytes ->
    val chooser = JFileChooser(lastDir).apply {
        dialogTitle = "Save"
        selectedFile = File(lastDir, fileName)
    }
    val ext = fileName.substringAfterLast('.', "")
    val f = chooseSaveFile(
        choose = {
            if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) null
            else chooser.selectedFile.let { if (ext.isNotEmpty() && !it.name.contains('.')) File(it.path + "." + ext) else it }
        },
        exists = File::exists,
        confirmReplace = { file ->
            JOptionPane.showConfirmDialog(null, replaceQuestion(file.name), "Save", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION
        },
    )
    if (f != null) {
        lastDir = f.parentFile
        writeFile(f, bytes, onError, onSaved)
    } else {
        onSaved(null)
    }
}

/** The question before a save replaces the existing file [name]. */
internal fun replaceQuestion(name: String) = "$name already exists. Replace it?"

/**
 * The file to save to (Save file and Export SVG): [choose] shows the save dialog (null: cancelled);
 * an existing file is only taken if [confirmReplace] says yes, else the dialog shows again.
 */
internal fun chooseSaveFile(choose: () -> File?, exists: (File) -> Boolean, confirmReplace: (File) -> Boolean): File? {
    while (true) {
        val f = choose() ?: return null
        if (!exists(f) || confirmReplace(f)) return f
    }
}

/** Reads [f] for [onOpened]; a read error goes to [onError]. */
internal fun readFile(f: File, onError: (String) -> Unit, onOpened: (name: String, bytes: ByteArray) -> Unit) {
    val bytes = try {
        f.readBytes()
    } catch (e: IOException) {
        onError("Cannot open ${f.name}: ${e.message ?: "read error"}")
        return
    }
    onOpened(f.name, bytes)
}

/** Writes [bytes] to [f] and reports its name, or a write error to [onError] and null (not saved). */
internal fun writeFile(f: File, bytes: ByteArray, onError: (String) -> Unit, onSaved: (name: String?) -> Unit) {
    try {
        f.writeBytes(bytes)
    } catch (e: IOException) {
        onError("Cannot save ${f.name}: ${e.message ?: "write error"}")
        onSaved(null)
        return
    }
    onSaved(f.name)
}
