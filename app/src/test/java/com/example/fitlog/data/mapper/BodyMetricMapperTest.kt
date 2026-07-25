package com.example.fitlog.data.mapper

import com.example.fitlog.data.local.entity.BodyMetricEntity
import com.example.fitlog.model.BodyMetric
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * [BodyMetricMapper] 的单元测试。
 * 验证身体指标在 Entity 与领域模型间的双向映射。
 */
class BodyMetricMapperTest {

    private val date = LocalDate.of(2026, 7, 20)

    /**
     * 测试 Entity → Model → Entity 往返映射字段不变。
     */
    @Test
    fun testRoundTrip_fieldsPreserved() {
        val entity = BodyMetricEntity(date = date, weightKg = 70.5f)

        val model = entity.toModel()
        assertEquals(date, model.date)
        assertEquals(70.5f, model.weightKg)

        val backToEntity = model.toEntity()
        assertEquals(entity, backToEntity)
    }
}
