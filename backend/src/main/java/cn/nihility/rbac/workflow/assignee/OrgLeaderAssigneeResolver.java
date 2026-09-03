package cn.nihility.rbac.workflow.assignee;

import cn.nihility.rbac.workflow.assignee.support.AdminRoleLookupService;
import cn.nihility.rbac.workflow.constant.AssigneeType;
import cn.nihility.rbac.workflow.constant.WorkflowConstants;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 指定组织负责人审批人解析器。当前 schema 未提供额外的"目标组织"配置列
 * （{@code tab_wf_node_assignee_rule} 只有一个 {@code assignee_value} 承载"要求的管理员角色
 * 编码"，见 design.md Decision 5），因此本实现的"目标组织"取自流程实例快照的发起人所属
 * 组织，与 {@link ApplicantDeptLeaderAssigneeResolver} 算法一致——这是本次实现在 schema
 * 约束下的既定假设，两者作为独立的 {@link AssigneeType} 枚举值保留，供后续如需支持"指定
 * 任意具体组织"时再扩展规则表结构、改写本类。
 */
@Component
@RequiredArgsConstructor
public class OrgLeaderAssigneeResolver implements AssigneeResolver {

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
        if (context.applicantOrgId() == null) {
            return Set.of();
        }
        String roleCode = StringUtils.hasText(context.assigneeValue())
                ? context.assigneeValue()
                : WorkflowConstants.DEFAULT_ORG_LEADER_ROLE_CODE;
        return adminRoleLookupService.findOrgLeaderUserIds(context.applicantOrgId(), roleCode);
    }
}
