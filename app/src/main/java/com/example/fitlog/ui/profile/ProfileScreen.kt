package com.example.fitlog.ui.profile

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fitlog.model.user.Gender
import com.example.fitlog.model.user.TrainingGoal
import com.example.fitlog.ui.components.SectionLabel
import com.example.fitlog.ui.components.SettingsCard

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
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            LargeTopAppBar(
                title = { Text("个人资料") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { focusManager.clearFocus() })
                }
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionLabel("基本信息")
            SettingsCard {
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
            SettingsCard {
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
            SettingsCard {
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
    }

    // 保存成功提示
    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(it)
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
