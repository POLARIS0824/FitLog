package com.example.fitlog.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.fitlog.R
import com.example.fitlog.data.settings.UiLook

@Composable
internal fun GlassLookPicker(selected: UiLook, enabled: Boolean, onSelect: (UiLook) -> Unit) {
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        UiLook.entries.forEach { look ->
            val checked = selected == look
            val liquid = look != UiLook.CLASSIC
            val fullLook = look == UiLook.LIQUID_GLASS
            Surface(shape = MaterialTheme.shapes.large,
                color = if (checked) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest) {
                Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large)
                    .selectable(checked, enabled = enabled, role = Role.RadioButton, onClick = { onSelect(look) })
                    .padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(
                        Brush.linearGradient(if (fullLook) listOf(Color(0xFF242C4D), Color(0xFF60547A)) else
                            listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surfaceContainer))),
                        contentAlignment = Alignment.BottomCenter) {
                        Row(Modifier.padding(8.dp).fillMaxWidth().height(16.dp)
                            .background(if (liquid) Color.White.copy(alpha = 0.20f) else MaterialTheme.colorScheme.secondaryContainer,
                                RoundedCornerShape(50)).padding(3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            repeat(3) { Box(Modifier.size(10.dp).background(if (liquid) Color.White.copy(alpha = 0.60f)
                                else MaterialTheme.colorScheme.primary, RoundedCornerShape(50))) }
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(when (look) {
                            UiLook.CLASSIC -> R.string.look_classic
                            UiLook.LIQUID_GLASS -> R.string.look_liquid
                            UiLook.GLASS_NAVIGATION -> R.string.look_glass_navigation
                        }), style = MaterialTheme.typography.titleMediumEmphasized)
                        Text(stringResource(when (look) {
                            UiLook.CLASSIC -> R.string.look_classic_description
                            UiLook.LIQUID_GLASS -> R.string.look_liquid_description
                            UiLook.GLASS_NAVIGATION -> R.string.look_glass_navigation_description
                        }),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    RadioButton(checked, onClick = null, enabled = enabled)
                }
            }
        }
    }
}
