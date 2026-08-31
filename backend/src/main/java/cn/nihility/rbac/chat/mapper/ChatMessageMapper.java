package cn.nihility.rbac.chat.mapper;

import cn.nihility.rbac.chat.entity.ChatMessageEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 聊天消息 MyBatis-Plus 数据访问接口，单表 CRUD 与分页查询直接复用 {@link BaseMapper}；
 * {@link #selectLatestByConversationIds} 需要"每个会话取最新一条消息"，SQL 写在
 * {@code resources/mybatis/mapper/ChatMessageMapper.xml} 里（用自关联 + GROUP BY MAX(id)
 * 实现，不使用 MySQL 8.0+ 窗口函数）。
 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessageEntity> {

    /**
     * 批量查询多个会话各自最新一条消息，供会话列表接口展示"最近消息摘要"使用。
     *
     * @param conversationIds 会话 id 列表
     * @return 每个会话最新一条消息（不保证顺序，调用方自行按 conversationId 索引）
     */
    List<ChatMessageEntity> selectLatestByConversationIds(@Param("conversationIds") List<Long> conversationIds);
}
