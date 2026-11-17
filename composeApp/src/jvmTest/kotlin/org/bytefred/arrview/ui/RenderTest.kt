package org.bytefred.arrview.ui

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.bytefred.arrview.core.Arrangement
import org.bytefred.arrview.core.DrawModel
import org.bytefred.arrview.core.Examples
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * Renders the whole app offscreen, so drawing crashes show up as test failures, and writes
 * screenshots to composeApp/build/screenshots/ (to look at).
 */
class RenderTest {
    private fun state() = AppState(CoroutineScope(SupervisorJob() + Dispatchers.Default))

    @BeforeTest
    fun clearPrefs() = clearInMemoryPrefs()

    private fun render(state: AppState, w: Int = 1200, h: Int = 800, file: String? = null) {
        runBlocking { state.awaitLayout() }
        val scene = ImageComposeScene(w, h, Density(1f)) { App(state) }
        try {
            // several frames: first layout, then the fit
            for (t in 0L until 4L) scene.render(t * 50_000_000L).close()
            val image = scene.render(400_000_000L)
            if (file != null) {
                val dir = File("build/screenshots").apply { mkdirs() }
                dir.resolve(file).writeBytes(assertNotNull(image.encodeToData(EncodedImageFormat.PNG)).bytes)
            }
            image.close()
        } finally {
            scene.close()
        }
    }

    @Test
    fun startExample() {
        val s = state()
        render(s, file = "start-model1.png")
        s.changeModel(DrawModel.MODEL2)
        render(s, file = "start-model2.png")
        render(s, w = 420, h = 860, file = "start-portrait.png")
    }

    @Test
    fun examplesWithDigonsAndLargeN() {
        for (name in listOf("n4-intersecting-all", "n5-connected-all", "n24-great-fueredi-palasti", "n9-great-nonr-pappus")) {
            val s = state()
            val set = assertNotNull(Examples.byName(name), name)
            // the member with the most digons
            s.load(name, (0 until set.size).map(set::arrangement).maxBy { it.digonCount })
            render(s, file = "$name-model1.png")
            s.changeModel(DrawModel.MODEL2)
            render(s, file = "$name-model2.png")
        }
    }

    @Test
    fun twoCircles() {
        val s = state()
        s.load("two", Arrangement.two())
        render(s, file = "two.png")
        render(s, w = 300, h = 200)
    }
}
