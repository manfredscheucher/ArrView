package org.bytefred.arrview.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.bytefred.arrview.core.Arrangement

/** Placeholder UI (phase 1): the app header and one example arrangement as text. */
@Composable
fun App() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("ArrView", style = MaterialTheme.typography.headlineMedium)
                Text("Arrangements of pseudocircles", style = MaterialTheme.typography.titleSmall)
                Text(Arrangement.krupp().toString(), modifier = Modifier.padding(top = 16.dp))
            }
        }
    }
}
