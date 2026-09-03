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

    /** 创建人，即申请提交人。 */
    private String createBy;

    /** 创建时间，即申请提交时间。 */
    private LocalDateTime createTime;

    /** 更新人。 */
    private String updateBy;

    /** 更新时间。 */
    private LocalDateTime updateTime;
}
