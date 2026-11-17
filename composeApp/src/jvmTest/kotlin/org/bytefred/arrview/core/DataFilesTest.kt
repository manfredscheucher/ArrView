package org.bytefred.arrview.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * The bundled examples against further research files (test resources in `data/`, see
 * doc/DEVELOPMENT.md): p-vector statistics, the sequences of the old sage code, and the model 2
 * lists in the OLD encoding.
 */
class DataFilesTest {
    private fun resource(path: String): String =
        assertNotNull(javaClass.getResource("/data/$path"), path).readText()

    private fun members(name: String): List<Arrangement> =
        assertNotNull(Examples.byName(name), name).let { s -> (0 until s.size).map(s::arrangement) }

    @Test
    fun pVectorsMatchTheStatistics() {
        for (n in 3..5) {
            // {(0, 0, 0, 8): 1, (0, 0, 3, 2, 3): 1}
            val expected = Regex("""\(([\d, ]+)\): (\d+)""").findAll(resource("stats/all$n.enc.stat.txt"))
                .associate { m -> m.groupValues[1].split(",").map { it.trim().toInt() } to m.groupValues[2].toInt() }
            val actual = members("n$n-intersecting-all").groupingBy { it.pVector.toList() }.eachCount()
            assertEquals(expected, actual, "n=$n")
        }
    }

    @Test
    fun sequencesOfTheOldCodeMatchModel1() {
        for (n in 3..5) {
            val sequences = resource("fingerprints/all$n.enc.fingerprints.txt").lines().filter { it.isNotBlank() }
            val model1 = members("n$n-intersecting-all")
            assertEquals(model1.size, sequences.size)
            for ((i, text) in sequences.withIndex()) {
                assertEquals(model1[i].canonical, Arrangement.parse(text).canonical, "n=$n line ${i + 1}")
            }
        }
    }

    @Test
    fun oldAndNewModel2EncodingsAgree() {
        for ((n, count) in listOf(3 to 3, 4 to 21, 5 to 984)) {
            val old = resource("model2_old/all$n.mod2s6").lines().filter { it.isNotBlank() }.map { Model2.decode(it).canonical }
            assertEquals(count, old.size)
            assertEquals(count, old.toSet().size)
            assertEquals(members("n$n-connected-all").map { it.canonical }.toSet(), old.toSet(), "n=$n")
        }
    }
}
