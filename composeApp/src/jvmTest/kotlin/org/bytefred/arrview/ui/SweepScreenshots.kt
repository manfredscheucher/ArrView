package org.bytefred.arrview.ui

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.bytefred.arrview.core.Arrangement
import org.bytefred.arrview.core.DrawModel
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.assertNotNull

/** Screenshots of single drawings (the defects of the drawing sweep), to build/sweep/screenshots/. */
object SweepScreenshots {
    fun render(name: String, arr: Arrangement, model: DrawModel, file: String, w: Int = 900, h: Int = 700) {
        clearInMemoryPrefs()
        val state = AppState(CoroutineScope(SupervisorJob() + Dispatchers.Default))
        state.openWidgets.clear()
        state.load(name, arr)
        state.changeModel(model)
        runBlocking { state.awaitLayout() }
        val scene = ImageComposeScene(w, h, Density(1f)) { App(state, initialDialog = DialogKind.NONE) }
        try {
            for (t in 0L until 4L) scene.render(t * 50_000_000L).close()
            val image = scene.render(400_000_000L)
            val dir = File("build/sweep/screenshots").apply { mkdirs() }
            dir.resolve(file).writeBytes(assertNotNull(image.encodeToData(EncodedImageFormat.PNG)).bytes)
            image.close()
        } finally {
            scene.close()
        }
    }
}
