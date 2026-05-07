package com.example.myfitness.data.repository

import com.example.myfitness.data.local.dao.DailyCheckInDao
import com.example.myfitness.data.local.entity.DailyCheckInEntity
import com.example.myfitness.domain.model.DailyCheckIn
import com.example.myfitness.domain.model.ExerciseEntry
import com.example.myfitness.domain.model.WorkoutSet
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class WorkoutRepositoryImplTest {

    private lateinit var fakeDao: FakeDailyCheckInDao
    private lateinit var repository: WorkoutRepositoryImpl

    @Before
    fun setUp() {
        fakeDao = FakeDailyCheckInDao()
        repository = WorkoutRepositoryImpl(fakeDao)
    }

    @Test
    fun `getSessions returns empty list when no data`() = runTest {
        assertEquals(emptyList<DailyCheckIn>(), repository.getSessions())
    }

    @Test
    fun `getSessions returns mapped domain models sorted by date descending`() = runTest {
        val entity1 = DailyCheckInEntity(
            id = 1L,
            date = "2026-05-05",
            content = """
                ## 卧推
                - 80.0 x 8
            """.trimIndent(),
        )
        val entity2 = DailyCheckInEntity(
            id = 2L,
            date = "2026-05-07",
            content = """
                ## 深蹲
                - 100.0 x 5
            """.trimIndent(),
        )
        fakeDao.insert(entity1)
        fakeDao.insert(entity2)

        val result = repository.getSessions()

        assertEquals(2, result.size)
        // Should be sorted by date DESC
        assertEquals(LocalDate.of(2026, 5, 7), result[0].date)
        assertEquals(LocalDate.of(2026, 5, 5), result[1].date)
    }

    @Test
    fun `getSessions parses markdown content into exercises`() = runTest {
        val entity = DailyCheckInEntity(
            id = 1L,
            date = "2026-05-07",
            content = """
                ## 卧推
                - 80.0 x 8
                - 82.5 x 6
            """.trimIndent(),
        )
        fakeDao.insert(entity)

        val result = repository.getSessions()

        assertEquals(1, result.size)
        assertEquals(1, result[0].exercises.size)
        assertEquals("卧推", result[0].exercises[0].name)
        assertEquals(2, result[0].exercises[0].sets.size)
    }

    @Test
    fun `saveSession inserts entity with serialized markdown`() = runTest {
        val checkIn = DailyCheckIn(
            id = 0L,
            date = LocalDate.of(2026, 5, 7),
            exercises = listOf(
                ExerciseEntry(
                    name = "硬拉",
                    sets = listOf(WorkoutSet(120.0f, 3)),
                ),
            ),
        )

        repository.saveSession(checkIn)

        val stored = fakeDao.getAll().first()
        assertEquals("2026-05-07", stored.date)
        assertEquals(
            """
                ## 硬拉
                - 120.0 x 3
            """.trimIndent(),
            stored.content,
        )
    }

    @Test
    fun `getSessionByDate returns mapped domain model`() = runTest {
        val entity = DailyCheckInEntity(
            id = 1L,
            date = "2026-05-07",
            content = """
                ## 推举
                - 60.0 x 10
            """.trimIndent(),
        )
        fakeDao.insert(entity)

        val result = repository.getSessionByDate(LocalDate.of(2026, 5, 7))

        assertEquals(1L, result?.id)
        assertEquals(LocalDate.of(2026, 5, 7), result?.date)
        assertEquals(1, result?.exercises?.size)
        assertEquals("推举", result?.exercises?.get(0)?.name)
    }

    @Test
    fun `getSessionByDate returns null when not found`() = runTest {
        assertNull(repository.getSessionByDate(LocalDate.of(2026, 1, 1)))
    }

    @Test
    fun `importFromMarkdown inserts raw markdown without parsing`() = runTest {
        val markdown = """
            ## 引体向上
            - 0 x 10
        """.trimIndent()

        repository.importFromMarkdown(markdown, LocalDate.of(2026, 5, 8))

        val stored = fakeDao.getByDate("2026-05-08")
        assertEquals(markdown, stored?.content)
        assertEquals(0L, stored?.id)
    }

    @Test
    fun `saveSession roundtrips through getSessionByDate`() = runTest {
        val original = DailyCheckIn(
            id = 0L,
            date = LocalDate.of(2026, 5, 10),
            exercises = listOf(
                ExerciseEntry(
                    name = "卧推",
                    sets = listOf(WorkoutSet(80.0f, 8), WorkoutSet(82.5f, 6)),
                ),
                ExerciseEntry(
                    name = "深蹲",
                    sets = listOf(WorkoutSet(100.0f, 5)),
                ),
            ),
        )

        repository.saveSession(original)
        val retrieved = repository.getSessionByDate(LocalDate.of(2026, 5, 10))

        assertEquals(original.date, retrieved?.date)
        assertEquals(original.exercises, retrieved?.exercises)
    }

    @Test
    fun `getSessions handles empty markdown content`() = runTest {
        val entity = DailyCheckInEntity(
            id = 1L,
            date = "2026-05-07",
            content = "",
        )
        fakeDao.insert(entity)

        val result = repository.getSessions()

        assertEquals(1, result.size)
        assertEquals(emptyList<ExerciseEntry>(), result[0].exercises)
    }

    private class FakeDailyCheckInDao : DailyCheckInDao {
        private val storage = mutableListOf<DailyCheckInEntity>()

        override suspend fun insert(dailyCheckInEntity: DailyCheckInEntity) {
            storage.add(dailyCheckInEntity)
        }

        override suspend fun update(dailyCheckInEntity: DailyCheckInEntity) {
            val index = storage.indexOfFirst { it.id == dailyCheckInEntity.id }
            if (index != -1) {
                storage[index] = dailyCheckInEntity
            }
        }

        override suspend fun delete(dailyCheckInEntity: DailyCheckInEntity) {
            storage.removeAll { it.id == dailyCheckInEntity.id }
        }

        override suspend fun getByDate(date: String): DailyCheckInEntity? {
            return storage.find { it.date == date }
        }

        override suspend fun getAll(): List<DailyCheckInEntity> {
            return storage.sortedByDescending { it.date }
        }
    }
}
