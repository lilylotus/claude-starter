package cn.nihility.rbac.approval.entity;

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
 * 主数据变更审批申请持久化实体，对应表 {@code tab_approval_request}。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tab_approval_request")
public class ApprovalRequestEntity {

    /** 主键 id。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务对象类型：ORG/USER/POSITION/APP。 */
    private String bizType;

    /** 操作类型：CREATE/UPDATE/ENABLE/DISABLE/DELETE。 */
    private String operationType;

    /** 目标记录 id，创建申请为空。 */
    private Long targetId;

    /** 创建申请审批通过后实际生成的记录 id。 */
    private Long resultTargetId;

    /** 创建或更新请求的 JSON 快照。 */
    private String requestPayload;

    /** 申请状态。 */
    private Integer status;

    /** 审批人用户 id。 */
    private Long approverId;

    /** 审批时间。 */
    private LocalDateTime approveTime;

    /** 审批意见。 */
    private String opinion;

    /** Flowable 流程实例 id，仅作兼容展示用，不再是驱动审批状态流转的依据。 */
    private String flowableProcessInstanceId;

    /** Flowable 用户任务 id，仅作兼容展示用，不再是驱动审批状态流转的依据。 */
    private String flowableTaskId;

    /** 关联的通用审批引擎流程实例 id，对应 {@code tab_wf_process_instance.id}，驱动多级审批。 */
    private Long processInstanceId;

    /** 当前所在审批节点名称，流程结束（已通过/已拒绝/已撤回）后置空。 */
    private String currentNodeName;

    /** 执行模式：{@code LEGACY_SYNC} 同步执行（历史行为，默认）/{@code RELIABLE_ASYNC} 审批
     *  通过后经 Outbox 可靠异步执行，取值见 {@link cn.nihility.rbac.workflow.constant.ExecutionMode}
     *  （production-approval-lifecycle change design.md Decision 6，tasks.md 7.3 补齐既有
     *  实体遗漏的字段映射：本列由 V11 迁移脚本建好，此前实体一直未映射）。 */
    private String executionMode;

    /** 业务执行状态，仅 {@code executionMode=RELIABLE_ASYNC} 时使用，取值见
     *  {@link cn.nihility.rbac.workflow.constant.ExecutionStatus}；{@code LEGACY_SYNC} 申请
     *  恒为空（tasks.md 7.3 补齐既有实体遗漏的字段映射）。 */
    private String executionStatus;

    /** 发起时目标业务数据的版本/哈希快照，执行前用于重新校验目标是否已变化（tasks.md 7.3
     *  补齐既有实体遗漏的字段映射，本轮暂未写入/使用，7.5 范围）。 */
    private String baseRevision;

    /** 因路由或 payload 变更而重新发起时关联的前一条申请 id（tasks.md 7.3 补齐既有实体遗漏的
     *  字段映射，本轮暂未写入/使用，7.5 范围）。 */
    private Long previousRequestId;

    /** 提交时命中的表单版本 id，关联 {@code tab_wf_form_version.id}，历史申请为空。 */
    private Long formVersionId;

    /** 提交时冻结的变更前业务数据快照（JSON），仅 UPDATE/ENABLE/DISABLE/DELETE 类操作有值，
     *  CREATE 操作无"变更前"概念，为空。 */
    private String beforeSnapshot;

    /** 提交时冻结的变更后业务数据快照（JSON），即 {@link #requestPayload} 的等价只读副本，
     *  供审计留痕，审批过程中业务 payload 本身不可再变更（design.md Decision 5）。 */
    private String afterSnapshot;

    /** 创建人，即申请提交人。 */
    private String createBy;

    /** 创建时间，即申请提交时间。 */
    private LocalDateTime createTime;

    /** 更新人。 */
    private String updateBy;

    /** 更新时间。 */
    private LocalDateTime updateTime;
}
