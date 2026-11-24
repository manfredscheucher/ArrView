package org.bytefred.arrview.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Every member of every example set through all text forms, and what the groups and descriptions
 * state about them (ExamplesTest has the sizes, validity, classes and named triangle counts).
 */
class ExampleCatalogTest {
    private fun set(name: String) = assertNotNull(Examples.byName(name), name)

    private fun members(name: String): List<Arrangement> = set(name).let { s -> (0 until s.size).map(s::arrangement) }

    /** For every edge (the arc from crossing `k` to the next one on its circle), the number of triangles on it. */
    private fun trianglesPerEdge(arr: Arrangement): IntArray {
        val count = IntArray(arr.size)
        for (f in arr.faces.filter { it.size == 3 }) for (a in f) count[if (a % 2 == 0) a / 2 else arr.previousOnCircle(a / 2)]++
        return count
    }

    @Test
    fun everyMemberRoundTripsThroughAllTextForms() {
        var count = 0
        for (s in Examples.sets) for (i in 0 until s.size) {
            val arr = s.arrangement(i)
            val what = s.memberName(i)
            // sequences and files keep the arrangement exactly (numbering and orientations)
            assertEquals(arr, Arrangement.parse(arr.toString()), what)
            assertEquals(arr, TextFormat.SEQUENCES.read(TextFormat.SEQUENCES.write(arr)), what)
            val json = readArrangement(arrangementFile(what, arr, FileFormat.JSON))
            assertEquals(what, json.name)
            assertEquals(arr, json.arrangement, what)
            assertEquals(arr, readArrangement(arrangementFile(what, arr, FileFormat.TXT)).arrangement, what)
            // sparse6 numbers the vertices its own way: the same arrangement up to isomorphism
            assertEquals(TextFormat.MODEL1.availableFor(arr), arr.isIntersecting, what)
            val forms = if (arr.isIntersecting) listOf(TextFormat.MODEL1, TextFormat.MODEL2) else listOf(TextFormat.MODEL2)
            for (f in forms) {
                val text = f.write(arr)
                assertTrue(text.startsWith(":"), "$what ${f.label}")
                assertEquals(arr.canonical, f.read(text).canonical, "$what ${f.label}")
                // Import / Open file detect the format (once per set: detection does not depend on the member)
                if (i == 0) assertEquals(arr.canonical, readArrangement(text).arrangement.canonical, "$what ${f.label} detected")
            }
            // a stored model 2 line is not mistaken for model 1 (Import tries model 1 first)
            if (s.text(i).startsWith(":")) assertEquals(arr.canonical, readArrangement(s.text(i)).arrangement.canonical, what)
            count++
        }
        assertEquals(Examples.sets.sumOf { it.size }, count)
    }

    @Test
    fun greatCirclesHaveOnlyKruppTriples() {
        for (s in Examples.sets.filter { it.name.split("-")[1] == "great" }) for (i in 0 until s.size) {
            val arr = s.arrangement(i)
            val n = arr.n
            assertEquals(n * (n - 1) * (n - 2) / 6 to 0, arr.kruppTriples, s.memberName(i))
        }
    }

    @Test
    fun intersectingSetsAlsoHaveMembersWithDigons() {
        for (s in Examples.sets.filter { Regex("n\\d+-intersecting-all").matches(it.name) && it.n >= 3 }) assertTrue(members(s.name).any { it.digonCount > 0 }, s.name)
    }

    @Test
    fun connectedSetsIncludeNonIntersectingMembers() {
        for (s in Examples.sets.filter { Regex("n\\d+-connected-all").matches(it.name) }) {
            val list = members(s.name)
            assertTrue(list.any { !it.isIntersecting && it.nonIntersectingPairCount > 0 }, s.name)
            assertTrue(list.any { it.isIntersecting }, s.name)
        }
    }

    @Test
    fun triangleMinimizersAreMinimalAndComplete() {
        // checkable where all digon-free arrangements are bundled (n = 5, 6; for n = 3, 4 min = max)
        for (n in 5..6) {
            val all = members("n$n-digonfree-all")
            val min = all.minOf { it.triangleCount }
            val set = Examples.inGroup(Examples.group("min-triangles")).single { it.n == n }
            assertEquals("n$n-digonfree-$min-triangles", set.name)
            assertEquals(all.filter { it.triangleCount == min }.map { it.canonical }.toSet(), members(set.name).map { it.canonical }.toSet())
        }
        // "All" up to n = 9, "Some" from n = 10 on; the minimum grows with n
        val sets = Examples.inGroup(Examples.group("min-triangles"))
        for (s in sets) assertTrue(s.description.startsWith(if (s.n <= 9) "All" else "Some"), s.description)
        val minima = sets.map { members(it.name).first().triangleCount }
        assertEquals(minima.sorted(), minima)
    }

    @Test
    fun triangleMaximizersAreMaximal() {
        val digonFree5 = members("n5-digonfree-all")
        assertEquals(12, digonFree5.maxOf { it.triangleCount })
        assertEquals(13, members("n5-intersecting-all").maxOf { it.triangleCount })
        val digonFree6 = members("n6-digonfree-all")
        assertEquals(20, digonFree6.maxOf { it.triangleCount })
        // the website's sets ("All" up to n = 7), then the maxima for n = 8, 9, 10 from the computer search
        val website = Examples.inGroup(Examples.group("max-triangles")).filter { it.page != null }
        for (s in website) assertTrue(s.description.startsWith(if (s.n <= 7) "All" else "Some"), s.description)
        val max8 = members("n8-intersecting-38-triangles").single()
        assertEquals(38, max8.triangleCount)
        assertTrue(max8.digonCount > 0)
        val max9 = members("n9-digonfree-50-triangles").single()
        assertEquals(50, max9.triangleCount)
        assertTrue(max9.isDigonFree)
        val max10 = members("n10-digonfree-61-triangles").single()
        assertEquals(61, max10.triangleCount)
        assertTrue(max10.isDigonFree)
        // the website's n = 8 set: digon-free, below the maximum 38
        assertTrue(website.filter { it.n == 8 }.all { s -> members(s.name).all { it.isDigonFree && it.triangleCount == 37 } })
        assertEquals(website.size + 3, Examples.inGroup(Examples.group("max-triangles")).size)
    }

    @Test
    fun furediPalastiTrianglesTendToTwoThirdsNSquared() {
        val sets = Examples.inGroup(Examples.group("furedi-palasti"))
        assertEquals((6..42 step 6).toList(), sets.map { it.n })
        var last = 0.0
        for (s in sets) {
            val arr = s.arrangement(0)
            assertTrue(arr.isGreat && arr.isDigonFree, s.name)
            // p3 = 2n(n - 3)/3, so p3/n^2 = 2/3 - 2/n grows towards 2/3
            assertEquals(2 * s.n * (s.n - 3) / 3, arr.triangleCount, s.name)
            val ratio = arr.triangleCount.toDouble() / (s.n * s.n)
            assertTrue(ratio > last && ratio < 2.0 / 3, "${s.name}: $ratio")
            last = ratio
        }
    }

    @Test
    fun namedArrangementsAreWhatTheirDescriptionsSay() {
        fun one(name: String) = set(name).arrangement(0)
        // n = 3: one Krupp (8 triangles) and one NonKrupp among the intersecting ones
        val three = set("n3-intersecting-all")
        val triples = (0 until three.size).map { three.arrangement(it).kruppTriples }.toSet()
        assertEquals(setOf(1 to 0, 0 to 1), triples)
        assertEquals(8, Arrangement.krupp().triangleCount)
        // "every triangle is a Krupp": the three circles of every triangle form a Krupp
        for (name in listOf("n6-intersecting-nonr-all-triangles-krupp", "n6-digonfree-nonr-2")) {
            val allKrupp = one(name)
            for (f in allKrupp.faces.indices.filter { allKrupp.faces[it].size == 3 }) {
                val (a, b, c) = allKrupp.faceCircles(f).sorted()
                assertTrue(allKrupp.isKrupp(a, b, c), "$name triangle $f")
            }
        }
    }

    @Test
    fun descriptionsNameTheirSize() {
        for (s in Examples.sets) {
            assertTrue(s.description.endsWith("."), s.name)
            assertTrue(" ${s.n} " in s.description, "${s.name}: ${s.description}")
            Regex("""-(\d+)-triangles$""").find(s.name)?.let { assertTrue("${it.groupValues[1]} triangles" in s.description, s.description) }
        }
    }

    @Test
    fun triangleSaturatedColouringsAreProper() {
        val coloured = Examples.inGroup(Examples.group("colorings")).filter { it.hasColors }
        assertEquals(6, coloured.size)
        for (s in coloured) {
            val arr = s.arrangement(0)
            val colours = assertNotNull(s.colors(0), s.name)
            assertEquals(arr.size, colours.size, s.name)
            assertTrue(arr.isDigonFree, s.name)
            for (k in 0 until arr.size) {
                assertTrue(colours[k] in listOf("R", "G", "B"), s.name)
                // one colour per crossing, neighbours along a circle differ
                assertEquals(colours[k], colours[arr.partners[k]], "${s.name} crossing $k")
                assertTrue(colours[k] != colours[arr.nextOnCircle(k)], "${s.name} crossing $k")
            }
            // triangle-saturated: every edge lies on exactly one triangle
            assertTrue(trianglesPerEdge(arr).all { it == 1 }, s.name)
            assertEquals(s.name.split("-")[1] == "great", arr.isGreat, s.name)
        }
        // the coloured examples are exactly these 6: no other set has colours
        assertEquals(coloured, Examples.sets.filter { it.hasColors })
    }

    @Test
    fun koesterHasSevenCirclesAndOneNonCrossingPair() {
        val koester = set("n7-connected-koester")
        assertEquals(false, koester.hasColors)
        val arr = koester.arrangement(0)
        assertEquals(7, arr.n)
        assertEquals(1, arr.nonIntersectingPairCount)
        assertEquals(40, arr.crossingCount)
        // not triangle-saturated: 5 edges lie on no triangle
        assertEquals(5, trianglesPerEdge(arr).count { it == 0 })
    }

    @Test
    fun groupsAreOrderedByN() {
        // the database lists each class by n; the other groups list all their sets by n
        for (g in Examples.groups) {
            val sets = Examples.inGroup(g)
            val runs = if (g.id == "database") sets.groupBy { it.name.split("-")[1] }.values else listOf(sets)
            for (run in runs) assertEquals(run.map { it.n }.sorted(), run.map { it.n }, g.id)
        }
    }
}
