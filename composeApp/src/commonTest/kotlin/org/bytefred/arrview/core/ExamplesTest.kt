package org.bytefred.arrview.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The bundled examples against the counts and properties known from the research data. */
class ExamplesTest {
    private val expectedSizes = mapOf(
        "n3-great-all" to 1, "n4-great-all" to 1, "n5-great-all" to 1, "n6-great-all" to 4, "n7-great-all" to 11,
        "n8-great-all" to 135,
        "n3-digonfree-all" to 1, "n4-digonfree-all" to 2, "n5-digonfree-all" to 14, "n6-digonfree-all" to 2131,
        "n2-intersecting-all" to 1, "n3-intersecting-all" to 2, "n4-intersecting-all" to 8, "n5-intersecting-all" to 278,
        "n3-connected-all" to 3, "n4-connected-all" to 21, "n5-connected-all" to 984,
        "n3-digonfree-8-triangles" to 1, "n4-digonfree-8-triangles" to 2, "n5-digonfree-8-triangles" to 1,
        "n6-digonfree-8-triangles" to 1, "n7-digonfree-10-triangles" to 4, "n8-digonfree-11-triangles" to 3,
        "n9-digonfree-12-triangles" to 4, "n10-digonfree-14-triangles" to 317, "n11-digonfree-15-triangles" to 1,
        "n12-digonfree-16-triangles" to 2, "n13-digonfree-18-triangles" to 23, "n14-digonfree-19-triangles" to 1,
        "n5-digonfree-12-triangles" to 6, "n5-intersecting-13-triangles" to 1, "n6-digonfree-20-triangles" to 4,
        "n7-digonfree-28-triangles" to 19, "n7-digonfree-29-triangles" to 3, "n8-digonfree-37-triangles" to 7,
        "n9-digonfree-48-triangles" to 1,
    )

    private fun set(name: String) = assertNotNull(Examples.byName(name), name)

    private fun canonicalSet(name: String): Set<Arrangement> =
        set(name).let { s -> (0 until s.size).map { s.arrangement(it).canonical }.toSet() }

    @Test
    fun namesAreUniqueAndFollowTheScheme() {
        val names = Examples.sets.map { it.name }
        assertEquals(names.size, names.toSet().size)
        val scheme = Regex("""n(\d+)-(great|digonfree|intersecting|connected)-[a-z0-9-]+""")
        for (s in Examples.sets) {
            val match = assertNotNull(scheme.matchEntire(s.name), s.name)
            assertEquals(s.n, match.groupValues[1].toInt(), s.name)
        }
        assertTrue(ExampleGroup.entries.all { Examples.inGroup(it).isNotEmpty() })
    }

    @Test
    fun everyBundledFileIsInTheCatalogue() {
        val paths = Examples.sets.mapNotNull { (it.data as? ExampleSet.Data.File)?.path }
        assertEquals(GeneratedExamples.FILES.keys, paths.toSet())
        assertEquals(paths.size, paths.toSet().size)
    }

    @Test
    fun listSizes() {
        for ((name, size) in expectedSizes) assertEquals(size, set(name).size, name)
        for (s in Examples.inGroup(ExampleGroup.FUREDI_PALASTI) + Examples.inGroup(ExampleGroup.SINGLETONS)) {
            assertEquals(1, s.size, s.name)
        }
    }

    @Test
    fun membersAreValidDistinctAndOfTheirClass() {
        for (s in Examples.sets) {
            val canonical = mutableSetOf<Arrangement>()
            for (i in 0 until s.size) {
                val arr = s.arrangement(i)
                val what = s.memberName(i)
                assertEquals(s.n, arr.n, what)
                assertNull(LinhartOrtner.check(arr.lists), what)
                assertTrue(canonical.add(arr.canonical), "$what occurs twice")
                when (s.name.split("-")[1]) {
                    "great" -> assertTrue(arr.isGreat, "$what is not great")
                    "digonfree" -> assertTrue(arr.isIntersecting && arr.isDigonFree, "$what is not digon-free intersecting")
                    "intersecting" -> assertTrue(arr.isIntersecting, "$what is not intersecting")
                }
                Regex("""-(\d+)-triangles$""").find(s.name)?.let {
                    assertEquals(it.groupValues[1].toInt(), arr.triangleCount, what)
                }
            }
        }
    }

    @Test
    fun subclassesAgree() {
        for (n in 3..5) {
            val intersecting = (0 until set("n$n-intersecting-all").size).map { set("n$n-intersecting-all").arrangement(it) }
            assertEquals(intersecting.filter { it.isDigonFree }.map { it.canonical }.toSet(), canonicalSet("n$n-digonfree-all"))
            assertEquals(intersecting.filter { it.isGreat }.map { it.canonical }.toSet(), canonicalSet("n$n-great-all"))
            val connected = set("n$n-connected-all").let { s -> (0 until s.size).map(s::arrangement) }
            assertEquals(intersecting.map { it.canonical }.toSet(), connected.filter { it.isIntersecting }.map { it.canonical }.toSet())
        }
        val digonFree6 = set("n6-digonfree-all").let { s -> (0 until s.size).map(s::arrangement) }
        assertEquals(canonicalSet("n6-great-all"), digonFree6.filter { it.isGreat }.map { it.canonical }.toSet())
        assertEquals(canonicalSet("n6-digonfree-8-triangles"), digonFree6.filter { it.triangleCount == 8 }.map { it.canonical }.toSet())
        assertEquals(canonicalSet("n6-digonfree-20-triangles"), digonFree6.filter { it.triangleCount == 20 }.map { it.canonical }.toSet())
        assertEquals(1, (0 until 4).count { set("n6-digonfree-20-triangles").arrangement(it).isGreat })
        val intersecting5 = set("n5-intersecting-all").let { s -> (0 until s.size).map(s::arrangement) }
        assertEquals(canonicalSet("n5-intersecting-13-triangles"), intersecting5.filter { it.triangleCount == 13 }.map { it.canonical }.toSet())
        assertEquals(canonicalSet("n5-digonfree-12-triangles"), intersecting5.filter { it.isDigonFree && it.triangleCount == 12 }.map { it.canonical }.toSet())
    }

    @Test
    fun namedExamples() {
        val krupp = set("n3-great-krupp").arrangement(0)
        val nonKrupp = set("n3-intersecting-nonkrupp").arrangement(0)
        assertEquals(canonicalSet("n3-intersecting-all"), setOf(krupp.canonical, nonKrupp.canonical))
        assertEquals(nonKrupp.canonical, krupp.triangleFlip(0).canonical)
        val chain = set("n3-connected-chain").arrangement(0)
        assertEquals(1, chain.nonIntersectingPairCount)
        assertTrue(chain.canonical in canonicalSet("n3-connected-all"))
        assertTrue(set("n5-intersecting-nonr-1").arrangement(0).isIntersecting)
        for (k in 2..4) assertTrue(!set("n5-connected-nonr-$k").arrangement(0).isIntersecting)
    }

    @Test
    fun startExampleIsTheDigonFreeSixWithEightTriangles() {
        val start = Examples.start
        assertEquals("n6-digonfree-nonr-8-triangles", start.name)
        val arr = start.arrangement()
        assertEquals(8, arr.triangleCount)
        assertEquals(canonicalSet("n6-digonfree-8-triangles"), setOf(arr.canonical))
    }

    @Test
    fun memberNamesAndPositions() {
        val s = set("n5-intersecting-all")
        assertEquals("n5-intersecting-all #17", s.memberName(16))
        assertEquals("#17 of 278", s.position(16))
        assertEquals("n3-great-krupp", set("n3-great-krupp").memberName(0))
    }

    @Test
    fun websitePagesAndPapers() {
        assertEquals("https://www3.math.tu-berlin.de/diskremath/pseudocircles/?show=nomorecircles", WebPage.NO_MORE_CIRCLES.url)
        for (s in Examples.sets) {
            // a page of the website: its data is from the website mirror
            if (s.page != null) assertTrue(s.source!!.startsWith("website/") || s.group in listOf(ExampleGroup.INTERSECTING, ExampleGroup.DIGON_FREE), s.name)
            if (s.group == ExampleGroup.SINGLETONS && s.page == null) assertEquals(null, s.paper, s.name)
        }
        assertTrue(Examples.inGroup(ExampleGroup.SINGLETONS).all { it.size == 1 })
    }
}
