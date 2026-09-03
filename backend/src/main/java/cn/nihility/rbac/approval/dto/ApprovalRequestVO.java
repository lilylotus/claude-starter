package cn.nihility.rbac.approval.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 审批申请视图对象。
 */
@Getter
@Setter
@Schema(description = "主数据变更审批申请")
public class ApprovalRequestVO {

    /** 申请 id。 */
    private Long id;

    /** 业务对象类型。 */
    private String bizType;

    /** 操作类型。 */
    private String operationType;

    /** 目标记录 id。 */
    private Long targetId;

    /** 创建审批通过后生成的记录 id。 */
    private Long resultTargetId;

    /** 请求 JSON 快照。 */
    private Object requestPayload;

    /** 申请状态。 */
    private Integer status;

    /** 审批人用户 id。 */
    private Long approverId;

    /** 审批人展示名称。 */
    private String approverName;

    /** 审批时间。 */
    private LocalDateTime approveTime;

    /** 审批意见。 */
    private String opinion;

    /** 提交人用户 id 文本。 */
    private String createBy;

    /** 提交人展示名称。 */
    private String createByName;

    /** 提交时间。 */
    private LocalDateTime createTime;

    /** 目标记录当前值，更新申请详情使用。 */
    private Object targetSnapshot;

    /** 当前所在审批节点名称，流程结束后为空。 */
    private String currentNodeName;
}
