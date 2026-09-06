package cn.nihility.rbac.workflow.outbox.consumer;

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
 * 测试专用实体，映射 7.1 已建好的 {@code tab_wf_business_execution} 表（真实 DDL 见
 * {@code V11__add_production_approval_lifecycle_tables.sql}，无外键约束，可安全写入测试用的
 * 任意 {@code requestId}）。仅供 {@link RecordingOutboxEventConsumer} 测试桩消费者写入"业务
 * 结果"代表数据使用，验证"成功业务结果与消费标记原子提交"（production-approval-lifecycle
 * change tasks.md 7.2）；不代表 7.3/7.4 真实业务执行适配器最终会采用的落库结构，7.3/7.4 落地时
 * 应按自身需要重新设计该实体，不要假定可以直接复用本类。只存在于测试源码集，不会被打进生产
 * 制品。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tab_wf_business_execution")
public class StubBusinessExecutionEntity {

    /** 主键 id。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 测试场景下直接复用触发消费的 Outbox 事件行主键 id，代表关联的业务申请 id。 */
    private Long requestId;

    /** 第几次执行尝试，测试场景固定为 1。 */
    private Integer attemptNo;

    /** 执行该次尝试时持有的 Outbox 租约 token，用于断言业务结果与触发它的领取快照对应。 */
    private String leaseToken;

    /** 本次尝试的执行状态。 */
    private String executionStatus;

    /** 失败错误码，测试场景未使用。 */
    private String errorCode;

    /** 执行成功后生效的业务记录 id，测试场景未使用。 */
    private Long resultTargetId;

    /** 创建人。 */
    private String createBy;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 更新人。 */
    private String updateBy;

    /** 更新时间。 */
    private LocalDateTime updateTime;
}
