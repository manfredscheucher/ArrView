package org.bytefred.arrview.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Text forms, files and import detection; the Krupp triple counts. */
class FilesTest {
    private val start = Examples.start.arrangement()
    private val krupp = Arrangement.krupp()

    @Test
    fun sequencesTextHasOneLinePerCircleAndReadsBack() {
        val text = sequencesText(start)
        assertEquals(start.n, text.lines().size)
        assertTrue(text.startsWith("{1: ["))
        assertEquals(start, Arrangement.parse(text))
        // keys right-aligned from n = 10 on
        val big = Examples.byName("n12-great-fueredi-palasti")!!.arrangement(0)
        assertTrue(sequencesText(big).lines()[1].startsWith("  2: ["))
        assertEquals(big, Arrangement.parse(sequencesText(big)))
    }

    @Test
    fun textFormatsRoundTrip() {
        for (f in TextFormat.entries) assertEquals(start.canonical, f.read(f.write(start)).canonical, f.label)
        val connected = Examples.byName("n4-connected-all")!!.let { set -> (0 until set.size).map(set::arrangement).first { !it.isIntersecting } }
        assertFalse(TextFormat.MODEL1.availableFor(connected))
        assertTrue(TextFormat.MODEL2.availableFor(connected))
        assertEquals(connected.canonical, TextFormat.MODEL2.read(TextFormat.MODEL2.write(connected)).canonical)
    }

    @Test
    fun filesReadBackWithTheirName() {
        val json = arrangementFile("my \"best\" one", start, FileFormat.JSON)
        val read = readArrangement(json)
        assertEquals("my \"best\" one", read.name)
        assertEquals(start, read.arrangement)
        val txt = readArrangement(arrangementFile("x", start, FileFormat.TXT))
        assertNull(txt.name)
        assertEquals(start, txt.arrangement)
    }

    @Test
    fun importDetectsTheFormat() {
        assertEquals(krupp, readArrangement(krupp.toString()).arrangement)
        assertEquals(krupp.canonical, readArrangement(Model1.encode(krupp)).arrangement.canonical)
        assertEquals(krupp.canonical, readArrangement(Model2.encode(krupp)).arrangement.canonical)
        assertEquals(krupp, readArrangement("{\"sequences\": [[2, 3, -2, -3], [1, -3, -1, 3], [1, 2, -1, -2]]}").arrangement)
    }

    @Test
    fun badInputIsRejectedWithAReason() {
        val notConsistent = assertFailsWith<InvalidArrangementException> { readArrangement("{1: [2], 2: [1]}") }
        assertTrue(notConsistent.message!!.isNotEmpty())
        assertFailsWith<InvalidArrangementException> { readArrangement("{\"sequences\": [[2, \"x\"]]}") }
        assertFailsWith<InvalidArrangementException> { readArrangement("{\"name\": 1, \"sequences\": []}") }
        assertFailsWith<InvalidArrangementException> { readArrangement(":::") }
        assertFailsWith<InvalidArrangementException> { readArrangement("  ") }
    }

    @Test
    fun kruppTriples() {
        assertEquals(1 to 0, krupp.kruppTriples)
        assertEquals(0 to 1, krupp.triangleFlip(0).kruppTriples)
        // pseudo great circles: every triple is a Krupp
        val great = Examples.byName("n6-great-all")!!.arrangement(0)
        assertEquals(20 to 0, great.kruppTriples)
        // triples that do not cross pairwise are not counted
        val connected = Examples.byName("n3-connected-all")!!.let { set -> (0 until set.size).map(set::arrangement).first { !it.isIntersecting } }
        assertEquals(0 to 0, connected.kruppTriples)
    }
}
