package com.example.fitlog.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.example.fitlog.R

internal object FitLogSheetDefaults {
    val ContentPadding = PaddingValues(start = FitLogDimens.SheetPadding, end = FitLogDimens.SheetPadding,
        top = 8.dp, bottom = FitLogDimens.SheetPadding)
}

@Composable
internal fun FitLogSheetTitle(title: String, modifier: Modifier = Modifier) {
    Text(title, style = MaterialTheme.typography.headlineSmallEmphasized,
        modifier = modifier.fillMaxWidth().semantics { heading() })
}

@Composable
internal fun FitLogSheetActions(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    dismissEnabled: Boolean = true,
    confirmLabel: String = stringResource(R.string.detail_picker_done),
) {
    val cancelLabel = stringResource(R.string.record_cancel)
    val style = MaterialTheme.typography.labelLargeEmphasized
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val padding = 16.dp
    val gap = 12.dp
    val minimumButtonWidth = with(density) {
        maxOf(measurer.measure(cancelLabel, style, maxLines = 1).size.width,
            measurer.measure(confirmLabel, style, maxLines = 1).size.width).toDp()
    } + padding * 2
    val height = ButtonDefaults.MinHeight
    val cancel: @Composable (Modifier) -> Unit = { buttonModifier ->
        OutlinedButton(onClick = onDismiss, enabled = dismissEnabled,
            modifier = buttonModifier.heightIn(min = height), shapes = ButtonDefaults.shapesFor(height),
            contentPadding = PaddingValues(horizontal = padding, vertical = 8.dp)) {
            Text(cancelLabel, style = style, maxLines = 1, softWrap = false)
        }
    }
    val confirm: @Composable (Modifier) -> Unit = { buttonModifier ->
        Button(onClick = onConfirm, enabled = enabled,
            modifier = buttonModifier.heightIn(min = height), shapes = ButtonDefaults.shapesFor(height),
            contentPadding = PaddingValues(horizontal = padding, vertical = 8.dp)) {
            Text(confirmLabel, style = style, maxLines = 1, softWrap = false)
        }
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        if (maxWidth < minimumButtonWidth * 2 + gap) Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            confirm(Modifier.fillMaxWidth())
            cancel(Modifier.fillMaxWidth())
        } else Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            cancel(Modifier.weight(1f))
            confirm(Modifier.weight(1f))
        }
    }
}
