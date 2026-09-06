package cn.nihility.rbac.workflow.outbox.service.impl;

import cn.nihility.rbac.common.util.JacksonUtils;
import cn.nihility.rbac.workflow.outbox.config.OutboxRetryProperties;
import cn.nihility.rbac.workflow.outbox.constant.OutboxEventStatus;
import cn.nihility.rbac.workflow.outbox.entity.OutboxEventEntity;
import cn.nihility.rbac.workflow.outbox.mapper.OutboxEventMapper;
import cn.nihility.rbac.workflow.outbox.service.OutboxEventService;
import cn.nihility.rbac.workflow.outbox.support.OutboxRetryScheduleCalculator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Outbox 可靠事件生产/领取服务实现（production-approval-lifecycle change design.md 第10节，
 * tasks.md 7.1）。
 * <p>
 * {@link #publish} 声明 {@link Propagation#REQUIRED}（加入调用方已存在的事务，而不是像
 * design.md 字面提到的反模式那样用 {@code REQUIRES_NEW} 另起物理连接/事务）——本方法不单独
 * 起新事务，只是在调用方的当前事务里追加一条 {@code insert}，调用方事务回滚时这条事件行随之
 * 回滚，从而满足"同事务写入保证不丢/不多"的要求；调用方自身不在事务中时，{@code REQUIRED}
 * 会按 Spring 默认语义新开一个事务，这与直接执行一次独立 insert 等价，不影响语义正确性。
 * <p>
 * {@link #claimDueEvents}/{@link #markSucceeded}/{@link #markFailed} 均采用"逐条条件
 * {@code UPDATE} + 检查受影响行数"的 MySQL 5.7 兼容 CAS 写法，不依赖
 * {@code SELECT ... FOR UPDATE SKIP LOCKED}（MySQL 8.0+ 才支持）；完成/失败流转额外要求
 * {@code lease_token} 匹配，防止旧 worker 用过期的租约 token 覆盖新 worker 已经抢占并可能
 * 正在处理的记录（design.md 第10节"租约 token 用于完成/续期 CAS，防旧 worker 覆盖新
 * worker"）。
 * <p>
 * 所有落库/比较用的"当前时刻"统一先 {@code withNano(0)} 截断到整秒
 * （见私有方法 {@link #now()}）：{@code next_retry_time}/{@code lease_until} 对应的列是不带
 * 小数秒精度的 {@code DATETIME}，MySQL 对超出列精度的小数秒按四舍五入（而不是截断）写入——
 * 真实集成测试中曾复现"发布后立即领取因此漏领"的问题：写入时 Java 侧 {@code now} 恰好落在
 * 整秒的后半段（如 .672xxx），被 MySQL 四舍五入进位到下一整秒存库，而几毫秒后发起的领取查询
 * 用同一秒内、未进位的 {@code now} 与之比较，`next_retry_time <= now` 判定为假，新发布的事件
 * 被漏掉。统一在 Java 侧先截断到整秒再写入/比较，两侧精度一致后不再依赖 MySQL 的进位行为，
 * 问题不再复现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxEventServiceImpl implements OutboxEventService {

    /** Outbox 生产/领取场景下审计字段的固定操作人标识：多数调用发生在无登录用户上下文的后台流程。 */
    private static final String SYSTEM_OPERATOR = "system";

    /** Outbox 事件数据访问接口。 */
    private final OutboxEventMapper outboxEventMapper;

    /** 重试/租约/死信相关配置。 */
    private final OutboxRetryProperties outboxRetryProperties;

    /** 重试退避时间计算器。 */
    private final OutboxRetryScheduleCalculator outboxRetryScheduleCalculator;

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public OutboxEventEntity publish(
            String eventId, String aggregateId, long eventSeq, String eventType, Object payload) {
        if (!StringUtils.hasText(eventId)) {
            throw new IllegalArgumentException("Outbox 事件 eventId 不能为空");
        }
        if (!StringUtils.hasText(aggregateId)) {
            throw new IllegalArgumentException("Outbox 事件 aggregateId 不能为空");
        }
        if (!StringUtils.hasText(eventType)) {
            throw new IllegalArgumentException("Outbox 事件 eventType 不能为空");
        }

        LocalDateTime now = now();
        OutboxEventEntity event = OutboxEventEntity.builder()
                .eventId(eventId)
                .aggregateId(aggregateId)
                .eventSeq(eventSeq)
                .eventType(eventType)
                .payload(JacksonUtils.toJson(payload))
                .status(OutboxEventStatus.PENDING)
                .nextRetryTime(now)
                .attemptCount(0)
                .createBy(SYSTEM_OPERATOR)
                .createTime(now)
                .updateBy(SYSTEM_OPERATOR)
                .updateTime(now)
                .build();
        try {
            outboxEventMapper.insert(event);
        } catch (DuplicateKeyException ex) {
            log.info("Outbox 事件幂等键 {} 已存在，复用已落库的行，不重复插入", eventId);
            return findByEventId(eventId);
        }
        return event;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public List<OutboxEventEntity> claimDueEvents(int batchSize) {
        LocalDateTime now = now();
        List<OutboxEventEntity> candidates = outboxEventMapper.selectDueEvents(now, batchSize);
        List<OutboxEventEntity> claimed = new ArrayList<>(candidates.size());
        for (OutboxEventEntity candidate : candidates) {
            String leaseToken = UUID.randomUUID().toString();
            LocalDateTime leaseUntil = now.plusSeconds(outboxRetryProperties.getLeaseSeconds());
            int updated = outboxEventMapper.update(null, new LambdaUpdateWrapper<OutboxEventEntity>()
                    .eq(OutboxEventEntity::getId, candidate.getId())
                    .and(w -> w.eq(OutboxEventEntity::getStatus, OutboxEventStatus.PENDING)
                            .or(w2 -> w2.eq(OutboxEventEntity::getStatus, OutboxEventStatus.LEASED)
                                    .lt(OutboxEventEntity::getLeaseUntil, now)))
                    .set(OutboxEventEntity::getStatus, OutboxEventStatus.LEASED)
                    .set(OutboxEventEntity::getLeaseToken, leaseToken)
                    .set(OutboxEventEntity::getLeaseUntil, leaseUntil)
                    .set(OutboxEventEntity::getUpdateBy, SYSTEM_OPERATOR)
                    .set(OutboxEventEntity::getUpdateTime, now));
            if (updated != 1) {
                // 候选在读取候选列表与本次 UPDATE 之间已被其他 worker 抢占，跳过、继续下一个候选，
                // 不抛异常中断整批（design.md 第10节"没有抢到则跳过"）。
                continue;
            }
            candidate.setStatus(OutboxEventStatus.LEASED);
            candidate.setLeaseToken(leaseToken);
            candidate.setLeaseUntil(leaseUntil);
            candidate.setUpdateBy(SYSTEM_OPERATOR);
            candidate.setUpdateTime(now);
            claimed.add(candidate);
        }
        return claimed;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public boolean markSucceeded(OutboxEventEntity claimedEvent) {
        LocalDateTime now = now();
        int updated = outboxEventMapper.update(null, leaseOwnedWrapper(claimedEvent)
                .set(OutboxEventEntity::getStatus, OutboxEventStatus.SUCCEEDED)
                .set(OutboxEventEntity::getLeaseToken, null)
                .set(OutboxEventEntity::getLeaseUntil, null)
                // next_retry_time 列非空，终态行不再参与到期扫描，填充为当前时刻仅为满足约束。
                .set(OutboxEventEntity::getNextRetryTime, now)
                .set(OutboxEventEntity::getUpdateBy, SYSTEM_OPERATOR)
                .set(OutboxEventEntity::getUpdateTime, now));
        if (updated != 1) {
            log.warn("Outbox 事件[{}]标记成功被拒绝：租约已不再由 token[{}] 持有（可能已被其他 worker 重新领取）",
                    claimedEvent.getId(), claimedEvent.getLeaseToken());
            return false;
        }
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public boolean markFailed(OutboxEventEntity claimedEvent) {
        LocalDateTime now = now();
        int attemptCountBefore = claimedEvent.getAttemptCount() == null ? 0 : claimedEvent.getAttemptCount();
        OutboxRetryScheduleCalculator.RetryDecision decision =
                outboxRetryScheduleCalculator.decide(attemptCountBefore, claimedEvent.getCreateTime(), now);

        LambdaUpdateWrapper<OutboxEventEntity> wrapper = leaseOwnedWrapper(claimedEvent)
                .set(OutboxEventEntity::getLeaseToken, null)
                .set(OutboxEventEntity::getLeaseUntil, null)
                .set(OutboxEventEntity::getUpdateBy, SYSTEM_OPERATOR)
                .set(OutboxEventEntity::getUpdateTime, now);
        if (decision.dead()) {
            wrapper.set(OutboxEventEntity::getStatus, OutboxEventStatus.FAILED)
                    .set(OutboxEventEntity::getAttemptCount, decision.attemptCount())
                    // next_retry_time 列非空，终态行不再参与到期扫描，填充为当前时刻仅为满足约束。
                    .set(OutboxEventEntity::getNextRetryTime, now);
        } else {
            wrapper.set(OutboxEventEntity::getStatus, OutboxEventStatus.PENDING)
                    .set(OutboxEventEntity::getAttemptCount, decision.attemptCount())
                    .set(OutboxEventEntity::getNextRetryTime, decision.nextRetryTime());
        }

        int updated = outboxEventMapper.update(null, wrapper);
        if (updated != 1) {
            log.warn("Outbox 事件[{}]标记失败被拒绝：租约已不再由 token[{}] 持有（可能已被其他 worker 重新领取）",
                    claimedEvent.getId(), claimedEvent.getLeaseToken());
            return false;
        }
        return true;
    }

    /**
     * 构造"仅当当前仍持有租约"的 CAS 更新条件：{@code id} 精确匹配 + {@code status='LEASED'}
     * + {@code lease_token} 与调用方持有的一致。三个条件任一不满足都会导致更新影响 0 行，
     * 即租约已被其他 worker 重新抢占（fencing 生效）。
     *
     * @param claimedEvent 调用方持有的事件实体快照
     * @return 携带 CAS 条件、尚未追加 {@code set} 子句的更新构造器
     */
    private LambdaUpdateWrapper<OutboxEventEntity> leaseOwnedWrapper(OutboxEventEntity claimedEvent) {
        return new LambdaUpdateWrapper<OutboxEventEntity>()
                .eq(OutboxEventEntity::getId, claimedEvent.getId())
                .eq(OutboxEventEntity::getStatus, OutboxEventStatus.LEASED)
                .eq(OutboxEventEntity::getLeaseToken, claimedEvent.getLeaseToken());
    }

    /**
     * 获取截断到整秒的当前时刻，用于全部落库到 {@code next_retry_time}/{@code lease_until}
     * 或与之比较的"当前时刻"（理由见类注释）。
     *
     * @return 截断到整秒（{@code nano=0}）的当前时刻
     */
    private LocalDateTime now() {
        return LocalDateTime.now().withNano(0);
    }

    /**
     * 按 {@code eventId} 唯一键查询已存在的事件行，用于 {@link #publish} 命中唯一键冲突后的
     * 幂等复用。
     *
     * @param eventId 业务幂等事件 id
     * @return 已存在的事件行
     */
    private OutboxEventEntity findByEventId(String eventId) {
        return outboxEventMapper.selectOne(
                new LambdaQueryWrapper<OutboxEventEntity>().eq(OutboxEventEntity::getEventId, eventId));
    }
}
