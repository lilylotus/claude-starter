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

    /** 状态：{@code RUNNING}/{@code APPROVED}/{@code REJECTED}/{@code WITHDRAWN}/{@code TERMINATED}。 */
    private String status;

    /** 当前所在节点 id，结束后置空。 */
    private String currentNodeId;

    /** 当前所在节点名称，结束后置空。 */
    private String currentNodeName;

    /** 启动时间。 */
    private LocalDateTime startedTime;

    /** 结束时间，运行中为空。 */
    private LocalDateTime finishedTime;

    /** 创建人。 */
    private String createBy;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 更新人。 */
    private String updateBy;

    /** 更新时间。 */
    private LocalDateTime updateTime;
}
