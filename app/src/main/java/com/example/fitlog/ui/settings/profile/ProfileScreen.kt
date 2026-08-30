package com.example.fitlog.ui.settings.profile

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fitlog.model.user.Gender
import com.example.fitlog.model.user.TrainingGoal
import com.example.fitlog.ui.components.CollapsingTitleScaffold
import com.example.fitlog.ui.components.FitLogCard
import com.example.fitlog.ui.components.SectionLabel
import com.example.fitlog.ui.components.StackedSnackbarHost
import com.example.fitlog.ui.components.rememberStackedSnackbarHostState

/**
 * 1. 容器层 (Stateful)
 */
@Composable
fun ProfileRoute(
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ProfileScreen(
        uiState = uiState,
        onBack = onBack,
        onNameChange = viewModel::onNameChange,
        onAgeChange = viewModel::onAgeChange,
        onGenderChange = viewModel::onGenderChange,
        onHeightChange = viewModel::onHeightChange,
        onWeightChange = viewModel::onWeightChange,
        onGoalChange = viewModel::onGoalChange,
        onSave = viewModel::onSave,
        onSuccessShown = viewModel::onSuccessShown,
        onErrorShown = viewModel::onErrorShown,
        modifier = modifier,
    )
}

/**
 * 2. 纯 UI 展示层 (Stateless)
 *
 * 动态双标题交互契约见 [CollapsingTitleScaffold]。
 */
@Composable
fun ProfileScreen(
    uiState: ProfileUiState,
    onBack: () -> Unit,
    onNameChange: (String) -> Unit,
    onAgeChange: (String) -> Unit,
    onGenderChange: (Gender) -> Unit,
    onHeightChange: (String) -> Unit,
    onWeightChange: (String) -> Unit,
    onGoalChange: (TrainingGoal) -> Unit,
    onSave: () -> Unit,
    onSuccessShown: () -> Unit,
    onErrorShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val stackedSnackbarHostState = rememberStackedSnackbarHostState()

    CollapsingTitleScaffold(
        title = "Personal Info",
        onBack = onBack,
        parentTitle = "Settings",
        modifier = modifier,
        snackbarHost = { StackedSnackbarHost(hostState = stackedSnackbarHostState) },
        // 表单页特例：软键盘避让 + 点击空白处收起键盘
        contentColumnModifier = Modifier
            .imePadding()
            .pointerInput(Unit) {
                detectTapGestures(onTap = { focusManager.clearFocus() })
            },
    ) {
        SectionLabel("基本信息")
        FitLogCard {
            OutlinedTextField(
                value = uiState.name,
                onValueChange = onNameChange,
                label = { Text("姓名") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = uiState.age,
                onValueChange = onAgeChange,
                label = { Text("年龄") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "性别",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val genders = listOf(
                Gender.MALE to "男",
                Gender.FEMALE to "女",
                Gender.OTHER to "其他",
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                genders.forEachIndexed { index, (gender, label) ->
                    SegmentedButton(
                        selected = uiState.gender == gender,
                        onClick = { onGenderChange(gender) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = genders.size,
                        ),
                    ) {
                        Text(label)
                    }
                }
            }
        }

        SectionLabel("身体数据")
        FitLogCard {
            OutlinedTextField(
                value = uiState.height,
                onValueChange = onHeightChange,
                label = { Text("身高 (cm)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = uiState.weight,
                onValueChange = onWeightChange,
                label = { Text("体重 (kg)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        SectionLabel("训练目标")
        FitLogCard {
            val goals = listOf(
                TrainingGoal.HYPERTROPHY to "增肌",
                TrainingGoal.FATLOSS to "减脂",
                TrainingGoal.STRENGTH to "力量",
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                goals.forEachIndexed { index, (goal, label) ->
                    SegmentedButton(
                        selected = uiState.goal == goal,
                        onClick = { onGoalChange(goal) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = goals.size,
                        ),
                    ) {
                        Text(label)
                    }
                }
            }
        }

        Button(
            onClick = onSave,
            enabled = uiState.name.isNotBlank() && !uiState.isSaving,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        ) {
            Text("保存")
        }
        Spacer(modifier = Modifier.height(16.dp))
    }

    // 保存成功提示
    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let {
            stackedSnackbarHostState.showSnackbar(it)
            onSuccessShown()
        }
    }

    // 错误提示
    uiState.errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = onErrorShown,
            confirmButton = { TextButton(onClick = onErrorShown) { Text("知道了") } },
            title = { Text("出错了") },
            text = { Text(message) },
        )
    }
}

/**
 * 3. 预览层
 */
@Preview(showBackground = true)
@Composable
private fun ProfileScreenPreview() {
    ProfileScreen(
        uiState = ProfileUiState(name = "Polaris", goal = TrainingGoal.HYPERTROPHY),
        onBack = {},
        onNameChange = {},
        onAgeChange = {},
        onGenderChange = {},
        onHeightChange = {},
        onWeightChange = {},
        onGoalChange = {},
        onSave = {},
        onSuccessShown = {},
        onErrorShown = {},
    )
}
