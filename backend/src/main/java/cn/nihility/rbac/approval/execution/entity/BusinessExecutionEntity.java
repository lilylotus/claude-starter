package cn.nihility.rbac.approval.execution.entity;

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
 * 业务执行尝试记录持久化实体，对应表 {@code tab_wf_business_execution}
 * （production-approval-lifecycle change design.md 第9节，tasks.md 7.3）。每次消费者领取到
 * {@code PROCESS_APPROVED} 触发事件并尝试执行一次业务写操作，落一条记录；最终成功结果的唯一性
 * 由 {@code tab_approval_request} 行的 CAS（{@code execution_status} 从
 * {@code PENDING}/{@code EXECUTING} 转 {@code SUCCEEDED}）保证，本表 {@code (request_id,
 * attempt_no)} 唯一仅约束"同一次尝试不得重复写入"。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tab_wf_business_execution")
public class BusinessExecutionEntity {

    /** 主键 id。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联 {@code tab_approval_request.id}。 */
    private Long requestId;

    /** 第几次执行尝试，同一 {@code requestId} 下从 1 递增。 */
    private Integer attemptNo;

    /** 执行该次尝试时持有的 Outbox 租约 token。 */
    private String leaseToken;

    /** 本次尝试的执行状态，取值见
     *  {@link cn.nihility.rbac.workflow.constant.ExecutionStatus}（本表只会写入
     *  {@code SUCCEEDED}/{@code FAILED_MANUAL}/{@code FAILED_RETRYABLE}，不会写入
     *  {@code NOT_READY}/{@code PENDING}，因为落库这条记录时执行已经跑完）。 */
    private String executionStatus;

    /** 失败错误码/原因摘要，供运维分类处理；成功时为空。 */
    private String errorCode;

    /** 执行成功后生效的业务记录 id（{@code CREATE} 场景），其余场景为空。 */
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
