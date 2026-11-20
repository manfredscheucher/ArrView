package org.bytefred.arrview.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The bundled examples against the counts and properties known from the research data. */
/** The group's or set's website page. */
internal val ExampleGroup.page get() = links.firstOrNull { it.title.startsWith("Website: ") }

internal val ExampleGroup.paper get() = links.firstOrNull { it.title.startsWith("Paper: ") }

internal val ExampleSet.page get() = links.firstOrNull { it.title.startsWith("Website: ") }

internal val ExampleSet.paper get() = links.firstOrNull { it.title.startsWith("Paper: ") }

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
        "n9-digonfree-48-triangles" to 1, "n8-intersecting-38-triangles" to 1, "n9-digonfree-50-triangles" to 1,
        "n4-intersecting-digongraphs" to 7, "n5-intersecting-digongraphs" to 27, "n6-intersecting-digongraphs" to 107,
        "n7-intersecting-digongraphs" to 485, "n7-intersecting-trianglefree-digongraphs" to 99,
        "n8-intersecting-nonplanar-digongraph" to 2,
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
        assertTrue(Examples.groups.all { Examples.inGroup(it).isNotEmpty() })
    }

    @Test
    fun everyBundledFileIsInTheCatalogue() {
        val paths = Examples.sets.map { it.path } + Examples.INDEX
        assertEquals(GeneratedExamples.FILES.keys, paths.toSet())
        assertEquals(paths.size, paths.toSet().size)
        // every file is JSON, in the folder of its group, named after its data (no data extension left)
        for (p in paths) assertTrue(p.endsWith(".json") && p.substringBeforeLast(".json").none { it == '.' }, p)
    }

    @Test
    fun theFilesCarryTheirMetadata() {
        for (s in Examples.sets) {
            val o = parseJson(GeneratedExamples.FILES.getValue(s.path).joinToString("")) as Map<*, *>
            // PoCoView's key order: metadata first, then the data
            assertEquals(listOf("name", "n", "group", "label", "description", "links", "source"), o.keys.take(7), s.path)
            assertEquals(s.group.title, o["group"], s.path)
            assertEquals(s.n.toLong(), o["n"], s.path)
            val data = o.keys.drop(7)
            assertTrue(data == listOf("arrangements") || data.first() in JsonEncoding.entries.map { it.key }, "${s.path}: $data")
            assertEquals(s.size > 1, "arrangements" in o, s.path)
        }
    }

    @Test
    fun listSizes() {
        for ((name, size) in expectedSizes) assertEquals(size, set(name).size, name)
        val singles = listOf("furedi-palasti", "non-circularizable", "colorings", "three").map(Examples::group)
        for (s in singles.flatMap(Examples::inGroup)) assertEquals(1, s.size, s.name)
        assertEquals(Examples.sets.size, (singles.flatMap(Examples::inGroup) + Examples.sets.filter { it.name in expectedSizes }).size)
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
        assertEquals(PSEUDOCIRCLES_WEBSITE + "?show=nomorecircles", Examples.group("non-circularizable").page?.url)
        assertEquals(PSEUDOCIRCLES_WEBSITE + "?show=colorings", Examples.group("colorings").page?.url)
        val fromComputations = listOf(
            "n6-intersecting-nonr-edelsbrunner-ramos", "n6-intersecting-nonr-all-triangles-krupp", "n9-great-nonr-pappus",
            "n7-connected-koester",
        )
        for (s in Examples.sets) {
            // a page of the website: its data is from the website mirror (the database also from the computations)
            if (s.page != null) {
                val fromResults = s.name.endsWith("-all") || s.name == "n6-intersecting-nonr-edelsbrunner-ramos"
                assertTrue(s.source!!.startsWith("website/") || fromResults, s.name)
            }
            if (s.name in fromComputations - "n6-intersecting-nonr-edelsbrunner-ramos") assertEquals(null, s.page, s.name)
            if (s.source?.startsWith("computations/") == true && !s.name.endsWith("-all")) assertTrue(s.name in fromComputations, s.name)
        }
    }

    @Test
    fun everyGroupHasADescriptionAndWellFormedLinks() {
        val url = Regex("""https://[a-z0-9.-]+\.[a-z]+/[A-Za-z0-9./?=_-]*""")
        for (g in Examples.groups) {
            assertTrue(g.title.isNotBlank() && g.description.endsWith("."), g.id)
            // one or two short sentences
            assertTrue(g.description.split(". ").size <= 2 && g.description.length <= 110, "${g.id}: ${g.description.length}")
            assertTrue(g.links.isNotEmpty(), g.id)
        }
        for (link in Examples.groups.flatMap { it.links } + Examples.sets.flatMap { it.links }) {
            assertNotNull(url.matchEntire(link.url), link.url)
            assertTrue(link.title.startsWith("Website: ") || link.title.startsWith("Paper: "), link.title)
        }
        assertEquals("https://arxiv.org/abs/2205.08181", Examples.group("colorings").paper?.url)
    }

    @Test
    fun labelsAreShortAndUniqueWithinTheirGroup() {
        for (g in Examples.groups) {
            val labels = Examples.inGroup(g).map { it.label }
            assertEquals(labels.size, labels.toSet().size, g.id)
            for (l in labels) assertTrue(l.length <= 45, l)
        }
        for (s in Examples.sets) assertTrue(s.description.endsWith(".") && s.description.length <= 130, s.name)
    }
}
