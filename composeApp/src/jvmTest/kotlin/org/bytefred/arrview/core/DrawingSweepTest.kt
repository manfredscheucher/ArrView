package org.bytefred.arrview.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** A representative part of the [DrawingSweep]; the whole one is [DrawingFullSweepTest]. */
class DrawingSweepTest {
    @Test
    fun startExampleAndBundledSixDrawCleanly() {
        val problems = ArrayList<String>()
        problems += DrawingSweep.problems(Examples.start.name, Examples.start.arrangement())
        for (set in Examples.sets.filter { it.n == 6 }) for (i in listOf(0, set.size - 1).distinct()) {
            problems += DrawingSweep.problems(set.memberName(i), set.arrangement(i))
        }
        File("build/sweep/six.txt").also { it.parentFile.mkdirs() }.writeText(problems.joinToString("\n") + "\n")
        assertTrue(problems.filterNot(DrawingSweep::isModel1DigonCrossing).isEmpty(), problems.joinToString("\n"))
    }

    @Test
    fun kruppFlips() {
        // Manfred's screenshot: n3-intersecting-all #1 (the Krupp), a triangle flipped: model 1 drew circle 2 as a figure 8
        val krupp = Examples.byName("n3-intersecting-all")!!
        val problems = DrawingSweep.withFlips(krupp.memberName(0), krupp.arrangement(0)).flatMap { (name, arr) -> DrawingSweep.problems(name, arr) }
        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
    }

    @Test
    fun everyTwentiethArrangementWithItsFlips() {
        val report = DrawingSweep.run(DrawingSweep.sweepSets, File("build/sweep/representative.txt")) { set, i -> set.n < 5 || i % 20 == 0 }
        println("representative sweep: $report")
        assertTrue(report.defects.isEmpty(), "$report\n" + report.defects.take(40).joinToString("\n"))
    }
}
