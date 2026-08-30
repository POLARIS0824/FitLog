package com.example.fitlog.feature.today

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * 仪表盘下方快捷操作按钮组：[+ Log]、[🏃 Start]、[✏️ 编辑]
 * 支持点击与按住时细腻的弹性微膨胀及旁侧按钮微挤压 (compress) 物理动效。
 *
 * @param onLogClick 点击 Log 按钮回调
 * @param onStartClick 点击 Start 按钮回调
 * @param onEditClick 点击编辑按钮回调
 */
@Composable
internal fun MetricActionButtons(
    onLogClick: () -> Unit,
    onStartClick: () -> Unit,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val logInteractionSource = remember { MutableInteractionSource() }
    val isLogPressed by logInteractionSource.collectIsPressedAsState()

    val startInteractionSource = remember { MutableInteractionSource() }
    val isStartPressed by startInteractionSource.collectIsPressedAsState()

    val editInteractionSource = remember { MutableInteractionSource() }
    val isEditPressed by editInteractionSource.collectIsPressedAsState()

    // 点击/按住状态延时维持 150ms，确保短促的轻点 (click) 也能完整触发微妙挤压动效
    var isLogActive by remember { mutableStateOf(false) }
    var isStartActive by remember { mutableStateOf(false) }
    var isEditActive by remember { mutableStateOf(false) }

    LaunchedEffect(isLogPressed) {
        if (isLogPressed) {
            isLogActive = true
            delay(150)
            if (!isLogPressed) isLogActive = false
        } else {
            isLogActive = false
        }
    }

    LaunchedEffect(isStartPressed) {
        if (isStartPressed) {
            isStartActive = true
            delay(150)
            if (!isStartPressed) isStartActive = false
        } else {
            isStartActive = false
        }
    }

    LaunchedEffect(isEditPressed) {
        if (isEditPressed) {
            isEditActive = true
            delay(150)
            if (!isEditPressed) isEditActive = false
        } else {
            isEditActive = false
        }
    }

    // 微型挤压比例（轻微变化，极致优雅）
    val logWeightTarget = when {
        isLogActive -> 1.08f
        isStartActive -> 0.92f
        isEditActive -> 0.96f
        else -> 1.0f
    }

    val startWeightTarget = when {
        isStartActive -> 1.08f
        isLogActive -> 0.92f
        isEditActive -> 0.96f
        else -> 1.0f
    }

    val editWidthTarget = when {
        isEditActive -> 48.dp
        isLogActive || isStartActive -> 41.dp
        else -> 44.dp
    }

    val springSpec = spring<Float>(
        stiffness = Spring.StiffnessMedium,
        dampingRatio = Spring.DampingRatioLowBouncy,
    )

    val dpSpringSpec = spring<Dp>(
        stiffness = Spring.StiffnessMedium,
        dampingRatio = Spring.DampingRatioLowBouncy,
    )

    val animatedLogWeight by animateFloatAsState(
        targetValue = logWeightTarget,
        animationSpec = springSpec,
        label = "logWeight",
    )

    val animatedStartWeight by animateFloatAsState(
        targetValue = startWeightTarget,
        animationSpec = springSpec,
        label = "startWeight",
    )

    val animatedEditWidth by animateDpAsState(
        targetValue = editWidthTarget,
        animationSpec = dpSpringSpec,
        label = "editWidth",
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Log 按钮
        ActionPillButton(
            onClick = onLogClick,
            interactionSource = logInteractionSource,
            icon = Icons.Default.Add,
            label = "Log",
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier
                .weight(animatedLogWeight)
                .height(44.dp),
        )

        // Start 按钮
        ActionPillButton(
            onClick = onStartClick,
            interactionSource = startInteractionSource,
            icon = Icons.AutoMirrored.Filled.DirectionsRun,
            label = "Start",
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier
                .weight(animatedStartWeight)
                .height(44.dp),
        )

        // 编辑图标按钮
        Surface(
            onClick = onEditClick,
            interactionSource = editInteractionSource,
            modifier = Modifier
                .height(44.dp)
                .width(animatedEditWidth),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "编辑",
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** 胶囊形图标+文字按钮（Log / Start 共用形态）。 */
@Composable
private fun ActionPillButton(
    onClick: () -> Unit,
    interactionSource: MutableInteractionSource,
    icon: ImageVector,
    label: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = modifier,
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}
