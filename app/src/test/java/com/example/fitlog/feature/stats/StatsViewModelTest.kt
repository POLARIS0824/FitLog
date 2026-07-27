package com.example.fitlog.feature.stats

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.fitlog.data.local.AppDatabase
import com.example.fitlog.data.repository.WorkoutRepository
import com.example.fitlog.model.ExerciseLog
import com.example.fitlog.model.SetLog
import com.example.fitlog.model.SetType
import com.example.fitlog.model.Workout
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/**
 * [StatsViewModel] 的单元测试。
 *
 * 使用 Robolectric 内存 Room 数据库手工构造 [WorkoutRepository]（不经 Hilt），
 * 验证「档位 → 区间查询 → Kotlin 聚合」链路：周期切换重查、快速连点末次胜出、
 * 同档位数据更新保持桶 id 不变（AnimatedBarChart 原地变形的前提）。
 *
 * 注意：ViewModel 内 `LocalDate.now()` 固定"今天"，
 * 测试夹具的 workout 日期一律用 [LocalDate.now] 构造（同 TodayViewModelTest 约定）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class StatsViewModelTest {

    private lateinit var db: AppDatabase
    private lateinit var workoutRepository: WorkoutRepository

    private val today: LocalDate = LocalDate.now()

    /**
     * 设置主调度器并初始化内存数据库与仓库。
     */
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        workoutRepository = WorkoutRepository(
            workoutDao = db.workoutDao(),
            exerciseLogDao = db.exerciseLogDao(),
            setLogDao = db.setLogDao(),
            db = db,
        )
    }

    /**
     * 重置主调度器并关闭数据库。
     */
    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    /**
     * 测试初始暴露的状态为加载中（stateIn initialValue）。
     */
    @Test
    fun testInitialState_isLoading() = runTest {
        val viewModel = StatsViewModel(workoutRepository)
        assertTrue(viewModel.uiState.value.isLoading)
    }

    /**
     * 测试空库：默认 WEEK 档，7 个日桶，无数据降级。
     */
    @Test
    fun testEmptyDatabase_weekDefaultWithNoData() = runTest {
        val viewModel = StatsViewModel(workoutRepository)

        val state = viewModel.uiState.first { !it.isLoading }

        assertEquals(StatsPeriod.WEEK, state.period)
        assertEquals(7, state.chart.chartData.entries.size)
        assertFalse(state.chart.hasData)
    }

    /**
     * 测试周期切换重查区间：20 天前的训练在 WEEK 档不可见，切到 MONTH 档出现。
     */
    @Test
    fun testPeriodSwitch_requeriesWiderRange() = runTest {
        workoutRepository.insert(workout(today, 800f))
        workoutRepository.insert(workout(today.minusDays(20), 500f))
        val viewModel = StatsViewModel(workoutRepository)

        val weekState = viewModel.uiState.first { !it.isLoading }
        assertEquals(800f, weekState.chart.chartData.entries.last().value, 0.01f)
        // 20 天前超出 WEEK 窗口（7 天），总量仅为今天的 800
        assertTrue(weekState.chart.chartData.entries.none { it.value == 500f })

        viewModel.onPeriodSelected(StatsPeriod.MONTH)

        val monthState = viewModel.uiState.first { !it.isLoading && it.period == StatsPeriod.MONTH }
        assertEquals(30, monthState.chart.chartData.entries.size)
        assertEquals(800f, monthState.chart.chartData.entries.last().value, 0.01f)
        val twentyDaysAgo = monthState.chart.chartData.entries
            .first { it.id == today.minusDays(20).toString() }
        assertEquals(500f, twentyDaysAgo.value, 0.01f)
    }

    /**
     * 测试快速连点切换：flatMapLatest 取消旧查询，末次档位胜出。
     */
    @Test
    fun testRapidPeriodSwitch_lastPeriodWins() = runTest {
        val viewModel = StatsViewModel(workoutRepository)
        viewModel.uiState.first { !it.isLoading }

        viewModel.onPeriodSelected(StatsPeriod.MONTH)
        viewModel.onPeriodSelected(StatsPeriod.YEAR)

        val state = viewModel.uiState.first { !it.isLoading && it.period == StatsPeriod.YEAR }
        assertEquals(12, state.chart.chartData.entries.size)
    }

    /**
     * 测试同档位数据更新：新增训练后桶 id 不变、柱值累加
     * （AnimatedBarChart 凭稳定 id 原地变形，而非进退场）。
     */
    @Test
    fun testDataUpdate_sameBucketIdWithAccumulatedValue() = runTest {
        workoutRepository.insert(workout(today, 800f))
        val viewModel = StatsViewModel(workoutRepository)

        val before = viewModel.uiState.first {
            !it.isLoading && it.chart.chartData.entries.last().value == 800f
        }
        val bucketId = before.chart.chartData.entries.last().id
        assertEquals(today.toString(), bucketId)

        workoutRepository.insert(workout(today, 800f))

        val after = viewModel.uiState.first {
            !it.isLoading && it.chart.chartData.entries.last().value == 1600f
        }
        assertEquals(bucketId, after.chart.chartData.entries.last().id)
    }

    // ── 辅助方法 ──

    /** 单动作单组正式组，重量×次数 = [volumeKg]（exerciseKey 为 null，规避动作目录外键）。 */
    private fun workout(date: LocalDate, volumeKg: Float): Workout = Workout(
        id = 0L,
        userId = 0L,
        date = date,
        exercises = listOf(
            ExerciseLog(
                name = "测试动作",
                sets = listOf(
                    SetLog(
                        weightKg = volumeKg / 10,
                        reps = 10,
                        setType = SetType.WORKING,
                    ),
                ),
            ),
        ),
        feelings = null,
    )
}
