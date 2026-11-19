package org.bytefred.arrview.core

import java.io.File
import kotlin.time.TimeSource

fun main(args: Array<String>) = if (args.isNotEmpty()) limitMain() else LayoutImplementationProbe().probe()

/** PROBE (temporary): variants of the optimized layout against the classic reference (100 iterations, eps 1e-10). */
class LayoutImplementationProbe {
    private class Case(val name: String, val arr: Arrangement, val model: DrawModel)

    private fun cases(): List<Case> = buildList {
        for (set in Examples.sets) for (i in listOf(0, set.size - 1).distinct()) {
            val arr = set.arrangement(i)
            for (model in DrawModel.entries) if (model == DrawModel.MODEL2 || arr.hasModel1) add(Case(set.memberName(i), arr, model))
        }
        for (set in (3..5).flatMap { n -> listOf("n$n-intersecting-all", "n$n-connected-all") }.map { Examples.byName(it)!! }) for (i in 0 until set.size step maxOf(1, set.size / 6)) {
            val arr = set.arrangement(i)
            for (model in DrawModel.entries) if (model == DrawModel.MODEL2 || arr.hasModel1) add(Case(set.memberName(i), arr, model))
        }
    }

    private class Result(val iterations: Int, val millis: Double, val deviation: Double)

    private fun run(layout: ArrangementLayout, reference: List<List<Point>>?, settings: LayoutSettings, face: ((Int) -> Int)?, dist: Boolean, log: Boolean, memory: Int): Pair<Result, List<List<Point>>> {
        val clock = TimeSource.Monotonic.markNow()
        val t = IteratedTutte(layout.problem, settings, face, dist, log, memory)
        val pos = t.run()
        val ms = clock.elapsedNow().inWholeMicroseconds / 1000.0
        val ctrl = layout.controlPoints(pos)
        val dev = reference?.let { ref -> ref.indices.maxOf { c -> ref[c].indices.maxOf { k -> (ref[c][k] - ctrl[c][k]).length } } } ?: 0.0
        return Result(t.iteration, ms, dev) to ctrl
    }

    fun probe() {
        val classic = LayoutSettings(maxIterations = 50, eps = 1e-8, implementation = LayoutImplementation.CLASSIC)
        val reference = LayoutSettings(maxIterations = 100, eps = 1e-10, implementation = LayoutImplementation.CLASSIC)
        val opt = LayoutSettings(maxIterations = 50, eps = 1e-8, implementation = LayoutImplementation.OPTIMIZED)
        data class Variant(val name: String, val face: ((Int) -> Int)?, val dist: Boolean, val log: Boolean, val memory: Int)
        val variants = listOf(
            Variant("optTrueJ", null, true, true, 5),
            Variant("optJ100", { 100 }, true, true, 5),
            Variant("optJ50", { 50 }, true, true, 5),
            Variant("optLimit", { 1 }, false, true, 5),
            Variant("optJ100raw", { 100 }, true, false, 5),
            Variant("optJ100m3", { 100 }, true, true, 3),
            Variant("optJ100m8", { 100 }, true, true, 8),
        )
        val sb = StringBuilder()
        val header = "%-40s %-8s | %-14s | %-14s | " .format("case", "model", "ref100 it/ms", "classic50 it/ms/dev") + variants.joinToString(" | ") { "%-22s".format(it.name + " it/ms/dev") }
        sb.appendLine(header)
        println(header)
        val worst = HashMap<String, Double>()
        val itSum = HashMap<String, Int>()
        var count = 0
        for (c in cases()) {
            val layout = ArrangementLayout(c.arr, c.model, c.arr.defaultOuterFace(c.model))
            val (ref, refCtrl) = run(layout, null, reference, null, true, true, 5)
            val (cl, _) = run(layout, refCtrl, classic, null, true, true, 5)
            val cells = ArrayList<String>()
            cells += "%3d/%7.1f".format(ref.iterations, ref.millis)
            cells += "%3d/%7.1f/%.1e".format(cl.iterations, cl.millis, cl.deviation)
            worst["classic50"] = maxOf(worst["classic50"] ?: 0.0, cl.deviation)
            itSum["classic50"] = (itSum["classic50"] ?: 0) + cl.iterations
            for (vr in variants) {
                val (r, _) = run(layout, refCtrl, opt, vr.face, vr.dist, vr.log, vr.memory)
                cells += "%3d/%7.1f/%.1e".format(r.iterations, r.millis, r.deviation)
                worst[vr.name] = maxOf(worst[vr.name] ?: 0.0, r.deviation)
                itSum[vr.name] = (itSum[vr.name] ?: 0) + r.iterations
            }
            count++
            val line = "%-40s %-8s | ".format(c.name.take(40), c.model.label) + cells.joinToString(" | ")
            sb.appendLine(line)
            println(line)
        }
        val summary = "cases: $count\n" + worst.keys.sorted().joinToString("\n") { "%-12s worst dev %.2e  mean it %.1f".format(it, worst[it], itSum[it]!!.toDouble() / count) }
        sb.appendLine(summary)
        println(summary)
        File("probe-layout.txt").writeText(sb.toString())
    }
}
