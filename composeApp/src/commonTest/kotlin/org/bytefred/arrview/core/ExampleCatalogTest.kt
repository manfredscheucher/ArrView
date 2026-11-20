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
        // checkable where all digon-free arrangements are bundled (n = 3..6)
        for (n in 3..6) {
            val all = members("n$n-digonfree-all")
            val min = all.minOf { it.triangleCount }
            val set = Examples.inGroup(ExampleGroup.MIN_TRIANGLES).single { it.n == n }
            assertEquals("n$n-digonfree-$min-triangles", set.name)
            assertEquals(all.filter { it.triangleCount == min }.map { it.canonical }.toSet(), members(set.name).map { it.canonical }.toSet())
        }
        // "All" up to n = 9, "Some" from n = 10 on; the minimum grows with n
        val sets = Examples.inGroup(ExampleGroup.MIN_TRIANGLES)
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
        // the website's sets ("All" up to n = 7), then the maxima for n = 8, 9 from the computer search
        val website = Examples.inGroup(ExampleGroup.MAX_TRIANGLES).filter { it.page != null }
        for (s in website) assertTrue(s.description.startsWith(if (s.n <= 7) "All" else "Some"), s.description)
        val max8 = members("n8-intersecting-38-triangles").single()
        assertEquals(38, max8.triangleCount)
        assertTrue(max8.digonCount > 0)
        val max9 = members("n9-digonfree-50-triangles").single()
        assertEquals(50, max9.triangleCount)
        assertTrue(max9.isDigonFree)
        // the website's n = 8, 9 sets stay below the maxima
        assertTrue(website.filter { it.n == 8 }.all { s -> members(s.name).all { it.triangleCount < 38 } })
        assertTrue(website.filter { it.n == 9 }.all { s -> members(s.name).all { it.triangleCount < 50 } })
        assertEquals(website.size + 2, Examples.inGroup(ExampleGroup.MAX_TRIANGLES).size)
    }

    @Test
    fun furediPalastiTrianglesTendToTwoThirdsNSquared() {
        val sets = Examples.inGroup(ExampleGroup.FUREDI_PALASTI)
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
        val krupp = one("n3-great-krupp")
        assertEquals(1 to 0, krupp.kruppTriples)
        assertEquals(8, krupp.triangleCount)
        assertEquals(0 to 1, one("n3-intersecting-nonkrupp").kruppTriples)
        // the 3-Chain: circle 2 crosses circles 1 and 3, which do not cross
        val chain = one("n3-connected-chain")
        assertEquals(listOf(0 to 1, 1 to 2), chain.intersectionGraph())
        // "all triangles Krupp": the three circles of every triangle form a Krupp
        val allKrupp = one("n6-intersecting-nonr-all-triangles-krupp")
        for (f in allKrupp.faces.indices.filter { allKrupp.faces[it].size == 3 }) {
            val (a, b, c) = allKrupp.faceCircles(f).sorted()
            assertTrue(allKrupp.isKrupp(a, b, c), "triangle $f")
        }
    }

    @Test
    fun descriptionsNameTheirSize() {
        for (s in Examples.sets) {
            assertTrue(s.description.endsWith("."), s.name)
            if (s.group != ExampleGroup.THREE) assertTrue(" ${s.n} " in s.description, "${s.name}: ${s.description}")
            Regex("""-(\d+)-triangles$""").find(s.name)?.let { assertTrue("${it.groupValues[1]} triangles" in s.description, s.description) }
        }
    }

    @Test
    fun triangleSaturatedColouringsAreProper() {
        val coloured = Examples.inGroup(ExampleGroup.COLORINGS).filter { it.hasColours }
        assertEquals(6, coloured.size)
        for (s in coloured) {
            val arr = s.arrangement(0)
            val colours = assertNotNull(s.colours(0), s.name)
            assertEquals(arr.size, colours.size, s.name)
            assertTrue(arr.isDigonFree, s.name)
            for (k in 0 until arr.size) {
                assertTrue(colours[k] in 0..2, s.name)
                // one colour per crossing, neighbours along a circle differ
                assertEquals(colours[k], colours[arr.partners[k]], "${s.name} crossing $k")
                assertTrue(colours[k] != colours[arr.nextOnCircle(k)], "${s.name} crossing $k")
            }
            // triangle-saturated: every crossing is on a triangle
            val onTriangle = BooleanArray(arr.size)
            for (f in arr.faces.filter { it.size == 3 }) for (a in f) {
                onTriangle[a / 2] = true
                onTriangle[arr.partners[a / 2]] = true
            }
            assertTrue(onTriangle.all { it }, s.name)
            assertEquals(s.name.split("-")[1] == "great", arr.isGreat, s.name)
            assertTrue(Examples.crossingColours(arr).contentEquals(colours), s.name)
        }
        // only the stored arrangements: an edited one has no colours
        val first = coloured.first().arrangement(0)
        assertEquals(null, Examples.crossingColours(first.triangleFlip(first.faces.indexOfFirst { it.size == 3 })))
        assertEquals(null, Examples.crossingColours(Examples.start.arrangement()))
    }

    @Test
    fun koesterHasSevenCirclesAndOneNonCrossingPair() {
        val koester = set("n7-connected-koester")
        assertEquals(false, koester.hasColours)
        val arr = koester.arrangement(0)
        assertEquals(7, arr.n)
        assertEquals(1, arr.nonIntersectingPairCount)
        assertEquals(40, arr.crossingCount)
    }
}
