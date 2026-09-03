package cn.nihility.rbac.workflow.assignee.support;

import cn.nihility.rbac.workflow.assignee.AssigneeResolveContext;
import cn.nihility.rbac.workflow.assignee.AssigneeResolverRegistry;
import cn.nihility.rbac.workflow.constant.AssigneeType;
import cn.nihility.rbac.workflow.constant.EmptyAssigneeStrategy;
import cn.nihility.rbac.workflow.constant.WorkflowConstants;
import cn.nihility.rbac.workflow.entity.NodeAssigneeRuleEntity;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 节点审批人解析编排服务：组合 {@link AssigneeResolverRegistry} 的原始解析结果、"审批人为
 * 发起人本人且不允许自审"过滤、空审批人策略兜底三步，得到最终可直接用于设置 Flowable
 * {@code assignee}/{@code candidateUsers} 的结果（workflow-approval-engine change design.md
 * Decision 4）。抽成独立、不依赖任何 Flowable API 的纯服务类，便于脱离真实引擎单元测试。
 */
@Component
@RequiredArgsConstructor
public class NodeAssigneeResolutionService {

    /** 审批人解析器注册表。 */
    private final AssigneeResolverRegistry resolverRegistry;

    /** 管理员角色查询辅助组件，用于 {@code TO_WORKFLOW_ADMIN} 兜底。 */
    private final AdminRoleLookupService adminRoleLookupService;

    /**
     * 按节点审批人规则解析最终结果。
     *
     * @param rule    节点审批人规则
     * @param context 解析上下文
     * @return 解析结果
     */
    public ResolvedAssignees resolve(NodeAssigneeRuleEntity rule, AssigneeResolveContext context) {
        AssigneeType type = AssigneeType.valueOf(rule.getAssigneeType());
        Set<Long> resolved = resolverRegistry.resolve(type, context);

        boolean selfApprovalNotAllowed = !Boolean.TRUE.equals(rule.getAllowSelfApproval())
                && context.applicantId() != null
                && resolved.size() == 1
                && resolved.contains(context.applicantId());
        if (selfApprovalNotAllowed) {
            resolved = Set.of();
        }

        if (!resolved.isEmpty()) {
            return ResolvedAssignees.direct(resolved);
        }
        return applyEmptyAssigneeStrategy(rule);
    }

    /**
     * 按节点配置的空审批人策略兜底。
     */
    private ResolvedAssignees applyEmptyAssigneeStrategy(NodeAssigneeRuleEntity rule) {
        EmptyAssigneeStrategy strategy = EmptyAssigneeStrategy.valueOf(rule.getEmptyAssigneeStrategy());
        return switch (strategy) {
            case TO_WORKFLOW_ADMIN -> ResolvedAssignees.toWorkflowAdmin(
                    adminRoleLookupService.findUserIdsByRoleCode(WorkflowConstants.WORKFLOW_ADMIN_ROLE_CODE));
            case AUTO_SKIP -> ResolvedAssignees.autoSkip();
            case REJECT -> ResolvedAssignees.reject();
        };
    }
}
