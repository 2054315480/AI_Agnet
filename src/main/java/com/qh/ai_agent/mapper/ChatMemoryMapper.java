package com.qh.ai_agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qh.ai_agent.entity.ChatMemoryEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 聊天记忆 Mapper 接口
 * 继承 MyBatis-Plus 的 BaseMapper，获得基础 CRUD 方法
 */
@Mapper
public interface ChatMemoryMapper extends BaseMapper<ChatMemoryEntity> {

    /**
     * 根据会话 ID 查询消息列表，按创建时间升序排序
     *
     * @param conversationId 会话 ID
     * @return 消息实体列表
     */
    @Select("SELECT * FROM chat_memory WHERE conversation_id = #{conversationId} ORDER BY created_at ASC")
    List<ChatMemoryEntity> findByConversationIdOrderByCreatedAtAsc(@Param("conversationId") String conversationId);

    /**
     * 批量插入消息（使用 MyBatis-Plus 的 insert 方法循环调用）
     * 注意：MyBatis-Plus 3.5.x BaseMapper 不支持批量插入，需要在业务层循环调用
     * 或者使用 IService 的 saveBatch 方法
     *
     * @param entities 消息实体列表
     */
    default void insertBatch(List<ChatMemoryEntity> entities) {
        for (ChatMemoryEntity entity : entities) {
            this.insert(entity);
        }
    }
}
