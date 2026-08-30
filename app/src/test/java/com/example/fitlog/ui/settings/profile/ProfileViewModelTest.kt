package com.example.fitlog.ui.settings.profile

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.fitlog.data.local.AppDatabase
import com.example.fitlog.data.repository.UserProfileRepository
import com.example.fitlog.model.user.Gender
import com.example.fitlog.model.user.TrainingGoal
import com.example.fitlog.model.user.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [ProfileViewModel] 的单元测试。
 *
 * 使用真实 [UserProfileRepository] + 内存 Room，
 * 验证表单回填、保存校验（姓名必填）、数值解析容错、
 * insert/update 分支选择与一次性提示状态。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ProfileViewModelTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: UserProfileRepository
    private lateinit var viewModel: ProfileViewModel

    /**
     * 设置主调度器，初始化数据库、仓库与 ViewModel。
     */
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = UserProfileRepository(db.userProfileDao())
        viewModel = ProfileViewModel(repository)
    }

    /**
     * 重置主调度器并关闭数据库。
     */
    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    private fun profile(
        id: Long = 0,
        name: String = "张三",
        age: Int? = 25,
        gender: Gender? = Gender.MALE,
        height: Float? = 175f,
        weight: Float? = 70f,
        goal: TrainingGoal? = TrainingGoal.STRENGTH,
    ) = UserProfile(
        id = id,
        name = name,
        age = age,
        gender = gender,
        height = height,
        weight = weight,
        trainingGoal = goal,
    )

    /**
     * 测试表单输入事件更新对应字段。
     */
    @Test
    fun testFormInputEvents_updateState() = runTest {
        viewModel.onNameChange("张三")
        viewModel.onAgeChange("25")
        viewModel.onGenderChange(Gender.FEMALE)
        viewModel.onHeightChange("165.5")
        viewModel.onWeightChange("55")
        viewModel.onGoalChange(TrainingGoal.HYPERTROPHY)

        val state = viewModel.uiState.value
        assertEquals("张三", state.name)
        assertEquals("25", state.age)
        assertEquals(Gender.FEMALE, state.gender)
        assertEquals("165.5", state.height)
        assertEquals("55", state.weight)
        assertEquals(TrainingGoal.HYPERTROPHY, state.goal)
    }

    /**
     * 测试姓名为空白时保存被拦截：提示"请填写姓名"，且不写数据库。
     */
    @Test
    fun testOnSave_blankName_showsErrorAndSkipsDatabase() = runTest {
        viewModel.onNameChange("   ")
        viewModel.onSave()

        assertEquals("请填写姓名", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isSaving)
        assertNull(repository.getFirst())
    }

    /**
     * 测试首次保存走 insert 分支：写入数据库并弹出成功提示，姓名去首尾空格。
     */
    @Test
    fun testOnSave_newProfile_insertsAndShowsSuccess() = runTest {
        viewModel.onNameChange("  张三  ")
        viewModel.onAgeChange("25")
        viewModel.onGenderChange(Gender.MALE)
        viewModel.onHeightChange("175.5")
        viewModel.onWeightChange("70")
        viewModel.onGoalChange(TrainingGoal.STRENGTH)

        viewModel.onSave()

        val state = viewModel.uiState.first { it.successMessage != null }
        assertEquals("个人资料已保存", state.successMessage)
        assertFalse(state.isSaving)

        val saved = repository.getFirst()
        assertNotNull(saved)
        assertEquals("张三", saved?.name)
        assertEquals(25, saved?.age)
        assertEquals(Gender.MALE, saved?.gender)
        assertEquals(175.5f, saved?.height)
        assertEquals(70f, saved?.weight)
        assertEquals(TrainingGoal.STRENGTH, saved?.trainingGoal)
    }

    /**
     * 测试数值字段输入非法文本时保存被阻断：提示校验错误，不写数据库
     * （旧实现静默转 null 并提示"保存成功"，数据悄悄丢失）。
     */
    @Test
    fun testOnSave_invalidNumericInput_blockedWithError() = runTest {
        viewModel.onNameChange("张三")
        viewModel.onAgeChange("abc")

        viewModel.onSave()

        assertEquals("年龄需为 1–120 的整数", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isSaving)
        assertNull(repository.getFirst())
    }

    /**
     * 测试越界数值被阻断：年龄/身高/体重超出合理范围时不允许保存。
     */
    @Test
    fun testOnSave_outOfRangeValues_blocked() = runTest {
        viewModel.onNameChange("张三")
        viewModel.onAgeChange("300")
        viewModel.onSave()
        assertEquals("年龄需为 1–120 的整数", viewModel.uiState.value.errorMessage)

        viewModel.onAgeChange("")
        viewModel.onHeightChange("300")
        viewModel.onSave()
        assertEquals("身高需为 50–250 cm 的数字", viewModel.uiState.value.errorMessage)

        viewModel.onHeightChange("")
        viewModel.onWeightChange("10")
        viewModel.onSave()
        assertEquals("体重需为 20–400 kg 的数字", viewModel.uiState.value.errorMessage)
        assertNull(repository.getFirst())
    }

    /**
     * 测试非有限数值（NaN/Infinity）与小数逗号输入被阻断，不写入脏数据。
     * `toFloatOrNull` 对 "NaN"/"Infinity" 会解析出非有限值，必须显式拒绝。
     */
    @Test
    fun testOnSave_nonFiniteAndCommaDecimal_blocked() = runTest {
        viewModel.onNameChange("张三")
        viewModel.onHeightChange("NaN")
        viewModel.onSave()
        assertEquals("身高需为 50–250 cm 的数字", viewModel.uiState.value.errorMessage)

        viewModel.onHeightChange("Infinity")
        viewModel.onSave()
        assertEquals("身高需为 50–250 cm 的数字", viewModel.uiState.value.errorMessage)

        // 欧式小数逗号：解析失败 → 阻断并提示，而非静默丢字段
        viewModel.onHeightChange("175,5")
        viewModel.onSave()
        assertEquals("身高需为 50–250 cm 的数字", viewModel.uiState.value.errorMessage)
        assertNull(repository.getFirst())
    }

    /**
     * 测试数值字段留空仍按原设计存 null（"未填写"是合法语义）。
     */
    @Test
    fun testOnSave_blankNumericFields_storedAsNull() = runTest {
        viewModel.onNameChange("张三")

        viewModel.onSave()
        viewModel.uiState.first { it.successMessage != null }

        val saved = repository.getFirst()
        assertNotNull(saved)
        assertNull(saved?.age)
        assertNull(saved?.height)
        assertNull(saved?.weight)
    }

    /**
     * 测试已存在资料时，ViewModel 初始化自动回填表单。
     */
    @Test
    fun testInit_existingProfile_prefillsForm() = runTest {
        repository.insert(profile())

        val vm = ProfileViewModel(repository)
        val state = vm.uiState.first { it.name.isNotEmpty() }

        assertEquals("张三", state.name)
        assertEquals("25", state.age)
        assertEquals(Gender.MALE, state.gender)
        assertEquals("175.0", state.height)
        assertEquals("70.0", state.weight)
        assertEquals(TrainingGoal.STRENGTH, state.goal)
    }

    /**
     * 测试已有资料时保存走 update 分支：更新原记录而非新增。
     */
    @Test
    fun testOnSave_existingProfile_updatesInsteadOfInsert() = runTest {
        repository.insert(profile())
        val existingId = repository.getFirst()!!.id

        val vm = ProfileViewModel(repository)
        vm.uiState.first { it.name.isNotEmpty() } // 等待回填完成

        vm.onNameChange("李四")
        vm.onSave()
        vm.uiState.first { it.successMessage != null }

        // 原记录被更新
        assertEquals("李四", repository.getById(existingId)?.name)
        // 没有产生新记录（autoGenerate 下一条记录 id 应为 existingId + 1）
        assertNull(repository.getById(existingId + 1))
    }

    /**
     * 测试一次性提示的清除。
     */
    @Test
    fun testOneShotMessages_cleared() = runTest {
        // 错误提示
        viewModel.onSave()
        assertEquals("请填写姓名", viewModel.uiState.value.errorMessage)
        viewModel.onErrorShown()
        assertNull(viewModel.uiState.value.errorMessage)

        // 成功提示
        viewModel.onNameChange("张三")
        viewModel.onSave()
        viewModel.uiState.first { it.successMessage != null }
        viewModel.onSuccessShown()
        assertNull(viewModel.uiState.value.successMessage)
    }
}
