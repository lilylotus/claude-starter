package cn.nihility.rbac.workflow.designer.compiler;

import cn.nihility.rbac.workflow.constant.ApprovalMode;
import cn.nihility.rbac.workflow.constant.AssigneeType;
import cn.nihility.rbac.workflow.constant.EmptyAssigneeStrategy;

/**
 * 编译产物中的节点审批人规则草稿，不带 {@code processDefinitionId}/审计字段，供调用方
 * （{@code WorkflowProcessModelService.publish}）在拿到新的 {@code processDefinitionId} 后
 * 批量落库为 {@code tab_wf_node_assignee_rule} 行（workflow-approval-engine change
 * design.md Decision 10）。
 *
 * @param nodeId                BPMN 用户任务节点 id
 * @param nodeName              节点名称
 * @param nodeOrder             节点顺序，用于展示"第几级审批"
 * @param assigneeType          审批人来源类型
 * @param assigneeValue         审批人来源取值
 * @param approvalMode          审批模式
 * @param approvalPercent       会签通过比例，仅 {@code approvalMode=PERCENT} 使用
 * @param emptyAssigneeStrategy 空审批人处理策略
 * @param fallbackRoleCode      兜底角色编码，仅 {@code emptyAssigneeStrategy=FALLBACK_ROLE}
 *                              时使用（DSL v2 专用，v1 编译器恒传 {@code null}）
 * @param allowSelfApproval     是否允许自审
 * @param allowTransfer         是否允许转办
 * @param allowDelegate         是否允许委派
 * @param allowAddSign          是否允许加签
 * @param allowReturn           是否允许退回到该节点
 */
public record NodeAssigneeRuleDraft(
        String nodeId,
        String nodeName,
        int nodeOrder,
        AssigneeType assigneeType,
        String assigneeValue,
        ApprovalMode approvalMode,
        Integer approvalPercent,
        EmptyAssigneeStrategy emptyAssigneeStrategy,
        String fallbackRoleCode,
        boolean allowSelfApproval,
        boolean allowTransfer,
        boolean allowDelegate,
        boolean allowAddSign,
        boolean allowReturn) {
}
