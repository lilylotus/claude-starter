package cn.nihility.rbac.workflow.outbox.mapper;

import cn.nihility.rbac.workflow.outbox.entity.OutboxEventEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Outbox 事件数据访问接口，单表 CRUD 直接复用 {@link BaseMapper}；{@link #selectDueEvents}
 * 按到期条件筛选候选行，SQL 写在 {@code mybatis/mapper/OutboxEventMapper.xml}
 * （production-approval-lifecycle change tasks.md 7.1，套用 {@code AppNotifyRecordMapper
 * #selectDueTasks} 已验证的模式）。真正的租约抢占/完成/失败流转通过
 * {@code OutboxEventServiceImpl} 里带 CAS 条件的 {@code update(null, LambdaUpdateWrapper)}
 * 完成，不使用不带条件的 {@code updateById}，避免旧 worker 覆盖新 worker 的租约。
 */
@Mapper
public interface OutboxEventMapper extends BaseMapper<OutboxEventEntity> {

    /**
     * 扫描当前到期候选事件：{@code status='PENDING'} 且到期，或 {@code status='LEASED'} 且
     * 租约已过期，按主键升序取前 {@code limit} 条。返回完整行快照（供领取失败/重试判定使用
     * {@code attemptCount}/{@code createTime}），本查询本身不加锁、不修改数据，真正的抢占由
     * 调用方逐条条件 {@code UPDATE} 完成。
     *
     * @param now   扫描基准时刻
     * @param limit 单轮最多返回的记录数
     * @return 到期候选事件列表，按 id 升序排列
     */
    List<OutboxEventEntity> selectDueEvents(@Param("now") LocalDateTime now, @Param("limit") int limit);
}
