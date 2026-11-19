package org.bytefred.arrview.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The whole [DrawingSweep]: every intersecting and connected arrangement with n = 3, 4, 5 and all
 * their flips. Excluded from `jvmTest` (it takes minutes); run it with
 * `./gradlew :composeApp:jvmTest -PfullSweep --tests '*DrawingFullSweepTest'`. The report is
 * `composeApp/build/sweep/full.txt`.
 */
class DrawingFullSweepTest {
    @Test
    fun everyArrangementWithItsFlips() {
        val report = DrawingSweep.run(DrawingSweep.sweepSets, File("build/sweep/full.txt"))
        println("full sweep: $report")
        assertTrue(report.defects.isEmpty(), "$report\n" + report.defects.take(40).joinToString("\n"))
    }
}
