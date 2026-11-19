package org.bytefred.arrview.core

import java.io.File
import kotlin.time.TimeSource

/**
 * The drawing sweep (Manfred, 2026-11-19): arrangements and every single flip the app offers
 * (triangle flips, digon collapses that keep it connected, digon creates). Every result must be
 * valid by Linhart-Ortner and draw cleanly in every model it has ([CurveCheck.defects]: finite,
 * no curve crosses itself, every pair meets as the arrangement says).
 */
object DrawingSweep {
    /** The arrangements of the sweep: all of the intersecting and connected sets with n = 3, 4, 5. */
    val sweepSets: List<ExampleSet> = (3..5).flatMap { n -> listOf("n$n-intersecting-all", "n$n-connected-all") }.map { Examples.byName(it)!! }

    /** [arr] and every single flip of it, labelled. */
    fun withFlips(name: String, arr: Arrangement): List<Pair<String, Arrangement>> = buildList {
        add(name to arr)
        val actions = CellActions(arr)
        for (f in actions.triangles) add("$name, flip triangle ${arr.cellDescription(f)} (face $f)" to arr.triangleFlip(f))
        for (f in actions.collapsible) add("$name, collapse digon ${arr.cellDescription(f)} (face $f)" to arr.digonCollapse(f)!!)
        for (op in actions.creates) add("$name, create digon ${op.i + 1} ${op.j + 1} in face ${op.face} (darts ${op.dartI}, ${op.dartJ})" to arr.digonCreate(op))
    }

    /** The problems of [arr] (named [name]): invalid, or a defect per model; and the sub-pixel model 1 digons ([CurveCheck.check]). */
    fun check(name: String, arr: Arrangement, settings: LayoutSettings = LayoutSettings()): Pair<List<String>, List<String>> {
        LinhartOrtner.check(arr.lists)?.let { return listOf("$name: invalid: $it") to emptyList() }
        val problems = ArrayList<String>()
        val subPixel = ArrayList<String>()
        for (model in DrawModel.entries.filter { it == DrawModel.MODEL2 || arr.hasModel1 }) {
            val d = ArrangementLayout(arr, model, arr.defaultOuterFace(model)).layout(settings)
            val result = CurveCheck.check(d)
            problems += result.defects.map { "$name, ${model.label}: $it" }
            subPixel += result.subPixelDigons.map { "$name, ${model.label}: $it" }
        }
        return problems to subPixel
    }

    /** The problems of [arr] (see [check]). */
    fun problems(name: String, arr: Arrangement): List<String> = check(name, arr).first

    /**
     * Model 1 digons whose two curves cross visibly near the ends of their shared piece instead of
     * touching: a result of the literal port (draw7.sage itself refuses digons), reported to Manfred
     * (doc/SPEC.md, 2026-11-19); listed, not failed.
     */
    fun isModel1DigonCrossing(problem: String) = ", Model 1: curves " in problem && problem.endsWith("expected a touching")

    class Report(val cases: Int, val drawings: Int, val digons: Int, val problems: List<String>, val subPixelDigons: List<String>, val seconds: Double) {
        /** The problems other than [isModel1DigonCrossing]. */
        val defects: List<String> get() = problems.filterNot(::isModel1DigonCrossing)

        override fun toString() =
            "$cases arrangements, $drawings drawings, ${defects.size} defects, model 1 digons: $digons, of them " +
                "${problems.size - defects.size} crossing visibly and ${subPixelDigons.size} crossing below a pixel, %.1f s".format(seconds)
    }

    /** Sweeps the members of [sets] chosen by [pick] (set, index) with all their flips; the report also goes to [out]. */
    fun run(sets: List<ExampleSet>, out: File?, pick: (ExampleSet, Int) -> Boolean = { _, _ -> true }): Report {
        val clock = TimeSource.Monotonic.markNow()
        val problems = ArrayList<String>()
        val subPixel = ArrayList<String>()
        var cases = 0
        var drawings = 0
        var digons = 0
        for (set in sets) for (i in 0 until set.size) {
            if (!pick(set, i)) continue
            for ((name, arr) in withFlips(set.memberName(i), set.arrangement(i))) {
                cases++
                drawings += if (arr.hasModel1) 2 else 1
                if (arr.hasModel1) digons += arr.digonCount
                val (p, s) = check(name, arr)
                problems += p
                subPixel += s
            }
        }
        val report = Report(cases, drawings, digons, problems, subPixel, clock.elapsedNow().inWholeMilliseconds / 1000.0)
        out?.let {
            it.parentFile.mkdirs()
            it.writeText(
                "$report\n\nDefects:\n" + report.defects.joinToString("\n") +
                    "\n\nModel 1 digons crossing visibly:\n" + problems.filter(::isModel1DigonCrossing).joinToString("\n") +
                    "\n\nModel 1 digons crossing below a pixel:\n" + subPixel.joinToString("\n") + "\n",
            )
        }
        return report
    }
}
