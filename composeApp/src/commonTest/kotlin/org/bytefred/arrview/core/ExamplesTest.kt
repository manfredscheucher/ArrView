package org.bytefred.arrview.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The bundled examples against the counts and properties known from the research data. */
/** The set's website page. */
internal val ExampleSet.page get() = links.firstOrNull { it.title.startsWith("Website: ") }

internal val ExampleSet.paper get() = links.firstOrNull { it.title.startsWith("Paper: ") }

class ExamplesTest {
    private val expectedSizes = mapOf(
        "n3-great-all" to 1, "n4-great-all" to 1, "n5-great-all" to 1, "n6-great-all" to 4, "n7-great-all" to 11,
        "n8-great-all" to 135,
        "n3-digonfree-all" to 1, "n4-digonfree-all" to 2, "n5-digonfree-all" to 14, "n6-digonfree-all" to 2131,
        "n2-intersecting-all" to 1, "n3-intersecting-all" to 2, "n4-intersecting-all" to 8, "n5-intersecting-all" to 278,
        "n3-connected-all" to 3, "n4-connected-all" to 21, "n5-connected-all" to 984,
        // Table 1 of On Circularizability: connected digon-free 1, 3, 30
        "n3-connected-digonfree-all" to 1, "n4-connected-digonfree-all" to 3, "n5-connected-digonfree-all" to 30,
        "n5-digonfree-8-triangles" to 1,
        "n6-digonfree-8-triangles" to 1, "n7-digonfree-10-triangles" to 4, "n8-digonfree-11-triangles" to 3,
        "n9-digonfree-12-triangles" to 4, "n10-digonfree-14-triangles" to 317, "n11-digonfree-15-triangles" to 1,
        "n12-digonfree-16-triangles" to 2, "n13-digonfree-18-triangles" to 23, "n14-digonfree-19-triangles" to 1,
        "n5-digonfree-12-triangles" to 6, "n5-intersecting-13-triangles" to 1, "n6-digonfree-20-triangles" to 4,
        "n7-digonfree-29-triangles" to 3, "n8-digonfree-37-triangles" to 7,
        "n8-intersecting-38-triangles" to 1, "n9-digonfree-50-triangles" to 1, "n10-digonfree-61-triangles" to 1,
        // all intersecting arrangements with the maximum 2n - 2 digons
        "n4-intersecting-6-digons" to 1, "n5-intersecting-8-digons" to 1, "n6-intersecting-10-digons" to 13,
        "n7-intersecting-12-digons" to 1743,
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
            // the title of its group or supergroup
            assertTrue(o["group"] == s.group.title || o["group"] == s.group.supergroup.title, s.path)
            assertEquals(s.n.toLong(), o["n"], s.path)
            val data = o.keys.drop(7)
            assertTrue(data == listOf("arrangements") || data.first() in JsonEncoding.entries.map { it.key }, "${s.path}: $data")
            assertEquals(s.size > 1, "arrangements" in o, s.path)
        }
    }

    @Test
    fun listSizes() {
        for ((name, size) in expectedSizes) assertEquals(size, set(name).size, name)
        val singles = listOf("furedi-palasti", "colorings").map(Examples::group) +
            Examples.groupsIn(Examples.supergroup("non-circularizable"))
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
                Regex("""-(\d+)-digons$""").find(s.name)?.let {
                    assertEquals(it.groupValues[1].toInt(), arr.digonCount, what)
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
            assertEquals(connected.filter { it.isDigonFree }.map { it.canonical }.toSet(), canonicalSet("n$n-connected-digonfree-all"))
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
        // n = 3: the Krupp and the NonKrupp (its triangle flip) are the intersecting ones, the 3-Chain
        // (one pair does not cross) is the third connected one
        val krupp = Arrangement.krupp()
        assertEquals(canonicalSet("n3-intersecting-all"), setOf(krupp.canonical, krupp.triangleFlip(0).canonical))
        val connected3 = set("n3-connected-all")
        val chains = (0 until connected3.size).map(connected3::arrangement).filter { it.nonIntersectingPairCount == 1 }
        assertEquals(1, chains.size)
        assertEquals(canonicalSet("n3-connected-all"), canonicalSet("n3-intersecting-all") + chains.single().canonical)
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
        assertEquals("n10-digonfree-61-triangles", set("n10-digonfree-61-triangles").memberName(0))
    }

    @Test
    fun websitePagesAndPapers() {
        for (s in Examples.inGroup(Examples.group("nonr-5"))) assertEquals(PSEUDOCIRCLES_WEBSITE + "?show=nomorecircles", s.page?.url)
        for (s in Examples.groupsIn(Examples.supergroup("database")).flatMap { Examples.inGroup(it) }) {
            assertEquals(PSEUDOCIRCLES_WEBSITE + "?show=pseudocircles", s.page?.url, s.name)
            assertEquals("https://doi.org/10.1007/s00454-019-00077-y", s.paper?.url, s.name)
        }
        assertEquals(PSEUDOCIRCLES_WEBSITE + "?show=colorings", set("n6-digonfree-triangle-saturated-2").page?.url)
        // the maxima of triangles for n = 8, 9, 10 are new (computer search), not on the website
        for (name in listOf("n8-intersecting-38-triangles", "n9-digonfree-50-triangles", "n10-digonfree-61-triangles")) {
            assertNull(set(name).page, name)
        }
        // the constructions carry the paper they are from
        for (s in Examples.inGroup(Examples.group("furedi-palasti"))) {
            assertTrue(s.links.any { it.url == "https://doi.org/10.1090/S0002-9939-1984-0760946-2" }, s.name)
        }
        for (s in Examples.inGroup(Examples.group("digon-graphs"))) {
            assertTrue(s.links.any { it.url == "https://arxiv.org/abs/2208.12110" }, s.name)
        }
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
    fun descriptionsAreShortAndEverySetHasLinks() {
        val url = Regex("""https://[a-z0-9.-]+\.[a-z]+/[A-Za-z0-9./?=_-]*""")
        // descriptions are optional; one or two short sentences
        for ((id, title, description) in Examples.supergroups.map { Triple(it.id, it.title, it.description) } +
            Examples.groups.map { Triple(it.toString(), it.title, it.description) }) {
            assertTrue(title.isNotBlank(), id)
            if (description == null) continue
            assertTrue(description.endsWith("."), id)
            assertTrue(description.split(". ").size <= 2 && description.length <= 110, "$id: ${description.length}")
        }
        // links belong to the sets: each has at least one, none twice
        for (s in Examples.sets) {
            assertTrue(s.links.isNotEmpty(), s.name)
            assertEquals(s.links.size, s.links.map { it.url }.toSet().size, s.name)
        }
        for (link in Examples.sets.flatMap { it.links }) {
            assertNotNull(url.matchEntire(link.url), link.url)
            assertTrue(link.title.startsWith("Website: ") || link.title.startsWith("Paper: "), link.title)
        }
        assertEquals("https://arxiv.org/abs/2205.08181", set("n6-digonfree-triangle-saturated-2").paper?.url)
    }

    @Test
    fun supergroupsAndGroupsHaveNoLinks() {
        // the index has none, and the loader refuses them
        val index = parseJson(GeneratedExamples.FILES.getValue(Examples.INDEX).joinToString("")) as Map<*, *>
        for (sg in index["supergroups"] as List<*>) {
            assertFalse("links" in (sg as Map<*, *>), sg["id"].toString())
            for (g in sg["groups"] as List<*>) assertFalse("links" in (g as Map<*, *>), g["id"].toString())
        }
        val e = assertFailsWith<IllegalArgumentException> {
            Examples.checkNoLinks(mapOf("id" to "x", "links" to emptyList<Any>()))
        }
        assertEquals("index.json: 'x' has links; links belong to the sets", e.message)
        Examples.checkNoLinks(mapOf("id" to "x"))
    }

    @Test
    fun theTreeOfSupergroupsAndGroups() {
        val tree = Examples.supergroups.map { sg -> sg.id to Examples.groupsIn(sg).map { it.id } }
        assertEquals(
            listOf(
                "database" to listOf("great", "intersecting-digonfree", "intersecting", "connected-digonfree", "connected"),
                "combinatorial" to listOf("min-triangles", "max-triangles", "max-digons", "digon-graphs"),
                "non-circularizable" to listOf("nonr-5", "nonr-6-digonfree", "nonr-6-intersecting", "nonr-6-connected", "nonr-9-great"),
                "constructions" to listOf("furedi-palasti", "colorings"),
            ),
            tree,
        )
        assertEquals(Examples.groups, Examples.supergroups.flatMap(Examples::groupsIn))
        // every group has sets; a database group holds one class (the second part of the name)
        assertTrue(Examples.groups.all { Examples.inGroup(it).isNotEmpty() })
        val classOf = mapOf(
            "great" to "great-all", "intersecting-digonfree" to "digonfree-all", "intersecting" to "intersecting-all",
            "connected-digonfree" to "connected-digonfree-all", "connected" to "connected-all",
        )
        for ((id, suffix) in classOf) {
            val sets = Examples.inGroup(Examples.group(id))
            assertTrue(sets.all { Regex("n\\d+-$suffix").matches(it.name) }, id)
        }
        // the non-circularizable ones by n and class, the order of the paper
        fun names(id: String) = Examples.inGroup(Examples.group(id)).map { it.name }
        assertEquals((1..4).map { if (it == 1) "n5-intersecting-nonr-1" else "n5-connected-nonr-$it" }, names("nonr-5"))
        assertEquals(listOf("n6-digonfree-nonr-8-triangles", "n6-digonfree-nonr-2", "n6-digonfree-nonr-3"), names("nonr-6-digonfree"))
        assertEquals(
            listOf("n6-intersecting-nonr-all-triangles-krupp", "n6-intersecting-nonr-edelsbrunner-ramos", "n6-intersecting-nonr-sym6-2", "n6-intersecting-nonr-sym6-3"),
            names("nonr-6-intersecting"),
        )
        assertEquals(
            listOf("sym. 8, no. 1", "sym. 8, no. 2", "sym. 4, no. 1", "sym. 4, no. 2"),
            Examples.inGroup(Examples.group("nonr-6-connected")).drop(1).map { it.label.substringAfter("digon-free, ") },
        )
        assertEquals("n6-connected-nonr-sym24", names("nonr-6-connected").first())
        assertEquals(listOf("n9-great-nonr-pappus"), names("nonr-9-great"))
        for (s in Examples.groupsIn(Examples.supergroup("non-circularizable")).flatMap(Examples::inGroup)) {
            val arr = s.arrangement(0)
            when (s.group.id) {
                "nonr-6-digonfree" -> assertTrue(arr.isIntersecting && arr.isDigonFree, s.name)
                "nonr-6-intersecting" -> assertTrue(arr.isIntersecting && arr.digonCount > 0, s.name)
                "nonr-6-connected" -> assertTrue(arr.isDigonFree, s.name)
                "nonr-9-great" -> assertTrue(arr.isGreat, s.name)
            }
        }
    }

    @Test
    fun groupIdsAreUniqueAmongAllGroups() {
        assertEquals(Examples.groups.size, Examples.groups.map { it.id }.toSet().size)
        Examples.checkGroupIds(Examples.groups)
        // the same id in two supergroups is refused, not only within one
        val a = ExampleSupergroup("a", "A", null)
        val b = ExampleSupergroup("b", "B", null)
        val e = assertFailsWith<IllegalStateException> {
            Examples.checkGroupIds(listOf(ExampleGroup(a, "x", "X", null), ExampleGroup(b, "x", "X", null)))
        }
        assertEquals("index.json: group id 'x' twice (in 'a', 'b')", e.message)
    }

    @Test
    fun labelsAreShortAndUniqueWithinTheirGroup() {
        for (g in Examples.groups) {
            val labels = Examples.inGroup(g).map { it.label }
            assertEquals(labels.size, labels.toSet().size, g.toString())
            for (l in labels) assertTrue(l.length <= 45, l)
        }
        for (s in Examples.sets) assertTrue(s.description.endsWith(".") && s.description.length <= 130, s.name)
    }
}
