package com.example.fitlog.feature.workout

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.fitlog.data.local.AppDatabase
import com.example.fitlog.data.repository.WorkoutRepository
import com.example.fitlog.model.Workout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/**
 * [WorkoutViewModel] 的单元测试。
 * 使用 Robolectric 和内存 Room 数据库验证 ViewModel 状态流转（Loading -> Success）以及 UI 事件交互。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class WorkoutViewModelTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: WorkoutRepository
    private lateinit var viewModel: WorkoutViewModel

    private val testDispatcher = UnconfinedTestDispatcher()

    /**
     * 设置单元测试的协程主调度器，并初始化数据库 and ViewModel 实例。
     */
    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        repository = WorkoutRepository(
            workoutDao = db.workoutDao(),
            exerciseLogDao = db.exerciseLogDao(),
            setLogDao = db.setLogDao(),
            db = db,
        )

        viewModel = WorkoutViewModel(repository)
    }

    /**
     * 重置主调度器并关闭数据库。
     */
    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    /**
     * 测试刚初始化时的初始状态。
     */
    @Test
    fun testInitialState_isSuccessWithEmptyList() = runTest {
        // 使用 first { ... } 等待 Flow 监听到数据库的首次空列表 Success 状态
        val state = viewModel.uiState.first { it is WorkoutUiState.Success }
        assertTrue(state is WorkoutUiState.Success)
        assertEquals(0, (state as WorkoutUiState.Success).workouts.size)
    }

    /**
     * 测试插入一条训练日志时，UI 状态能够自动更新并捕获到成功状态。
     */
    @Test
    fun testInsertWorkout_updatesUiStateToSuccess() = runTest {
        val workout = Workout(
            id = 100L,
            userId = 0L,
            date = LocalDate.of(2026, 5, 20),
            exercises = emptyList(),
            feelings = "好极了",
            sourceFileName = "2026-05-20.md"
        )

        viewModel.insertWorkout(workout)

        // 使用 first { ... } 等待状态流转至包含 1 条记录的 Success 状态
        val state = viewModel.uiState.first {
            it is WorkoutUiState.Success && it.workouts.size == 1
        } as WorkoutUiState.Success

        val list = state.workouts
        assertEquals(1, list.size)
        assertEquals(100L, list[0].id)
        assertEquals("好极了", list[0].feelings)
    }

    /**
     * 测试删除日志时，UI 状态能够即时同步更新。
     */
    @Test
    fun testDeleteWorkout_updatesUiStateToEmpty() = runTest {
        val workout = Workout(
            id = 100L,
            userId = 0L,
            date = LocalDate.of(2026, 5, 20),
            exercises = emptyList(),
            feelings = "好极了",
            sourceFileName = "2026-05-20.md"
        )

        // 先插入并等待其成功同步
        viewModel.insertWorkout(workout)
        val stateAfterInsert = viewModel.uiState.first {
            it is WorkoutUiState.Success && it.workouts.size == 1
        } as WorkoutUiState.Success
        assertEquals(1, stateAfterInsert.workouts.size)

        // 后删除并等待数据重新变空
        viewModel.deleteWorkout(workout)
        val stateAfterDelete = viewModel.uiState.first {
            it is WorkoutUiState.Success && it.workouts.isEmpty()
        } as WorkoutUiState.Success
        assertEquals(0, stateAfterDelete.workouts.size)
    }
}
