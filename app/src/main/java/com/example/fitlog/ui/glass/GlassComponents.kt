package com.example.fitlog.ui.glass

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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.fitlog.R
import com.example.fitlog.data.settings.UiLook
import com.example.fitlog.ui.glass.backdrop.backdrops.LayerBackdrop

/** Uses the same source recording and 0.33-scale blur/lens path as Myspoti, adapting to light and dark. */
@Composable
internal fun Modifier.glassSurface(
    shape: Shape = RoundedCornerShape(28.dp),
    page: LayerBackdrop? = LocalGlassScene.current,
    interaction: MutableInteractionSource? = null,
    tint: Color = Color.Unspecified,
): Modifier {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val effectiveTint = if (tint.isSpecified) tint else GlassTokens.glassTint(dark)
    val press = rememberGlassPress(interaction)
    if (page != null && LocalImmersiveBackdrop.current != null) {
        return immersiveLiquidGlass(shape, page, tint = effectiveTint, press = press)
    }
    val fallbackFill = GlassTokens.fill(dark)
    val frost = if (page != null && Build.VERSION.SDK_INT >= 31)
        Modifier.immersivePageFrost(page, fallbackFill.copy(alpha = if (dark) 0.75f else 0.85f), 24f)
        else Modifier.background(fallbackFill)
    val borderColor = if (dark) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.08f)
    return then(if (press == null) Modifier else Modifier.graphicsLayer {
        scaleX = 1f - 0.04f * press(); scaleY = scaleX
    }).clip(shape).then(frost).border(0.75.dp, borderColor, shape)
}

@Composable
internal fun GlassCard(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(28.dp),
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
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val source = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(50)
    val tint = when {
        LocalUiLook.current == UiLook.GLASS_NAVIGATION ->
            if (dark) Color(0xFF1C1C22).copy(alpha = 0.82f) else Color.White.copy(alpha = 0.85f)
        accent -> MaterialTheme.colorScheme.primary.copy(alpha = if (dark) 0.18f else 0.12f)
        else -> GlassTokens.glassTint(dark)
    }
    val iconTint = if (accent) MaterialTheme.colorScheme.primary else if (dark) GlassTokens.Accent else MaterialTheme.colorScheme.primary
    val textColor = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    Row(modifier.heightIn(min = 52.dp)
        .glassSurface(shape, interaction = source, tint = tint)
        .clip(shape).clickable(enabled = enabled, role = Role.Button, interactionSource = source,
            indication = null, onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically) {
        icon?.let { Icon(painterResource(it), null, Modifier.size(22.dp), tint = iconTint) }
        Text(text, style = MaterialTheme.typography.titleMedium, color = textColor)
    }
}

@Composable
internal fun GlassIconButton(@DrawableRes icon: Int, label: String, onClick: () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true) {
    val source = remember { MutableInteractionSource() }
    val iconColor = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    Box(modifier.size(48.dp).glassSurface(CircleShape, interaction = source).clip(CircleShape)
        .clickable(enabled = enabled, role = Role.Button, interactionSource = source, indication = null,
            onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), label, Modifier.size(22.dp), tint = iconColor)
    }
}

@Composable
internal fun <T> GlassSegmentedControl(choices: List<T>, selected: T, labels: List<String>,
    onSelect: (T) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, canSelect: (T) -> Boolean = { true }) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val itemWidth = maxOf(88.dp, (maxWidth - 8.dp) / choices.size)
        Row(Modifier.glassSurface(RoundedCornerShape(50)).horizontalScroll(rememberScrollState())
            .selectableGroup().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            choices.forEachIndexed { index, choice ->
                val checked = choice == selected
                val checkedBg = if (dark) Color.White.copy(alpha = 0.15f) else MaterialTheme.colorScheme.primaryContainer
                val textColor = when {
                    checked -> if (dark) Color.White else MaterialTheme.colorScheme.onPrimaryContainer
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Box(Modifier.widthIn(min = itemWidth - 2.dp).heightIn(min = 44.dp)
                    .background(if (checked) checkedBg else Color.Transparent, CircleShape)
                    .clip(CircleShape).selectable(checked, enabled = enabled && canSelect(choice), role = Role.RadioButton,
                        onClick = { onSelect(choice) }).padding(horizontal = 14.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
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
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
    val textColor = MaterialTheme.colorScheme.onSurface
    val primaryColor = MaterialTheme.colorScheme.primary
    Row(modifier.fillMaxWidth().heightIn(min = 56.dp).glassSurface(CircleShape).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(painterResource(R.drawable.search_24px), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        val label = stringResource(R.string.log_search)
        Box(Modifier.weight(1f)) {
            if (state.text.isEmpty()) Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
            BasicTextField(state = state, lineLimits = TextFieldLineLimits.SingleLine,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = textColor),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(primaryColor),
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                    .semantics { contentDescription = label })
        }
        if (state.text.isNotEmpty()) IconButton(onClick = state::clearText) {
            Icon(painterResource(R.drawable.close_24px), stringResource(R.string.log_clear_search))
        }
    }
}
