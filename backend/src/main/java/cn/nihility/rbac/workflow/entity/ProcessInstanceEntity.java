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
 * 流程实例持久化实体，对应表 {@code tab_wf_process_instance}。{@code applicantId}/
 * {@code applicantOrgId} 是流程启动时刻的快照，全部 {@link cn.nihility.rbac.workflow.assignee.AssigneeResolver}
 * 只读这个快照，不实时重查申请人当前组织，避免审批中途申请人调岗导致路由突变
 * （workflow-approval-engine change design.md Decision 6）。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tab_wf_process_instance")
public class ProcessInstanceEntity {

    /** 主键 id。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** Flowable 流程实例 id；创建本行时流程实例可能尚未启动，允许为空，启动后立即回填。 */
    private String flowableInstanceId;

    /** 本实例具体跑在哪个不可变流程定义版本上，关联 {@code tab_wf_process_definition.id}。 */
    private Long processDefinitionId;

    /** 发起时命中的业务绑定 id，关联 {@code tab_wf_process_binding.id}；非经绑定发起
     *  （如尚未接入绑定机制的历史调用方）时为空。 */
    private Long bindingId;

    /** 发起时命中的业务绑定修订号快照。 */
    private Long bindingRevision;

    /** 发起时使用的表单版本 id 快照。 */
    private Long formVersionId;

    /** 业务对象类型，如 {@code ORG}/{@code USER}/{@code POSITION}/{@code APP}。 */
    private String businessType;

    /** 业务对象 id，如 {@code tab_approval_request.id}。 */
    private Long businessId;

    /** 流程标题，供列表展示。 */
    private String title;

    /** 发起人用户 id。 */
    private Long applicantId;

    /** 发起人所属组织 id，发起时快照。 */
    private Long applicantOrgId;

    /** 提交时冻结的申请人身份上下文快照（JSON：组织/岗位/角色等），DSL v2 专用。 */
    private String applicantIdentitySnapshot;

    /** 状态：{@code RUNNING}/{@code APPROVED}/{@code REJECTED}/{@code WITHDRAWN}/{@code TERMINATED}。 */
    private String status;

    /** 流程正常结束的明确结果，如 {@code APPROVED}/{@code REJECTED}；不能从"找不到运行实例"
     *  反推，运行中为空。 */
    private String outcome;

    /** 运维阻塞原因码，如 {@code ASSIGNEE_EMPTY}/{@code JOB_FAILED}；独立于 {@code status}，
     *  不伪造引擎终态，正常时为空。 */
    private String exceptionCode;

    /** 当前所在节点 id，结束后置空。 */
    private String currentNodeId;

    /** 当前所在节点名称，结束后置空。 */
    private String currentNodeName;

    /** 启动时间。 */
    private LocalDateTime startedTime;

    /** 结束时间，运行中为空。 */
    private LocalDateTime finishedTime;

    /** 乐观锁修订号，配合固定锁顺序使用。 */
    private Long revision;

    /** 创建人。 */
    private String createBy;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 更新人。 */
    private String updateBy;

    /** 更新时间。 */
    private LocalDateTime updateTime;
}
