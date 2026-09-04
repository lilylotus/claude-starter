package cn.nihility.rbac.workflow.assignee.support;

import cn.nihility.rbac.workflow.assignee.AssigneeResolveContext;
import cn.nihility.rbac.workflow.assignee.AssigneeResolverRegistry;
import cn.nihility.rbac.workflow.constant.AssigneeType;
import cn.nihility.rbac.workflow.constant.EmptyAssigneeStrategy;
import cn.nihility.rbac.workflow.constant.WorkflowConstants;
import cn.nihility.rbac.workflow.entity.NodeAssigneeRuleEntity;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 节点审批人解析编排服务：组合 {@link AssigneeResolverRegistry} 的原始解析结果、"审批人为
 * 发起人本人且不允许自审"过滤、去重、空审批人策略兜底四步，得到最终可直接用于设置 Flowable
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
        // 解析器返回的 Set 天然去重（同一来源解析出的重复 userId 只会保留一条），这里改用
        // LinkedHashSet 承接，是为了能在自审排除时对集合做原地 remove（AssigneeResolverRegistry
        // 部分实现返回不可变 Set.of()，不能直接 remove）。
        Set<Long> resolved = new LinkedHashSet<>(resolverRegistry.resolve(type, context));

        boolean selfExcluded = !Boolean.TRUE.equals(rule.getAllowSelfApproval())
                && context.applicantId() != null
                && resolved.remove(context.applicantId());

        String basis = describeBasis(type, rule, resolved, selfExcluded);
        if (!resolved.isEmpty()) {
            return ResolvedAssignees.direct(resolved, basis);
        }
        return applyEmptyAssigneeStrategy(rule, basis);
    }

    /**
     * 生成候选人解析依据说明文本，供落库 {@code tab_wf_approval_task_candidate.resolve_basis}
     * 及运维排查使用（production-approval-lifecycle change tasks.md 5.4）。
     */
    private String describeBasis(
            AssigneeType type, NodeAssigneeRuleEntity rule, Set<Long> resolved, boolean selfExcluded) {
        String base = switch (type) {
            case USER -> "指定用户 " + rule.getAssigneeValue();
            case ROLE -> "角色 " + rule.getAssigneeValue() + " 命中 " + resolved.size() + " 人";
            case POSITION -> "岗位类型 " + rule.getAssigneeValue() + " 任职命中 " + resolved.size() + " 人";
            case ORG_LEADER -> "组织负责人（角色 " + rule.getAssigneeValue() + "）命中 " + resolved.size() + " 人";
            case APPLICANT_DEPT_LEADER -> "申请人所属组织负责人命中 " + resolved.size() + " 人";
            case APPLICANT_DEPT_PARENT_LEADER -> "申请人上级组织负责人命中 " + resolved.size() + " 人";
            case PREVIOUS_APPROVER -> "上一节点处理人命中 " + resolved.size() + " 人";
            case INITIATOR -> "流程发起人";
        };
        return selfExcluded ? base + "（已排除申请人本人自审）" : base;
    }

    /**
     * 按节点配置的空审批人策略兜底。
     */
    private ResolvedAssignees applyEmptyAssigneeStrategy(NodeAssigneeRuleEntity rule, String originalBasis) {
        EmptyAssigneeStrategy strategy = EmptyAssigneeStrategy.valueOf(rule.getEmptyAssigneeStrategy());
        return switch (strategy) {
            case TO_WORKFLOW_ADMIN -> {
                Set<Long> admins = adminRoleLookupService.findUserIdsByRoleCode(WorkflowConstants.WORKFLOW_ADMIN_ROLE_CODE);
                yield ResolvedAssignees.toWorkflowAdmin(admins,
                        originalBasis + "；原候选人为空，按 TO_WORKFLOW_ADMIN 策略转流程管理员命中 " + admins.size() + " 人");
            }
            case AUTO_SKIP -> ResolvedAssignees.autoSkip(originalBasis + "；原候选人为空，按 AUTO_SKIP 策略自动通过");
            case REJECT -> ResolvedAssignees.reject(originalBasis + "；原候选人为空，按 REJECT 策略终止流程");
            case BLOCK -> ResolvedAssignees.blocked(originalBasis + "；原候选人为空，按 BLOCK 策略阻塞待运维重分配");
            case FALLBACK_ROLE -> applyFallbackRole(rule, originalBasis);
        };
    }

    /**
     * {@code FALLBACK_ROLE} 策略：改用 {@code fallback_role_code} 指定的角色解析；兜底角色
     * 解析结果仍为空时按 {@code BLOCK} 处理（DSL v2 专用，design.md Decision 5"兜底也为空时
     * 仍阻塞"）。
     */
    private ResolvedAssignees applyFallbackRole(NodeAssigneeRuleEntity rule, String originalBasis) {
        if (!StringUtils.hasText(rule.getFallbackRoleCode())) {
            return ResolvedAssignees.blocked(originalBasis + "；原候选人为空且未配置兜底角色，按 BLOCK 策略阻塞待运维重分配");
        }
        Set<Long> fallbackUsers = resolverRegistry.resolve(AssigneeType.ROLE,
                new AssigneeResolveContext(null, rule.getNodeId(), rule.getFallbackRoleCode(), null, null, null, null));
        String fallbackBasis = originalBasis + "；原候选人为空，按 FALLBACK_ROLE 兜底角色 "
                + rule.getFallbackRoleCode() + " 命中 " + fallbackUsers.size() + " 人";
        return fallbackUsers.isEmpty()
                ? ResolvedAssignees.blocked(fallbackBasis + "，仍为空，按 BLOCK 策略阻塞待运维重分配")
                : ResolvedAssignees.toWorkflowAdmin(fallbackUsers, fallbackBasis);
    }
}
