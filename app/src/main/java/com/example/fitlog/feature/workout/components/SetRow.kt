package com.example.fitlog.feature.workout.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.fitlog.feature.workout.SetUiModel

/**
 * 单组输入行组件。
 *
 * 展示组号、重量输入框、次数输入框和删除按钮。
 *
 * @param set 单组的 UI 模型
 * @param exerciseIndex 所属动作在列表中的索引
 * @param setIndex 组在动作中的索引
 * @param onRemove 删除组的回调，参数为 (exerciseIndex, setIndex)
 * @param onUpdate 更新组的回调，参数为 (exerciseIndex, setIndex, weightKg, reps)
 */
@Composable
fun SetRow(
    set: SetUiModel,
    exerciseIndex: Int,
    setIndex: Int,
    onRemove: (Int, Int) -> Unit,
    onUpdate: (Int, Int, Float?, Int?) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${set.setNumber}.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(32.dp),
        )

        OutlinedTextField(
            value = set.weightKg?.toString() ?: "",
            onValueChange = { value ->
                val weight = value.toFloatOrNull()
                onUpdate(exerciseIndex, setIndex, weight, set.reps)
            },
            label = { Text("kg") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
            singleLine = true,
        )

        Spacer(modifier = Modifier.width(8.dp))

        OutlinedTextField(
            value = set.reps?.toString() ?: "",
            onValueChange = { value ->
                val reps = value.toIntOrNull()
                onUpdate(exerciseIndex, setIndex, set.weightKg, reps)
            },
            label = { Text("次") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
            singleLine = true,
        )

        IconButton(onClick = { onRemove(exerciseIndex, setIndex) }) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "删除组",
            )
        }
    }
}
