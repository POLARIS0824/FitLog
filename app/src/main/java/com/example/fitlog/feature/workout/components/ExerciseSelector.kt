package com.example.fitlog.feature.workout.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.fitlog.domain.model.Exercise

/**
 * 动作选择器对话框。
 *
 * 提供搜索/浏览动作库的能力，用户选择动作后通过 [onSelect] 回调返回。
 *
 * @param availableExercises 全部可选动作列表
 * @param onSelect 选中动作的回调
 * @param onDismiss 关闭选择器的回调
 */
@Composable
fun ExerciseSelector(
    availableExercises: List<Exercise>,
    onSelect: (Exercise) -> Unit,
    onDismiss: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredExercises = remember(searchQuery, availableExercises) {
        if (searchQuery.isBlank()) {
            availableExercises
        } else {
            availableExercises.filter {
                it.name.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择动作") },
        text = {
            Column {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("搜索动作") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                ) {
                    items(filteredExercises, key = { it.id }) { exercise ->
                        TextButton(
                            onClick = {
                                onSelect(exercise)
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = exercise.name,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        dismissButton = null,
    )
}
