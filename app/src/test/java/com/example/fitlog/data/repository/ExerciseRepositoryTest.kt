package com.example.fitlog.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.fitlog.data.local.AppDatabase
import com.example.fitlog.model.BodyPart
import com.example.fitlog.model.Equipment
import com.example.fitlog.model.Exercise
import com.example.fitlog.model.Muscle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 动作库仓库 [ExerciseRepository] 的单元测试。
 *
 * 使用 Robolectric 在 JVM 环境下进行各种查询、筛选和搜索动作的逻辑验证。
 */
@RunWith(RobolectricTestRunner::class)
class ExerciseRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: ExerciseRepository

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ExerciseRepository(db.exerciseDao())
    }

    @After
    fun closeDb() {
        db.close()
    }

    // ── 基础 CRUD ──

    @Test
    fun `insert and getById returns correct exercise`() = runTest {
        val exercise = createExercise(
            id = "barbell-bench-press",
            name = "Barbell Bench Press",
            primaryMuscles = listOf(Muscle.CHEST),
            secondaryMuscles = listOf(Muscle.SHOULDERS, Muscle.TRICEPS),
            bodyPart = BodyPart.CHEST,
        )

        repository.insert(exercise)

        val fetched = repository.getById("barbell-bench-press")
        assertNotNull(fetched)
        assertEquals("Barbell Bench Press", fetched?.name)
        assertEquals(listOf(Muscle.CHEST), fetched?.primaryMuscles)
        assertEquals(listOf(Muscle.SHOULDERS, Muscle.TRICEPS), fetched?.secondaryMuscles)
        assertEquals(Equipment.BARBELL, fetched?.equipment)
        assertEquals(BodyPart.CHEST, fetched?.bodyPart)
    }

    @Test
    fun `update modifies existing exercise`() = runTest {
        val exercise = createExercise(id = "barbell-bench-press", name = "Barbell Bench Press")
        repository.insert(exercise)

        val updated = exercise.copy(name = "Barbell Flat Bench Press", description = "更新说明")
        repository.update(updated)

        val fetched = repository.getById("barbell-bench-press")
        assertEquals("Barbell Flat Bench Press", fetched?.name)
        assertEquals("更新说明", fetched?.description)
    }

    @Test
    fun `delete removes exercise`() = runTest {
        val exercise = createExercise(id = "barbell-bench-press")
        repository.insert(exercise)

        repository.delete(exercise)

        val fetched = repository.getById("barbell-bench-press")
        assertNull(fetched)
    }

    // ── 批量插入 ──

    @Test
    fun `insertAll inserts multiple exercises`() = runTest {
        val exercises = listOf(
            createExercise(id = "ex-1", name = "Exercise 1"),
            createExercise(id = "ex-2", name = "Exercise 2"),
            createExercise(id = "ex-3", name = "Exercise 3"),
        )

        repository.insertAll(exercises)

        assertEquals(3, repository.getCount())
    }

    // ── 筛选 ──

    @Test
    fun `getByBodyPart returns matching exercises`() = runTest {
        repository.insert(createExercise(id = "ex-chest", bodyPart = BodyPart.CHEST))
        repository.insert(createExercise(id = "ex-back", bodyPart = BodyPart.BACK))
        repository.insert(createExercise(id = "ex-legs", bodyPart = BodyPart.UPPER_LEGS))

        val chestExercises = repository.getByBodyPart("CHEST")
        assertEquals(1, chestExercises.size)
        assertEquals("ex-chest", chestExercises[0].id)

        val all = repository.getAll()
        assertEquals(3, all.size)
    }

    @Test
    fun `getByMuscle returns exercises with matching primary muscle`() = runTest {
        repository.insert(createExercise(
            id = "bench-press",
            primaryMuscles = listOf(Muscle.CHEST),
        ))
        repository.insert(createExercise(
            id = "squat",
            primaryMuscles = listOf(Muscle.QUADRICEPS),
        ))
        repository.insert(createExercise(
            id = "deadlift",
            primaryMuscles = listOf(Muscle.HAMSTRINGS, Muscle.LOWER_BACK),
        ))

        val chestExercises = repository.getByMuscle("CHEST")
        assertEquals(1, chestExercises.size)
        assertEquals("bench-press", chestExercises[0].id)

        val hamstringExercises = repository.getByMuscle("HAMSTRINGS")
        assertEquals(1, hamstringExercises.size)
        assertEquals("deadlift", hamstringExercises[0].id)
    }

    @Test
    fun `getCustomExercises returns only custom`() = runTest {
        repository.insert(createExercise(id = "builtin", isCustom = false))
        repository.insert(createExercise(id = "custom", isCustom = true))

        val customs = repository.getCustomExercises()
        assertEquals(1, customs.size)
        assertEquals("custom", customs[0].id)
    }

    // ── 搜索 ──

    @Test
    fun `getByName returns exact match`() = runTest {
        repository.insert(createExercise(id = "db-press", name = "Dumbbell Press"))

        val exact = repository.getByName("Dumbbell Press")
        assertNotNull(exact)
        assertEquals("db-press", exact?.id)
    }

    @Test
    fun `searchByName returns fuzzy matches`() = runTest {
        repository.insert(createExercise(id = "ex-1", name = "Dumbbell Press"))
        repository.insert(createExercise(id = "ex-2", name = "Dumbbell Lateral Raise"))
        repository.insert(createExercise(id = "ex-3", name = "Barbell Squat"))

        val results = repository.searchByName("Dumbbell")
        assertEquals(2, results.size)
    }

    // ── 计数 ──

    @Test
    fun `getCount returns total exercises`() = runTest {
        assertEquals(0, repository.getCount())

        repository.insert(createExercise(id = "ex-1"))
        assertEquals(1, repository.getCount())

        repository.insert(createExercise(id = "ex-2"))
        assertEquals(2, repository.getCount())
    }

    // ── 新字段 ──

    @Test
    fun `imageUrl and gifUrl are persisted`() = runTest {
        val exercise = createExercise(
            id = "bench-press",
            imageUrl = "0001-2gPfomN.jpg",
            gifUrl = "https://example.com/0001-2gPfomN.gif",
        )
        repository.insert(exercise)

        val fetched = repository.getById("bench-press")
        assertEquals("0001-2gPfomN.jpg", fetched?.imageUrl)
        assertEquals("https://example.com/0001-2gPfomN.gif", fetched?.gifUrl)
    }

    // ── 辅助方法 ──

    private fun createExercise(
        id: String = "test-exercise",
        name: String = "Test Exercise",
        primaryMuscles: List<Muscle> = listOf(Muscle.CHEST),
        secondaryMuscles: List<Muscle> = emptyList(),
        bodyPart: BodyPart = BodyPart.CHEST,
        isCustom: Boolean = false,
        imageUrl: String? = null,
        gifUrl: String? = null,
    ) = Exercise(
        id = id,
        name = name,
        primaryMuscles = primaryMuscles,
        secondaryMuscles = secondaryMuscles,
        bodyPart = bodyPart,
        isCustom = isCustom,
        isCompound = primaryMuscles.size + secondaryMuscles.size >= 3,
        equipment = Equipment.BARBELL,
        imageUrl = imageUrl,
        gifUrl = gifUrl,
    )
}
