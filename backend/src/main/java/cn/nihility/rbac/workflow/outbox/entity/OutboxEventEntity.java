package cn.nihility.rbac.workflow.outbox.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Outbox 可靠事件持久化实体，对应表 {@code tab_wf_outbox_event}
 * （production-approval-lifecycle change design.md 第9/10节，tasks.md 7.1）。审批终态副作用
 * （业务执行、通知、抄送）事件的同事务写入载体：生产者与业务写操作共用同一物理事务，事务
 * 回滚时事件行随之消失；消费者按 {@code status}/{@code next_retry_time}/{@code lease_until}
 * 组合以 MySQL 5.7 兼容的"逐条条件 UPDATE"方式领取租约，不依赖 {@code SELECT ... FOR
 * UPDATE SKIP LOCKED}（MySQL 5.7 不支持）。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tab_wf_outbox_event")
public class OutboxEventEntity {

    /** 主键 id。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务幂等事件 id，唯一，调用方指定（生产者去重键）。 */
    private String eventId;

    /** 聚合根标识，通常为流程实例 id 文本。 */
    private String aggregateId;

    /** 同一聚合根内事件序号，供顺序消费参考。 */
    private Long eventSeq;

    /**
     * 事件类型：{@code TASK_CREATED}/{@code TASK_ASSIGNED}/{@code TASK_CANCELLED}/
     * {@code PROCESS_APPROVED}/{@code PROCESS_REJECTED}/{@code BUSINESS_SUCCEEDED}/
     * {@code BUSINESS_FAILED}/{@code CC_CREATED}。
     */
    private String eventType;

    /** 事件负载（JSON 快照）。 */
    private String payload;

    /** 状态，取值见 {@link cn.nihility.rbac.workflow.outbox.constant.OutboxEventStatus}。 */
    private String status;

    /** 下次可领取/重试时间，用于按索引扫描到期候选；列非空，终态行填充为最近一次更新时刻。 */
    private LocalDateTime nextRetryTime;

    /** 当前持有租约的 token，完成/续期均需匹配该 token（CAS），空闲/终态时为空。 */
    private String leaseToken;

    /** 租约到期时间，过期后其他消费者可重新抢占，空闲/终态时为空。 */
    private LocalDateTime leaseUntil;

    /** 已尝试次数，超过配置上限转人工处理队列。 */
    private Integer attemptCount;

    /** 创建人。 */
    private String createBy;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 更新人。 */
    private String updateBy;

    /** 更新时间。 */
    private LocalDateTime updateTime;
}
