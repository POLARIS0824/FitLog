package com.example.fitlog.feature.stats

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.fitlog.data.local.AppDatabase
import com.example.fitlog.data.repository.BodyMetricRepository
import com.example.fitlog.data.repository.WorkoutRepository
import com.example.fitlog.model.BodyMetric
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/**
 * [StatsViewModel] 的单元测试。
 *
 * 使用 Robolectric 内存 Room 数据库手工构造 Repository（不经 Hilt），
 * 验证「三流 combine → 纯函数装配」链路与体重录入弹层：
 * 周期切换重查、快速连点末次胜出、热力图独立窗口、
 * 体重提交的校验/upsert/savedTick 语义。
 *
 * 注意：ViewModel 内 `LocalDate.now()` 固定"今天"，
 * 测试夹具的日期一律用 [LocalDate.now] 构造（同 TodayViewModelTest 约定）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class StatsViewModelTest {

    private lateinit var db: AppDatabase
    private lateinit var workoutRepository: WorkoutRepository
    private lateinit var bodyMetricRepository: BodyMetricRepository

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
        bodyMetricRepository = BodyMetricRepository(db.bodyMetricDao())
    }

    /**
     * 重置主调度器并关闭数据库。
     */
    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun createViewModel() = StatsViewModel(
        workoutRepository = workoutRepository,
        bodyMetricRepository = bodyMetricRepository,
    )

    /**
     * 测试初始暴露的状态为加载中（stateIn initialValue）。
     */
    @Test
    fun testInitialState_isLoading() = runTest {
        val viewModel = createViewModel()
        assertTrue(viewModel.uiState.value.isLoading)
    }

    /**
     * 测试空库：默认 WEEK 档，7 个日桶，各区降级为零值/空态。
     */
    @Test
    fun testEmptyDatabase_allSectionsDegrade() = runTest {
        val viewModel = createViewModel()

        val state = viewModel.uiState.first { !it.isLoading }

        assertEquals(StatsPeriod.WEEK, state.period)
        assertEquals(7, state.chart.chartData.entries.size)
        assertFalse(state.chart.hasData)
        assertEquals(4, state.overview.items.size)
        assertEquals("0 次", state.overview.items[0].valueText)
        assertEquals(0, state.heatmap.trainedDays)
        assertTrue(state.heatmap.values.isEmpty())
        assertFalse(state.weight.hasData)
        assertEquals("暂无记录", state.weight.valueText)
    }

    /**
     * 测试周期切换重查区间：20 天前的训练在 WEEK 档不可见，切到 MONTH 档出现。
     */
    @Test
    fun testPeriodSwitch_requeriesWiderRange() = runTest {
        workoutRepository.insert(workout(today, 800f))
        workoutRepository.insert(workout(today.minusDays(20), 500f))
        val viewModel = createViewModel()

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
     * 测试概览随档位联动：WEEK 档只计 7 天内的次数/组数。
     */
    @Test
    fun testOverview_followsPeriodWindow() = runTest {
        workoutRepository.insert(workout(today, 800f))
        workoutRepository.insert(workout(today.minusDays(20), 500f))
        val viewModel = createViewModel()

        val weekState = viewModel.uiState.first { !it.isLoading }
        assertEquals("1 次", weekState.overview.items[0].valueText)
        assertEquals("1 组", weekState.overview.items[3].valueText)

        viewModel.onPeriodSelected(StatsPeriod.MONTH)

        val monthState = viewModel.uiState.first { !it.isLoading && it.period == StatsPeriod.MONTH }
        assertEquals("2 次", monthState.overview.items[0].valueText)
        assertEquals("2 组", monthState.overview.items[3].valueText)
    }

    /**
     * 测试热力图独立窗口：200 天前的训练进热力图，但不进 WEEK 档图表。
     */
    @Test
    fun testHeatmap_independentYearWindow() = runTest {
        workoutRepository.insert(workout(today.minusDays(200), 600f))
        val viewModel = createViewModel()

        val state = viewModel.uiState.first { !it.isLoading }

        assertTrue(state.chart.chartData.entries.none { it.value == 600f })
        assertEquals(1, state.heatmap.trainedDays)
        assertEquals(600f, state.heatmap.values[today.minusDays(200)] ?: 0f, 0.01f)
    }

    /**
     * 测试快速连点切换：flatMapLatest 取消旧查询，末次档位胜出。
     */
    @Test
    fun testRapidPeriodSwitch_lastPeriodWins() = runTest {
        val viewModel = createViewModel()
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
        val viewModel = createViewModel()

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

    // ── 体重录入弹层 ──

    /**
     * 测试合法提交：写入 body_metrics、清空输入、savedTick +1。
     */
    @Test
    fun testWeightSubmit_valid_persistsAndTicks() = runTest {
        val viewModel = createViewModel()

        viewModel.onWeightInputChange("74.5")
        viewModel.onWeightSubmit()

        val sheet = viewModel.weightSheetState.first { it.savedTick == 1 }
        assertEquals("", sheet.input)
        assertNull(sheet.error)
        assertEquals(74.5f, db.bodyMetricDao().getByDateRange(today, today).first().single().weightKg)
    }

    /**
     * 测试非数字输入：error 提示，不写库、savedTick 不变。
     */
    @Test
    fun testWeightSubmit_notANumber_errorWithoutPersist() = runTest {
        val viewModel = createViewModel()

        viewModel.onWeightInputChange("abc")
        viewModel.onWeightSubmit()

        val sheet = viewModel.weightSheetState.value
        assertEquals("请输入数字", sheet.error)
        assertEquals(0, sheet.savedTick)
        assertTrue(db.bodyMetricDao().getByDateRange(today, today).first().isEmpty())
    }

    /**
     * 测试超范围输入：范围 error，不写库。
     */
    @Test
    fun testWeightSubmit_outOfRange_errorWithoutPersist() = runTest {
        val viewModel = createViewModel()

        viewModel.onWeightInputChange("500")
        viewModel.onWeightSubmit()

        val sheet = viewModel.weightSheetState.value
        assertEquals("体重需在 20–300 kg 之间", sheet.error)
        assertEquals(0, sheet.savedTick)
        assertTrue(db.bodyMetricDao().getByDateRange(today, today).first().isEmpty())
    }

    /**
     * 测试同日两次提交：按天 upsert——仅 1 行且为新值，savedTick 累加。
     */
    @Test
    fun testWeightSubmit_twiceSameDay_upserts() = runTest {
        val viewModel = createViewModel()

        viewModel.onWeightInputChange("74.5")
        viewModel.onWeightSubmit()
        viewModel.weightSheetState.first { it.savedTick == 1 }

        viewModel.onWeightInputChange("75.0")
        viewModel.onWeightSubmit()
        viewModel.weightSheetState.first { it.savedTick == 2 }

        val rows = db.bodyMetricDao().getByDateRange(today, today).first()
        assertEquals(1, rows.size)
        assertEquals(75.0f, rows.single().weightKg)
    }

    /**
     * 测试打开弹层预填：已有今日记录时输入框带当前值。
     */
    @Test
    fun testWeightSheetOpened_prefillsExisting() = runTest {
        bodyMetricRepository.upsert(BodyMetric(date = today, weightKg = 74.5f))
        val viewModel = createViewModel()

        viewModel.onWeightSheetOpened()

        // todayMetric 是 stateIn 冷启动流，等预填到达
        val sheet = viewModel.weightSheetState.first { it.input.isNotEmpty() }
        assertEquals("74.5", sheet.input)
    }

    /**
     * 测试关闭弹层：清空输入与错误，savedTick 保留单调性。
     */
    @Test
    fun testWeightSheetDismissed_clearsFormKeepsTick() = runTest {
        val viewModel = createViewModel()

        viewModel.onWeightInputChange("74.5")
        viewModel.onWeightSubmit()
        viewModel.weightSheetState.first { it.savedTick == 1 }

        viewModel.onWeightInputChange("80")
        viewModel.onWeightSheetDismissed()

        val sheet = viewModel.weightSheetState.value
        assertEquals("", sheet.input)
        assertNull(sheet.error)
        assertEquals(1, sheet.savedTick)
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
        // 夹具为"已结束"训练：isCountable 口径要求 endedAt 非空
        startedAt = 0L,
        endedAt = 3_600_000L,
    )
}
