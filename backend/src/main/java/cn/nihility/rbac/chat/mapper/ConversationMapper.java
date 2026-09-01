package cn.nihility.rbac.chat.mapper;

import cn.nihility.rbac.chat.entity.ConversationEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 聊天会话 MyBatis-Plus 数据访问接口，单表 CRUD 直接复用 {@link BaseMapper}；
 * {@link #selectNextSeqForUpdate}/{@link #incrementNextSeq} 需要行锁与原子自增，
 * SQL 写在 {@code resources/mybatis/mapper/ConversationMapper.xml} 里。
 */
@Mapper
public interface ConversationMapper extends BaseMapper<ConversationEntity> {

    /**
     * 在当前事务内对指定会话加行锁（{@code SELECT ... FOR UPDATE}）并读取当前
     * {@code next_seq}，调用方必须已处于事务上下文中，读取后应立即调用
     * {@link #incrementNextSeq} 完成自增，二者需在同一事务内配对使用
     * （design.md Decision 7，不使用 MySQL 8.0+ 窗口函数）。
     *
     * @param id 会话 id
     * @return 当前 {@code next_seq}，会话不存在时返回 {@code null}
     */
    Long selectNextSeqForUpdate(@Param("id") Long id);

    /**
     * 对指定会话的 {@code next_seq} 原子自增 1，必须与 {@link #selectNextSeqForUpdate}
     * 在同一事务内配对调用。
     *
     * @param id 会话 id
     * @return 受影响行数
     */
    int incrementNextSeq(@Param("id") Long id);
}
