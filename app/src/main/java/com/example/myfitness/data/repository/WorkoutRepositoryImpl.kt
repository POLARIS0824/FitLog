package com.example.myfitness.data.repository

import com.example.myfitness.data.local.dao.DailyCheckInDao
import com.example.myfitness.data.local.entity.DailyCheckInEntity
import com.example.myfitness.data.markdown.MarkdownParser
import com.example.myfitness.domain.model.DailyCheckIn
import com.example.myfitness.domain.repository.WorkoutRepository
import java.time.LocalDate
import javax.inject.Inject

/**
 * [WorkoutRepository] 的 Room 实现。
 * 训练记录以 Markdown 文本存储，通过 [MarkdownParser] 进行结构化转换。
 */
class WorkoutRepositoryImpl @Inject constructor(
    private val dailyCheckInDao: DailyCheckInDao,
) : WorkoutRepository {

    /**
     * 获取所有训练记录。
     *
     * @return [DailyCheckIn] 列表，按日期降序排列
     */
    override suspend fun getSessions(): List<DailyCheckIn> {
        return dailyCheckInDao.getAll().map { it.toDomain() }
    }

    /**
     * 保存一条训练记录。
     *
     * @param checkIn 待保存的训练记录
     */
    override suspend fun saveSession(checkIn: DailyCheckIn) {
        dailyCheckInDao.insert(checkIn.toEntity())
    }

    /**
     * 根据日期查询训练记录。
     *
     * @param date 训练日期
     * @return 匹配的训练记录，若不存在则返回 null
     */
    override suspend fun getSessionByDate(date: LocalDate): DailyCheckIn? {
        return dailyCheckInDao.getByDate(date.toString())?.toDomain()
    }

    /**
     * 从外部 Markdown 文本导入训练记录。
     *
     * @param content Markdown 格式的训练日志
     * @param date 训练日期
     */
    override suspend fun importFromMarkdown(content: String, date: LocalDate) {
        val entity = DailyCheckInEntity(
            id = 0L,
            date = date.toString(),
            content = content,
        )
        dailyCheckInDao.insert(entity)
    }

    /**
     * 将 [DailyCheckInEntity] 转换为领域模型 [DailyCheckIn]。
     */
    private fun DailyCheckInEntity.toDomain(): DailyCheckIn {
        return DailyCheckIn(
            id = id,
            date = LocalDate.parse(date),
            exercises = MarkdownParser.parse(content),
        )
    }

    /**
     * 将领域模型 [DailyCheckIn] 转换为数据库实体 [DailyCheckInEntity]。
     */
    private fun DailyCheckIn.toEntity(): DailyCheckInEntity {
        return DailyCheckInEntity(
            id = id,
            date = date.toString(),
            content = MarkdownParser.serialize(exercises),
        )
    }
}
