package com.example.fitlog.ui.settings.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitlog.data.repository.UserProfileRepository
import com.example.fitlog.model.user.Gender
import com.example.fitlog.model.user.TrainingGoal
import com.example.fitlog.model.user.TrainingLevel
import com.example.fitlog.model.user.UserProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/**
 * 个人资料页 ViewModel。
 *
 * 单用户 App：启动时通过 [UserProfileRepository.getFirst] 直接挂起查询回填表单
 * （不读 uiState.value，避免 combine 首发射时延导致的竞态），
 * 保存时按是否存在已有记录区分 insert / update。
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userProfileRepository: UserProfileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    /** 已存在记录的主键；null 表示尚未保存过资料。 */
    private var existingId: Long? = null

    init {
        viewModelScope.launch {
            val profile = userProfileRepository.getFirst() ?: return@launch
            existingId = profile.id
            _uiState.update {
                it.copy(
                    name = profile.name,
                    age = profile.age?.toString().orEmpty(),
                    gender = profile.gender,
                    height = profile.height?.toString().orEmpty(),
                    weight = profile.weight?.toString().orEmpty(),
                    goal = profile.trainingGoal,
                )
            }
        }
    }

    fun onNameChange(value: String) = _uiState.update { it.copy(name = value) }
    fun onAgeChange(value: String) = _uiState.update { it.copy(age = value) }
    fun onGenderChange(value: Gender) = _uiState.update { it.copy(gender = value) }
    fun onHeightChange(value: String) = _uiState.update { it.copy(height = value) }
    fun onWeightChange(value: String) = _uiState.update { it.copy(weight = value) }
    fun onGoalChange(value: TrainingGoal) = _uiState.update { it.copy(goal = value) }

    /** 保存资料：姓名为必填，其余字段留空则存 null。 */
    fun onSave() {
        val state = _uiState.value
        if (state.name.isBlank()) {
            _uiState.update { it.copy(errorMessage = "请填写姓名") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val profile = UserProfile(
                    id = existingId ?: 0,
                    name = state.name.trim(),
                    age = state.age.trim().toIntOrNull(),
                    gender = state.gender,
                    height = state.height.trim().toFloatOrNull(),
                    weight = state.weight.trim().toFloatOrNull(),
                    trainingLevel = TrainingLevel(emptyMap()),
                    trainingGoal = state.goal,
                )
                if (existingId != null) {
                    userProfileRepository.update(profile)
                } else {
                    userProfileRepository.insert(profile)
                }
                _uiState.update { it.copy(isSaving = false, successMessage = "个人资料已保存") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, errorMessage = e.message ?: "保存失败") }
            }
        }
    }

    /** 成功提示已展示，清除一次性状态。 */
    fun onSuccessShown() = _uiState.update { it.copy(successMessage = null) }

    /** 错误提示已展示，清除一次性状态。 */
    fun onErrorShown() = _uiState.update { it.copy(errorMessage = null) }
}
