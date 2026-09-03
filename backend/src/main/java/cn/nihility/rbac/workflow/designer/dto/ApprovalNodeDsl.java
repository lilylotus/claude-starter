package cn.nihility.rbac.workflow.designer.dto;

import cn.nihility.rbac.workflow.constant.ApprovalMode;
import cn.nihility.rbac.workflow.constant.AssigneeType;
import cn.nihility.rbac.workflow.constant.EmptyAssigneeStrategy;
import lombok.Getter;
import lombok.Setter;

/**
 * "审批"节点，字段与 {@code tab_wf_node_assignee_rule} 逐字段对应，前端属性面板即为该表
 * 字段的表单化编辑（workflow-approval-engine change design.md Decision 9）。
 */
@Getter
@Setter
public class ApprovalNodeDsl extends ProcessNodeDsl {

    /** 审批人来源类型。 */
    private AssigneeType assigneeType;

    /** 审批人来源取值，按 {@code assigneeType} 解释（角色编码/用户 id 等）。 */
    private String assigneeValue;

    /** 审批模式：单人/全部通过/任一通过/比例通过。 */
    private ApprovalMode approvalMode;

    /** 会签通过比例（1~100 的整数），仅 {@code approvalMode=PERCENT} 时使用。 */
    private Integer approvalPercent;

    /** 空审批人处理策略。 */
    private EmptyAssigneeStrategy emptyAssigneeStrategy;

    /** 是否允许审批人为发起人本人（自审）。 */
    private Boolean allowSelfApproval;

    /** 是否允许转办。 */
    private Boolean allowTransfer;

    /** 是否允许委派。 */
    private Boolean allowDelegate;

    /** 是否允许加签。 */
    private Boolean allowAddSign;

    /** 是否允许退回到该节点。 */
    private Boolean allowReturn;
}
