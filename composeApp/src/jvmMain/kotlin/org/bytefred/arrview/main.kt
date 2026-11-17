package org.bytefred.arrview

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.bytefred.arrview.ui.App

/** Desktop (JVM) entry point. */
fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "ArrView",
        state = rememberWindowState(width = 1200.dp, height = 800.dp),
    ) {
        App()
    }
}
