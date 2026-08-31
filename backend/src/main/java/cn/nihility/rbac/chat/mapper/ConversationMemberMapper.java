package cn.nihility.rbac.chat.mapper;

import cn.nihility.rbac.chat.entity.ConversationMemberEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 聊天会话成员 MyBatis-Plus 数据访问接口，单表 CRUD 直接复用 {@link BaseMapper}；
 * {@link #selectSingleConversationId} 需要自关联查询，SQL 写在
 * {@code resources/mybatis/mapper/ConversationMemberMapper.xml} 里。
 */
@Mapper
public interface ConversationMemberMapper extends BaseMapper<ConversationMemberEntity> {

    /**
     * 查询两个用户之间已存在的单聊会话 id（若存在）。
     *
     * @param userId1 用户 1 id
     * @param userId2 用户 2 id
     * @return 已存在的单聊会话 id，不存在时返回 {@code null}
     */
    Long selectSingleConversationId(@Param("userId1") Long userId1, @Param("userId2") Long userId2);
}
