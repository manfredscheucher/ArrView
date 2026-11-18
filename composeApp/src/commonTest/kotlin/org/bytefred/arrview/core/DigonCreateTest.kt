package org.bytefred.arrview.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.TimeSource

/** Digon create, its inverse digon collapse, and the actions per face / pair / triple. */
class DigonCreateTest {
    private val chain = Arrangement.parse("{1: [2, -2], 2: [1, -1, 3, -3], 3: [2, -2]}")

    /** All members of the small sets, samples of the big ones (with a non-crossing pair or a digon). */
    private val samples: List<Arrangement> by lazy {
        Examples.sets.flatMap { set ->
            val all = set.n <= 4 || set.size <= 30 || set.name == "n5-connected-all"
            val idx = if (all) (0 until set.size) else (0 until set.size step set.size / 25)
            if (set.n > 14) emptyList() else idx.map(set::arrangement)
        }
    }

    /** Checks one create: valid, two crossings more, a new digon of the pair, collapse gives it back. */
    private fun checkCreate(arr: Arrangement, op: DigonCreate) {
        val created = arr.digonCreate(op)
        // Arrangement.of checked Linhart-Ortner; check again explicitly
        assertEquals(null, LinhartOrtner.check(created.lists), "create $op of $arr")
        assertEquals(arr.crossingCount + 2, created.crossingCount)
        assertEquals(arr.faceCount + 2, created.faceCount)
        assertTrue(created.crosses(op.i, op.j))
        // p2 grows by 1, minus the faces across the two sides that were digons and now have more sides
        val across = setOf(arr.faceOfDart[arr.alpha(op.dartI)], arr.faceOfDart[arr.alpha(op.dartJ)])
        val lost = across.count { arr.faces[it].size == 2 }
        assertEquals(arr.digonCount + 1 - lost, created.digonCount, "p2 after create $op of $arr")
        // the new digon: collapsing it gives exactly the original sequences back
        val digons = created.faces.indices.filter { f ->
            created.faces[f].size == 2 && created.faceCircles(f).toSet() == setOf(op.i, op.j)
        }
        assertEquals(1, digons.size, "the pair has one digon after the create")
        assertEquals(arr, created.digonCollapse(digons.single()), "collapse after create $op of $arr")
        // the faces at the two corners of the new digon are the two parts of op.face: together
        // they have its sides, plus one more side of i and of j (the split sides)
        val corners = cornerFaces(created, digons.single())
        assertEquals(2, corners.size, "two faces at the corners of the new digon")
        assertEquals(
            (arr.faceCircles(op.face) + op.i + op.j).sorted(),
            corners.flatMap { created.faceCircles(it) }.sorted(),
            "the new digon lies in face ${op.face}: create $op of $arr",
        )
    }

    /** The faces at the two corners of digon [d], opposite to it (not across its sides). */
    private fun cornerFaces(arr: Arrangement, d: Int): Set<Int> {
        val across = arr.faces[d].map { arr.faceOfDart[arr.alpha(it)] }.toSet()
        return arr.faces[d].flatMap { a ->
            val k = a / 2
            val p = arr.partners[k]
            listOf(2 * k, 2 * k + 1, 2 * p, 2 * p + 1).map { arr.faceOfDart[it] }
        }.toSet() - across - d
    }

    @Test
    fun createThenCollapseOnAllSamples() {
        var count = 0
        for (arr in samples) for (op in arr.digonCreates()) {
            checkCreate(arr, op)
            count++
        }
        assertTrue(count > 1000, "only $count creates checked")
    }

    @Test
    fun collapseThenCreateGivesTheOriginal() {
        var count = 0
        for (arr in samples) for (f in arr.faces.indices.filter { arr.faces[it].size == 2 }) {
            val collapsed = arr.digonCollapse(f) ?: continue
            val (c, d) = arr.faceCircles(f).sorted()
            // the digon and the faces at its corners merge into one face: its sides are theirs minus
            // one side of c and one of d; a create through that face gives arr back exactly
            val corners = cornerFaces(arr, f)
            assertEquals(2, corners.size)
            val merged = corners.flatMap { arr.faceCircles(it) }.toMutableList().apply { remove(c); remove(d) }.sorted()
            val options = collapsed.digonCreates().filter { it.i == c && it.j == d && collapsed.faceCircles(it.face).sorted() == merged }
            assertTrue(options.isNotEmpty(), "no create through the merged face after collapse of $f in $arr")
            assertTrue(options.any { sameUpToRotation(collapsed.digonCreate(it), arr) }, "exact inverse of collapse $f in $arr")
            count++
        }
        assertTrue(count > 500, "only $count collapses checked")
    }

    private fun sameUpToRotation(a: Arrangement, b: Arrangement): Boolean =
        a.n == b.n && (0 until a.n).all { c ->
            val x = a.list(c)
            val y = b.list(c)
            x.size == y.size && (x.isEmpty() || y.indices.any { r -> x.indices.all { t -> x[t] == y[(t + r) % y.size] } })
        }

    @Test
    fun chainOptions() {
        // circles 1 and 3 of the chain do not cross; they share the two faces outside circle 2
        // that touch both (each with one side of 1 and one of 3)
        val creates = chain.digonCreates()
        assertTrue(creates.isNotEmpty())
        assertTrue(creates.all { it.i == 0 && it.j == 2 })
        for (op in creates) {
            val created = chain.digonCreate(op)
            assertTrue(created.isIntersecting)
            checkCreate(chain, op)
        }
        // every result is one of the two intersecting arrangements of three circles (Krupp or NonKrupp)
        val three = Examples.byName("n3-intersecting-all")!!.let { s -> (0 until s.size).map { s.arrangement(it).canonical } }
        for (op in creates) assertTrue(chain.digonCreate(op).canonical in three)
    }

    @Test
    fun noCreatesInIntersectingArrangements() {
        for (arr in listOf(Arrangement.two(), Arrangement.krupp(), Examples.start.arrangement())) {
            assertTrue(arr.digonCreates().isEmpty())
        }
    }

    @Test
    fun invalidCreatesAreRejected() {
        val krupp = Arrangement.krupp()
        val face = krupp.faces[0]
        assertFailsWith<IllegalArgumentException> {
            krupp.digonCreate(DigonCreate(0, krupp.circleOfDart(face[0]), krupp.circleOfDart(face[1]), face[0], face[1]))
        }
        val op = chain.digonCreates().first()
        assertFailsWith<IllegalArgumentException> { chain.digonCreate(op.copy(face = (op.face + 1) % chain.faceCount)) }
    }

    @Test
    fun cellDescriptions() {
        val krupp = Arrangement.krupp()
        for (f in krupp.faces.indices) {
            val d = krupp.cellDescription(f)
            assertTrue(d == "1 2 3" || d == "1 3 2", d)
        }
        // the chain: the face outside all three circles (and the one inside 2 only) has sides 1, 2, 3, 2
        val outside = chain.faces.indices.first { chain.faceCircles(it).sorted() == listOf(0, 1, 1, 2) }
        val d = chain.cellDescription(outside)
        assertTrue(d == "1 2 3 2", d)
        val marked = chain.faces[outside].filter { chain.circleOfDart(it) != 1 }.toSet()
        assertEquals("1* 2 3* 2", chain.cellDescription(outside, marked))
    }

    @Test
    fun actionsPerFacePairAndTriple() {
        for (arr in samples.take(400)) {
            val actions = CellActions(arr)
            for (f in arr.faces.indices) {
                val size = arr.faces[f].size
                val expected = size == 3 || (size == 2 && arr.digonCollapse(f) != null) || arr.digonCreatesIn(f).isNotEmpty()
                assertEquals(expected, actions.hasAction(f))
            }
            for (i in 0 until arr.n) for (j in i + 1 until arr.n) {
                // two digons of a pair use three or four of its four arcs, so one of the two circles
                // crosses no other circle and a collapse would cut it off: never two collapses
                assertTrue(actions.collapsesOf(i, j).size <= 1)
                if (arr.crosses(i, j)) assertTrue(actions.createsOf(i, j).isEmpty())
                else assertTrue(actions.collapsesOf(i, j).isEmpty())
            }
            assertEquals(arr.triangleCount, actions.triples.values.sumOf { it.size })
            assertEquals(arr.digonCreates().size, actions.creates.size)
        }
        // two circles: both digons would make them disjoint, so nothing to collapse
        val two = CellActions(Arrangement.two())
        assertTrue(two.collapsible.isEmpty())
        assertEquals(0, two.pairActionCount(0, 1))
        assertTrue(Arrangement.two().faces.indices.none(two::hasAction))
    }

    @Test
    fun actionsOfTheLargestExamples() {
        // computed on the UI thread after every change: must stay fast
        for (name in listOf("n42-great-fueredi-palasti", "n14-digonfree-some-min-triangles")) {
            val set = Examples.byName(name) ?: continue
            val arr = set.arrangement(0)
            val start = TimeSource.Monotonic.markNow()
            val actions = CellActions(arr)
            println("CellActions $name: ${start.elapsedNow()} (${actions.creates.size} creates, ${actions.collapsible.size} collapsible)")
        }
    }

    @Test
    fun sampleSetIsBroad() {
        assertNotNull(samples.firstOrNull { !it.isIntersecting })
        assertTrue(samples.size > 500)
    }
}
