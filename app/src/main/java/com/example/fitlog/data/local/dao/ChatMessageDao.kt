package com.example.fitlog.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.fitlog.data.local.entity.chat.ChatMessageEntity

/**
 * 聊天消息（[ChatMessageEntity]）的数据访问对象。
 */
@Dao
interface ChatMessageDao {

    /**
     * 全部消息按落库时间升序（对话回放顺序）。
     */
    @Query("SELECT * FROM chat_messages ORDER BY createdAt ASC, id ASC")
    suspend fun getAll(): List<ChatMessageEntity>

    /**
     * 写入一条消息，返回自增 id（LazyColumn 稳定 key 的事实源）。
     */
    @Insert
    suspend fun insert(entity: ChatMessageEntity): Long

    /**
     * 消息总数（判断是否需要从 ADK 历史做一次性 seed）。
     */
    @Query("SELECT COUNT(*) FROM chat_messages")
    suspend fun count(): Long

    /**
     * 清空全部消息（清空对话时与 ADK 会话删除同批执行）。
     */
    @Query("DELETE FROM chat_messages")
    suspend fun clearAll()
}
