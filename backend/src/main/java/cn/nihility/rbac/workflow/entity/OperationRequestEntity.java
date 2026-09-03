package cn.nihility.rbac.workflow.entity;

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
 * 操作幂等记录持久化实体，对应表 {@code tab_wf_operation_request}。{@code requestKey} 取自
 * 请求方传入的 {@code X-Request-Id}，为空时退化为不做幂等保护；插入本行与实际执行的写操作
 * 处于同一事务，唯一键冲突时短路跳过、不重复执行（{@code IdempotencyService} 实现细节）。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tab_wf_operation_request")
public class OperationRequestEntity {

    /** 主键 id。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 幂等键，取自 {@code X-Request-Id} 请求头，唯一。 */
    private String requestKey;

    /** 关联的审批任务 id（{@code tab_wf_approval_task.id}），部分操作（如撤回）不针对具体
     *  任务，可为空。 */
    private Long taskId;

    /** 操作人用户 id。 */
    private Long operatorId;

    /** 操作类型，{@link cn.nihility.rbac.workflow.constant.ApprovalAction} 字面量。 */
    private String operation;

    /** 执行结果状态：{@code SUCCESS}/{@code FAILED}。 */
    private String status;

    /** 创建人。 */
    private String createBy;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 更新人。 */
    private String updateBy;

    /** 更新时间。 */
    private LocalDateTime updateTime;
}
