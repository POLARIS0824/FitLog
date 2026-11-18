package com.example.fitlog.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import com.example.fitlog.R
import com.example.fitlog.ui.theme.FitLogTheme

internal object FitLogSheetDefaults {
    val ContentPadding: PaddingValues
        @Composable get() {
            val sheet = FitLogTheme.dimens.page.sheetPadding
            return PaddingValues(start = sheet, end = sheet, top = FitLogTheme.spacing.s, bottom = sheet)
        }
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
    // The confirm label is the primary action, so only it uses the emphasized style.
    val confirmStyle = MaterialTheme.typography.labelLargeEmphasized
    val cancelStyle = MaterialTheme.typography.labelLarge
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val height = ButtonDefaults.MinHeight
    val contentPadding = ButtonDefaults.contentPaddingFor(height)
    val horizontalPadding = contentPadding.calculateStartPadding(layoutDirection) +
        contentPadding.calculateEndPadding(layoutDirection)
    val gap = FitLogTheme.spacing.m
    val minimumButtonWidth = with(density) {
        maxOf(measurer.measure(cancelLabel, confirmStyle, maxLines = 1).size.width,
            measurer.measure(confirmLabel, confirmStyle, maxLines = 1).size.width).toDp()
    } + horizontalPadding
    val cancel: @Composable (Modifier) -> Unit = { buttonModifier ->
        OutlinedButton(onClick = onDismiss, enabled = dismissEnabled,
            modifier = buttonModifier.heightIn(min = height), shapes = ButtonDefaults.shapesFor(height),
            contentPadding = contentPadding) {
            Text(cancelLabel, style = cancelStyle, maxLines = 1, softWrap = false)
        }
    }
    val confirm: @Composable (Modifier) -> Unit = { buttonModifier ->
        Button(onClick = onConfirm, enabled = enabled,
            modifier = buttonModifier.heightIn(min = height), shapes = ButtonDefaults.shapesFor(height),
            contentPadding = contentPadding) {
            Text(confirmLabel, style = confirmStyle, maxLines = 1, softWrap = false)
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
