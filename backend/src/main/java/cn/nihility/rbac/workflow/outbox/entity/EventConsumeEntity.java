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
 * Outbox 事件消费去重记录，对应表 {@code tab_wf_event_consume}
 * （production-approval-lifecycle change design.md 第9/10节，tasks.md 7.2）。
 * {@code (event_id, consumer_code)} 唯一约束是"同一事件同一消费者只消费一次"的落库依据：
 * {@code cn.nihility.rbac.workflow.outbox.service.impl.EventConsumeServiceImpl} 采用先乐观
 * {@code INSERT}、命中唯一键冲突才判定已消费过的写法，不先 {@code SELECT} 再判断——对不存在的键
 * 做加锁读取会在 MySQL InnoDB 下触发间隙锁死锁，已在 {@code BusinessLockServiceImpl} 的真实并发
 * 测试中验证过同类问题，本表复用同一套安全写法。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tab_wf_event_consume")
public class EventConsumeEntity {

    /** 主键 id。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联 {@code tab_wf_outbox_event.event_id}。 */
    private String eventId;

    /**
     * 消费者标识，如 {@code BUSINESS_EXECUTOR}/{@code NOTIFIER}，对应
     * {@link cn.nihility.rbac.workflow.outbox.consumer.OutboxEventConsumer#consumerCode()}。
     */
    private String consumerCode;

    /** 消费结果，取值见 {@link cn.nihility.rbac.workflow.outbox.constant.EventConsumeResult}。 */
    private String result;

    /** 处理完成时间。 */
    private LocalDateTime processedTime;

    /** 创建人。 */
    private String createBy;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 更新人。 */
    private String updateBy;

    /** 更新时间。 */
    private LocalDateTime updateTime;
}
