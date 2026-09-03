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
import org.springframework.util.StringUtils;

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
            case BLOCK -> ResolvedAssignees.blocked();
            case FALLBACK_ROLE -> applyFallbackRole(rule);
        };
    }

    /**
     * {@code FALLBACK_ROLE} 策略：改用 {@code fallback_role_code} 指定的角色解析；兜底角色
     * 解析结果仍为空时按 {@code BLOCK} 处理（DSL v2 专用，design.md Decision 5"兜底也为空时
     * 仍阻塞"）。
     */
    private ResolvedAssignees applyFallbackRole(NodeAssigneeRuleEntity rule) {
        if (!StringUtils.hasText(rule.getFallbackRoleCode())) {
            return ResolvedAssignees.blocked();
        }
        Set<Long> fallbackUsers = resolverRegistry.resolve(AssigneeType.ROLE,
                new AssigneeResolveContext(null, rule.getNodeId(), rule.getFallbackRoleCode(), null, null));
        return fallbackUsers.isEmpty() ? ResolvedAssignees.blocked() : ResolvedAssignees.toWorkflowAdmin(fallbackUsers);
    }
}
