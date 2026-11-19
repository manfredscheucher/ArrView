package org.bytefred.arrview.core

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals

/** The geometric checker of the drawing tests, on constructed curves. */
class CurveCheckTest {
    /** A smooth closed curve: the B-spline of [k] points on an ellipse. */
    private fun ellipse(cx: Double, cy: Double, rx: Double, ry: Double, k: Int = 16) =
        closedUniformBSpline((0 until k).map { i -> Point(cx + rx * cos(2 * PI * i / k), cy + ry * sin(2 * PI * i / k)) })

    @Test
    fun crossingsOfTwoCircles() {
        val check = CurveCheck(listOf(ellipse(0.0, 0.0, 1.0, 1.0), ellipse(1.0, 0.0, 1.0, 1.0), ellipse(5.0, 0.0, 1.0, 1.0)))
        assertEquals(CurveCheck.Contact(2, 0), check.contacts(0, 1))
        assertEquals(CurveCheck.Contact(2, 0), check.contacts(1, 0))
        assertEquals(CurveCheck.Contact(0, 0), check.contacts(0, 2))
        for (c in 0..2) assertEquals(0, check.selfCrossings(c))
    }

    @Test
    fun fourCrossingsOfTwoEllipses() {
        val check = CurveCheck(listOf(ellipse(0.0, 0.0, 2.0, 1.0), ellipse(0.0, 0.0, 1.0, 2.0)))
        assertEquals(CurveCheck.Contact(4, 0), check.contacts(0, 1))
    }

    @Test
    fun aFigureEightCrossesItself() {
        // a lemniscate of Gerono through its control points
        val eight = closedUniformBSpline((0 until 24).map { i -> val t = 2 * PI * (i + 0.3) / 24; Point(sin(t), sin(t) * cos(t)) })
        assertEquals(1, CurveCheck(listOf(eight)).selfCrossings(0))
    }

    @Test
    fun aSharedSpanIsATouching() {
        // as model 1 draws a digon: both curves have the four collinear control points (0,0)..(3,0),
        // one runs above, the other below
        val above = closedUniformBSpline(listOf(Point(0.0, 0.0), Point(1.0, 0.0), Point(2.0, 0.0), Point(3.0, 0.0), Point(3.0, 2.0), Point(0.0, 2.0)))
        val below = closedUniformBSpline(listOf(Point(3.0, 0.0), Point(2.0, 0.0), Point(1.0, 0.0), Point(0.0, 0.0), Point(0.0, -2.0), Point(3.0, -2.0)))
        val check = CurveCheck(listOf(above, below))
        assertEquals(CurveCheck.Contact(0, 1), check.contacts(0, 1))
        assertEquals(CurveCheck.Contact(0, 1), check.contacts(1, 0))
    }

    @Test
    fun aSharedSpanCrossedThrough() {
        // the second curve shares the span too, but comes from inside the first one (its control
        // point before the span is higher) and leaves below: it crosses there, and once more on the left
        val above = closedUniformBSpline(listOf(Point(0.0, 0.0), Point(1.0, 0.0), Point(2.0, 0.0), Point(3.0, 0.0), Point(3.0, 2.0), Point(0.0, 2.0)))
        val through = closedUniformBSpline(listOf(Point(-2.0, -1.0), Point(0.0, 3.0), Point(0.0, 0.0), Point(1.0, 0.0), Point(2.0, 0.0), Point(3.0, 0.0), Point(4.0, -1.0), Point(1.5, -4.0)))
        val check = CurveCheck(listOf(above, through))
        assertEquals(CurveCheck.Contact(2, 0), check.contacts(1, 0))
        assertEquals(CurveCheck.Contact(2, 0), check.contacts(0, 1))
    }

    @Test
    fun nonFinitePointsAreFound() {
        val bad = ellipse(0.0, 0.0, 1.0, 1.0).toMutableList().also { it[3] = it[3].copy(c1 = Point(Double.NaN, 0.0)) }
        assertEquals(false, CurveCheck(listOf(bad)).finite)
        assertEquals(true, CurveCheck(listOf(ellipse(0.0, 0.0, 1.0, 1.0))).finite)
    }

    @Test
    fun collinearSegmentsNeverCross() {
        // a straight piece: rounding noise must not make two collinear segments cross
        val a = Point(-0.016257974359291872, 0.022029246417922574)
        val b = Point(-0.014450354034441976, 0.023473500036229798)
        val p = Point(-2.1877314081382264E-4, 0.034844257444349346)
        val q = Point(0.0015209187221122949, 0.03623423753764096)
        assertEquals(false, CurveCheck.properCrossing(a, b, p, q))
        assertEquals(true, CurveCheck.properCrossing(Point(0.0, 0.0), Point(1.0, 1.0), Point(0.0, 1.0), Point(1.0, 0.0)))
        // a straight closed curve part does not cross itself
        val straight = closedUniformBSpline(listOf(Point(0.0, 0.0), Point(1.0, 0.0), Point(2.0, 0.0), Point(3.0, 0.0), Point(4.0, 0.0), Point(2.0, 3.0)))
        assertEquals(0, CurveCheck(listOf(straight)).selfCrossings(0))
    }
}
