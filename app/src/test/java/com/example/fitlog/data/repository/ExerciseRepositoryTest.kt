package com.example.fitlog.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.fitlog.data.local.AppDatabase
import com.example.fitlog.model.Difficulty
import com.example.fitlog.model.Equipment
import com.example.fitlog.model.ExerciseCategory
import com.example.fitlog.model.Force
import com.example.fitlog.model.MovementPattern
import com.example.fitlog.model.PrimaryMuscle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 动作库仓库 [ExerciseRepository] 的单元测试。
 * 使用 Robolectric 在 JVM 环境下进行各种查询、筛选和搜索动作的逻辑验证。
 */
@RunWith(RobolectricTestRunner::class)
class ExerciseRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: ExerciseRepository

    /**
     * 初始化内存 Room 数据库和 ExerciseRepository 实例。
     */
    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ExerciseRepository(db.exerciseDao())
    }

    /**
     * 测试后关闭数据库。
     */
    @After
    fun closeDb() {
        db.close()
    }

    /**
     * 测试动作的插入与根据 ID 获取。
     */
    @Test
    fun testInsertAndGetById() = runTest {
        val exercise = com.example.fitlog.model.Exercise(
            id = "barbell-bench-press",
            name = "杠铃卧推",
            primaryMuscle = PrimaryMuscle.CHEST,
            secondaryMuscles = emptyList(),
            movementPattern = MovementPattern.HORIZONTAL_PUSH,
            force = Force.PUSH,
            difficulty = Difficulty.INTERMEDIATE,
            isCompound = true,
            isCustom = false,
            equipment = Equipment.BARBELL,
            category = ExerciseCategory.STRENGTH,
            description = "胸肌训练王牌动作",
            instructions = listOf("躺在长椅上", "握住杠铃", "下放至胸口并推起")
        )

        repository.insert(exercise)

        val fetched = repository.getById("barbell-bench-press")
        assertNotNull(fetched)
        assertEquals("杠铃卧推", fetched?.name)
        assertEquals(PrimaryMuscle.CHEST, fetched?.primaryMuscle)
        assertEquals(Equipment.BARBELL, fetched?.equipment)
    }

    /**
     * 测试更新已有动作。
     */
    @Test
    fun testUpdate() = runTest {
        val exercise = com.example.fitlog.model.Exercise(
            id = "barbell-bench-press",
            name = "杠铃卧推",
            primaryMuscle = PrimaryMuscle.CHEST,
            secondaryMuscles = emptyList(),
            movementPattern = MovementPattern.HORIZONTAL_PUSH,
            force = Force.PUSH,
            difficulty = Difficulty.INTERMEDIATE,
            isCompound = true,
            isCustom = false,
            equipment = Equipment.BARBELL,
            category = ExerciseCategory.STRENGTH,
            description = "胸肌训练王牌动作",
            instructions = emptyList()
        )
        repository.insert(exercise)

        val updated = exercise.copy(name = "杠铃平板卧推", description = "更新说明")
        repository.update(updated)

        val fetched = repository.getById("barbell-bench-press")
        assertEquals("杠铃平板卧推", fetched?.name)
        assertEquals("更新说明", fetched?.description)
    }

    /**
     * 测试动作删除。
     */
    @Test
    fun testDelete() = runTest {
        val exercise = com.example.fitlog.model.Exercise(
            id = "barbell-bench-press",
            name = "杠铃卧推",
            primaryMuscle = PrimaryMuscle.CHEST,
            secondaryMuscles = emptyList(),
            movementPattern = MovementPattern.HORIZONTAL_PUSH,
            force = Force.PUSH,
            difficulty = Difficulty.INTERMEDIATE,
            isCompound = true,
            isCustom = false,
            equipment = Equipment.BARBELL,
            category = ExerciseCategory.STRENGTH,
            description = null,
            instructions = emptyList()
        )
        repository.insert(exercise)

        repository.delete(exercise)

        val fetched = repository.getById("barbell-bench-press")
        assertNull(fetched)
    }

    /**
     * 测试按分类和主肌群筛选动作。
     */
    @Test
    fun testGetByCategoryAndMuscle() = runTest {
        val chestEx = com.example.fitlog.model.Exercise(
            id = "barbell-bench-press",
            name = "杠铃卧推",
            primaryMuscle = PrimaryMuscle.CHEST,
            secondaryMuscles = emptyList(),
            movementPattern = MovementPattern.HORIZONTAL_PUSH,
            force = Force.PUSH,
            difficulty = Difficulty.INTERMEDIATE,
            isCompound = true,
            isCustom = false,
            category = ExerciseCategory.STRENGTH
        )
        val legEx = com.example.fitlog.model.Exercise(
            id = "barbell-squat",
            name = "杠铃深蹲",
            primaryMuscle = PrimaryMuscle.LEGS,
            secondaryMuscles = emptyList(),
            movementPattern = MovementPattern.SQUAT,
            force = Force.PUSH,
            difficulty = Difficulty.ADVANCED,
            isCompound = true,
            isCustom = true,
            category = ExerciseCategory.STRENGTH
        )

        repository.insert(chestEx)
        repository.insert(legEx)

        val strengths = repository.getByCategory("STRENGTH")
        assertEquals(2, strengths.size)

        val chestOnly = repository.getByPrimaryMuscle("CHEST")
        assertEquals(1, chestOnly.size)
        assertEquals("barbell-bench-press", chestOnly[0].id)

        val customs = repository.getCustomExercises()
        assertEquals(1, customs.size)
        assertEquals("barbell-squat", customs[0].id)
    }

    /**
     * 测试按名称精确和模糊搜索动作。
     */
    @Test
    fun testSearchByName() = runTest {
        val ex1 = com.example.fitlog.model.Exercise(
            id = "db-press",
            name = "哑铃推举",
            primaryMuscle = PrimaryMuscle.SHOULDERS,
            secondaryMuscles = emptyList(),
            movementPattern = MovementPattern.VERTICAL_PUSH,
            force = Force.PUSH,
            difficulty = Difficulty.BEGINNER,
            isCompound = true,
            isCustom = false,
            category = ExerciseCategory.STRENGTH
        )
        val ex2 = com.example.fitlog.model.Exercise(
            id = "db-lateral-raise",
            name = "哑铃侧平举",
            primaryMuscle = PrimaryMuscle.SHOULDERS,
            secondaryMuscles = emptyList(),
            movementPattern = MovementPattern.ISOLATION,
            force = Force.PUSH,
            difficulty = Difficulty.BEGINNER,
            isCompound = false,
            isCustom = false,
            category = ExerciseCategory.STRENGTH
        )

        repository.insert(ex1)
        repository.insert(ex2)

        val exact = repository.getByName("哑铃推举")
        assertNotNull(exact)
        assertEquals("db-press", exact?.id)

        val fuzzy = repository.searchByName("哑铃")
        assertEquals(2, fuzzy.size)
    }
}
