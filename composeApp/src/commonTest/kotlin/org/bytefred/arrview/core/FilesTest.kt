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

    @Test
    fun jsonFormsAndCrossingColors() {
        // the encoding is one attribute: sequences, mod1s6 or mod2s6; other keys are metadata
        val m1 = readArrangement("{\"name\": \"k\", \"n\": 3, \"label\": \"x\", \"mod1s6\": ${jsonString(Model1.encode(krupp))}}")
        assertEquals("k", m1.name)
        assertEquals(krupp.canonical, m1.arrangement.canonical)
        assertEquals(krupp.canonical, readArrangement("{\"mod2s6\": ${jsonString(Model2.encode(krupp))}}").arrangement.canonical)
        // crossing colours: one word per entry, written and read back with the sequences
        val colors = List(krupp.size) { listOf("R", "G", "B")[it % 3] }
        val json = arrangementFile("c", krupp, FileFormat.JSON, colors)
        assertTrue("\"$CROSSING_COLORS_KEY\": [" in json)
        val read = readArrangement(json)
        assertEquals(krupp, read.arrangement)
        assertEquals(colors, read.colors)
        assertNull(readArrangement(arrangementFile("c", krupp, FileFormat.JSON)).colors)
        // a set object (the example files, Save file of a set), one object per line, a list (older files)
        val set = arrangementsFile("both", listOf(krupp, start), FileFormat.JSON) { if (it == 0) colors else null }
        val file = readArrangementFile(set)
        assertEquals("both", file.name)
        assertEquals(listOf(krupp, start), file.arrangements.map { it.arrangement })
        assertEquals(listOf(colors, null), file.arrangements.map { it.colors })
        val lines = arrangementFile("a", krupp, FileFormat.JSON).replace("\n", "") + "\n" + arrangementFile("b", start, FileFormat.JSON).replace("\n", "")
        assertEquals(listOf("a", "b"), readArrangements(lines).map { it.name })
        val list = "[" + arrangementFile("a", krupp, FileFormat.JSON) + ", " + arrangementFile("b", start, FileFormat.JSON) + "]"
        assertEquals(listOf(krupp, start), readArrangements(list).map { it.arrangement })
        // wrong ones say why
        val bad = mapOf(
            "{\"sequences\": [[2, 3, -2, -3], [1, -3, -1, 3], [1, 2, -1, -2]], \"mod1s6\": \":B\"}" to "not several",
            "{\"name\": \"x\"}" to "expected one of",
            "{\"n\": 4, \"sequences\": [[2, 3, -2, -3], [1, -3, -1, 3], [1, 2, -1, -2]]}" to "\"n\" is 4",
            "{\"sequences\": [[2, 3, -2, -3], [1, -3, -1, 3], [1, 2, -1, -2]], \"crossingColors\": [[\"R\"]]}" to "shape",
            "{\"mod2s6\": ${jsonString(Model2.encode(krupp))}, \"crossingColors\": []}" to "needs",
            "{\"name\": \"s\", \"arrangements\": []}" to "list of objects",
        )
        for ((text, reason) in bad) {
            val e = assertFailsWith<InvalidArrangementException>(text) { readArrangementFile(text) }
            assertTrue(reason in e.message!!, "$text: ${e.message}")
        }
    }
}
