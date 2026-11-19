package org.bytefred.arrview.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A small round "i" next to a heading or a control: a click shows [text] in a popover (closed by
 * a click anywhere). The explanations of the GUI live here, so the widgets stay short. Test tag
 * `info-[tag]`.
 */
@Composable
fun InfoButton(text: String, tag: String) {
    var open by remember { mutableStateOf(false) }
    Box {
        InfoIcon(tag) { open = !open }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            Text(
                text,
                Modifier.widthIn(max = INFO_WIDTH).padding(horizontal = 12.dp, vertical = 8.dp).testTag("info-$tag-text"),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** The "i" in a circle (a glyph in a drawn circle, no icon font); [onClick] opens a popover or a Help page. */
@Composable
fun InfoIcon(tag: String, onClick: () -> Unit) {
    val color = MaterialTheme.colorScheme.primary
    Box(
        Modifier.padding(4.dp).size(18.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick)
            .border(1.5.dp, color, CircleShape).testTag("info-$tag"),
        contentAlignment = Alignment.Center,
    ) {
        Text("i", color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, lineHeight = 11.sp)
    }
}

/** A popover text wraps at this width. */
private val INFO_WIDTH = 320.dp
