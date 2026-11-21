package org.bytefred.arrview.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Desktop save: an existing file is replaced only after "Replace it?" says yes; no shows the dialog again. */
class SaveFileTest {
    private val existing = File("a.json")
    private val fresh = File("b.json")

    /** Runs [chooseSaveFile] with the dialog returning [choices] in turn and the answers [answers]. */
    private fun run(choices: List<File?>, answers: List<Boolean>): Triple<File?, Int, List<String>> {
        val c = choices.iterator()
        val a = answers.iterator()
        var shown = 0
        val asked = mutableListOf<String>()
        val f = chooseSaveFile(
            choose = { shown++; c.next() },
            exists = { it == existing },
            confirmReplace = { asked += replaceQuestion(it.name); a.next() },
        )
        return Triple(f, shown, asked)
    }

    @Test
    fun aNewFileIsSavedWithoutAsking() = assertEquals(Triple(fresh, 1, emptyList()), run(listOf(fresh), emptyList()))

    @Test
    fun yesReplacesTheFile() =
        assertEquals(Triple(existing, 1, listOf("a.json already exists. Replace it?")), run(listOf(existing), listOf(true)))

    @Test
    fun noShowsTheDialogAgain() {
        val (f, shown, asked) = run(listOf(existing, fresh), listOf(false))
        assertEquals(fresh, f)
        assertEquals(2, shown)
        assertEquals(1, asked.size)
    }

    @Test
    fun cancelSavesNothing() {
        assertNull(run(listOf(null), emptyList()).first)
        assertNull(run(listOf(existing, null), listOf(false)).first)
    }
}
