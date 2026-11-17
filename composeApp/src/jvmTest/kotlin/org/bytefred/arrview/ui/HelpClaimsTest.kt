package org.bytefred.arrview.ui

import org.bytefred.arrview.core.Arrangement
import org.bytefred.arrview.core.DrawModel
import org.bytefred.arrview.core.LayoutSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** What the Help pages state, checked against the code (as PoCoView's HelpClaimsTest). */
class HelpClaimsTest {
    private val pages = HelpPage.entries.associateWith { helpText(it).joinToString(" ") }

    private fun assertSays(page: HelpPage, claim: String) =
        assertTrue(claim in pages.getValue(page), "Help ${page.title} should say \"$claim\"")

    @Test
    fun onlyAscii() {
        for ((page, text) in pages) {
            val bad = text.filter { it.code !in 32..126 }
            assertEquals("", bad, "non-ASCII characters on the Help page ${page.title}")
        }
    }

    @Test
    fun everyWidgetIsExplained() {
        for (w in Widget.entries) assertSays(HelpPage.WIDGETS, "${w.label}: ")
    }

    @Test
    fun layoutDefaults() {
        assertSays(HelpPage.WIDGETS, "Max. iterations (default ${LayoutSettings().maxIterations})")
        assertSays(HelpPage.WIDGETS, "eps (default ${epsText(LayoutSettings().eps)})")
    }

    @Test
    fun modelNames() {
        for (m in DrawModel.entries) assertSays(HelpPage.GENERAL, m.label)
    }

    @Test
    fun signConvention() {
        // "+j in list i and -i in list j are the same crossing": every +j in list i has its -i in list j
        val krupp = Arrangement.krupp()
        for (i in 0 until krupp.n) for (e in krupp.list(i)) if (e > 0) assertTrue(-(i + 1) in krupp.list(e - 1))
        assertSays(HelpPage.FORMATS, "So +j in list i and -i in list j are the same crossing.")
    }
}
