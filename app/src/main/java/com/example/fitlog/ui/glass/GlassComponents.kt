package com.example.fitlog.ui.glass

import com.example.fitlog.ui.theme.FitLogAlpha
import com.example.fitlog.ui.theme.FitLogTheme
import com.example.fitlog.ui.theme.GlassColors

import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.example.fitlog.R
import com.example.fitlog.data.settings.UiLook
import com.example.fitlog.ui.glass.backdrop.backdrops.LayerBackdrop

/** Uses the same source recording and 0.33-scale blur/lens path as Myspoti, adapting to light and dark. */
@Composable
internal fun Modifier.glassSurface(
    shape: Shape = RoundedCornerShape(FitLogTheme.dimens.glass.surfaceCorner),
    page: LayerBackdrop? = LocalGlassScene.current,
    interaction: MutableInteractionSource? = null,
    tint: Color = Color.Unspecified,
): Modifier {
    val glass = FitLogTheme.dimens.glass
    val dark = GlassColors.isDark(MaterialTheme.colorScheme.surface)
    val effectiveTint = if (tint.isSpecified) tint else GlassColors.glassTint(dark)
    val press = rememberGlassPress(interaction)
    if (page != null && LocalImmersiveBackdrop.current != null) {
        return immersiveLiquidGlass(shape, page, tint = effectiveTint, press = press)
    }
    val frost = if (page != null && Build.VERSION.SDK_INT >= 31)
        Modifier.immersivePageFrost(page, GlassColors.frostTint(dark), glass.frostBlurRadius)
        else Modifier.background(GlassColors.fill(dark))
    val borderColor = GlassColors.frostBorder(dark)
    return then(if (press == null) Modifier else Modifier.graphicsLayer {
        scaleX = 1f - glass.pressScaleDepth * press(); scaleY = scaleX
    }).clip(shape).then(frost).border(glass.frostBorderWidth, borderColor, shape)
}

@Composable
internal fun GlassCard(modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(FitLogTheme.dimens.glass.surfaceCorner),
    content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.glassSurface(shape), content = content)
}

/** Classic delegates to the original Material Surface without changing its defaults. */
@Composable
internal fun LookSurface(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    color: Color = MaterialTheme.colorScheme.surface,
    content: @Composable () -> Unit,
) {
    if (LocalUiLook.current == UiLook.LIQUID_GLASS) Box(modifier.glassSurface(shape)) { content() }
    else Surface(modifier = modifier, shape = shape, color = color, content = content)
}

@Composable
internal fun GlassButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null, enabled: Boolean = true, accent: Boolean = false) {
    val glass = FitLogTheme.dimens.glass
    val dark = GlassColors.isDark(MaterialTheme.colorScheme.surface)
    val source = remember { MutableInteractionSource() }
    val shape = CircleShape
    val tint = when {
        LocalUiLook.current == UiLook.GLASS_NAVIGATION -> GlassColors.navTint(dark, fullLook = false)
        accent -> GlassColors.accentButtonTint(dark, MaterialTheme.colorScheme.primary)
        else -> GlassColors.glassTint(dark)
    }
    val iconTint = if (accent) MaterialTheme.colorScheme.primary else if (dark) GlassColors.Accent else MaterialTheme.colorScheme.primary
    val textColor = if (enabled) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSurface.copy(alpha = FitLogAlpha.DISABLED)
    Row(modifier.heightIn(min = glass.buttonMinHeight)
        .glassSurface(shape, interaction = source, tint = tint)
        .clip(shape).clickable(enabled = enabled, role = Role.Button, interactionSource = source,
            indication = null, onClick = onClick)
        .padding(horizontal = glass.buttonHorizontalPadding, vertical = glass.buttonVerticalPadding),
        horizontalArrangement = Arrangement.spacedBy(glass.buttonIconGap, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically) {
        icon?.let { Icon(painterResource(it), null, Modifier.size(glass.iconSize), tint = iconTint) }
        Text(text, style = MaterialTheme.typography.titleMedium, color = textColor)
    }
}

@Composable
internal fun GlassIconButton(@DrawableRes icon: Int, label: String, onClick: () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true) {
    val glass = FitLogTheme.dimens.glass
    val source = remember { MutableInteractionSource() }
    val iconColor = if (enabled) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSurface.copy(alpha = FitLogAlpha.DISABLED)
    Box(modifier.size(glass.iconButtonSize).glassSurface(CircleShape, interaction = source).clip(CircleShape)
        .clickable(enabled = enabled, role = Role.Button, interactionSource = source, indication = null,
            onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), label, Modifier.size(glass.iconSize), tint = iconColor)
    }
}

@Composable
internal fun <T> GlassSegmentedControl(choices: List<T>, selected: T, labels: List<String>,
    onSelect: (T) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, canSelect: (T) -> Boolean = { true }) {
    val glass = FitLogTheme.dimens.glass
    val spacing = FitLogTheme.spacing
    val dark = GlassColors.isDark(MaterialTheme.colorScheme.surface)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val trackPadding = spacing.xs
        val itemWidth = maxOf(glass.segmentedItemMinWidth, (maxWidth - trackPadding * 2) / choices.size)
        Row(Modifier.glassSurface(CircleShape).horizontalScroll(rememberScrollState())
            .selectableGroup().padding(trackPadding), horizontalArrangement = Arrangement.spacedBy(glass.segmentedItemGap)) {
            choices.forEachIndexed { index, choice ->
                val checked = choice == selected
                val checkedBg = GlassColors.segmentSelected(dark, MaterialTheme.colorScheme.primaryContainer)
                val textColor = when {
                    checked -> GlassColors.onSegmentSelected(dark, MaterialTheme.colorScheme.onPrimaryContainer)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Box(Modifier.widthIn(min = itemWidth - glass.segmentedItemGap).heightIn(min = glass.segmentedItemMinHeight)
                    .background(if (checked) checkedBg else Color.Transparent, CircleShape)
                    .clip(CircleShape).selectable(checked, enabled = enabled && canSelect(choice), role = Role.RadioButton,
                        onClick = { onSelect(choice) })
                    .padding(horizontal = glass.segmentedItemHorizontalPadding, vertical = spacing.m),
                    contentAlignment = Alignment.Center) {
                    Text(labels[index], style = MaterialTheme.typography.labelLarge,
                        color = textColor, maxLines = 1)
                }
            }
        }
    }
}

@Composable
internal fun GlassTopAppBar(title: String, subtitle: String?, onBack: (() -> Unit)?, backEnabled: Boolean,
    actions: @Composable RowScope.() -> Unit) {
    val spacing = FitLogTheme.spacing
    Column(Modifier.fillMaxWidth().padding(horizontal = FitLogTheme.dimens.glass.pageGutter, vertical = spacing.l),
        verticalArrangement = Arrangement.spacedBy(spacing.s)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.m)) {
            onBack?.let {
                if (LocalUiLook.current == UiLook.LIQUID_GLASS) {
                    GlassIconButton(R.drawable.arrow_back_24px, stringResource(R.string.cd_back), it, enabled = backEnabled)
                } else {
                    FilledTonalIconButton(onClick = it, enabled = backEnabled, shapes = IconButtonDefaults.shapes()) {
                        Icon(painterResource(R.drawable.arrow_back_24px), stringResource(R.string.cd_back))
                    }
                }
            }
            Text(title, style = MaterialTheme.typography.headlineMediumEmphasized, maxLines = 2,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).semantics { heading() })
            actions()
        }
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
internal fun GlassSearchField(state: TextFieldState, modifier: Modifier = Modifier) {
    val spacing = FitLogTheme.spacing
    val textColor = MaterialTheme.colorScheme.onSurface
    val primaryColor = MaterialTheme.colorScheme.primary
    Row(modifier.fillMaxWidth().heightIn(min = FitLogTheme.dimens.glass.searchFieldMinHeight)
        .glassSurface(CircleShape).padding(horizontal = spacing.l),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.m)) {
        Icon(painterResource(R.drawable.search_24px), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        val label = stringResource(R.string.log_search)
        Box(Modifier.weight(1f)) {
            if (state.text.isEmpty()) Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
            BasicTextField(state = state, lineLimits = TextFieldLineLimits.SingleLine,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = textColor),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(primaryColor),
                modifier = Modifier.fillMaxWidth().padding(vertical = spacing.l)
                    .semantics { contentDescription = label })
        }
        if (state.text.isNotEmpty()) IconButton(onClick = state::clearText) {
            Icon(painterResource(R.drawable.close_24px), stringResource(R.string.log_clear_search))
        }
    }
}
