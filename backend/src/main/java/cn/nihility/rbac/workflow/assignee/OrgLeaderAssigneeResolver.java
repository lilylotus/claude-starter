package cn.nihility.rbac.workflow.assignee;

import cn.nihility.rbac.workflow.assignee.support.AdminRoleLookupService;
import cn.nihility.rbac.workflow.constant.AssigneeType;
import cn.nihility.rbac.workflow.constant.WorkflowConstants;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 指定组织负责人审批人解析器。目标组织按节点规则的 {@code assigneeOrgSource} 决定：
 * {@code FIXED_ORG} 时使用规则配置的固定目标组织（{@code context.targetOrgId()}，供"指定
 * 固定组织管理员审批"场景使用，如"test 组织下新增需要 test 组织下的管理员审批"）；未配置
 * 或为 {@code APPLICANT_SNAPSHOT}（默认）时保持既有行为，取流程实例快照的发起人所属组织
 * （{@code context.applicantOrgId()}），与 {@link ApplicantDeptLeaderAssigneeResolver} 的
 * 默认算法一致（production-approval-lifecycle change tasks.md 5.3）。
 */
@Component
@RequiredArgsConstructor
public class OrgLeaderAssigneeResolver implements AssigneeResolver {

    /** {@code orgSource} 固定目标组织取值，见 {@link AssigneeResolveContext#orgSource()}。 */
    private static final String ORG_SOURCE_FIXED_ORG = "FIXED_ORG";

    /** 管理员角色查询辅助组件。 */
    private final AdminRoleLookupService adminRoleLookupService;

    /**
     * {@inheritDoc}
     */
    @Override
    public AssigneeType supportedType() {
        return AssigneeType.ORG_LEADER;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<Long> resolve(AssigneeResolveContext context) {
        Long targetOrgId = ORG_SOURCE_FIXED_ORG.equals(context.orgSource())
                ? context.targetOrgId()
                : context.applicantOrgId();
        if (targetOrgId == null) {
            return Set.of();
        }
        String roleCode = StringUtils.hasText(context.assigneeValue())
                ? context.assigneeValue()
                : WorkflowConstants.DEFAULT_ORG_LEADER_ROLE_CODE;
        return adminRoleLookupService.findOrgLeaderUserIds(targetOrgId, roleCode);
    }
}
