package cn.nihility.rbac.workflow.assignee;

import cn.nihility.rbac.workflow.assignee.support.AdminRoleLookupService;
import cn.nihility.rbac.workflow.constant.AssigneeType;
import cn.nihility.rbac.workflow.constant.WorkflowConstants;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 发起人部门负责人审批人解析器：管辖范围覆盖发起人所属组织、且持有 {@code assigneeValue}
 * 指定角色编码（未配置时回退 {@link WorkflowConstants#DEFAULT_ORG_LEADER_ROLE_CODE}）、
 * 状态启用的管理员，即视为其"部门负责人"。
 */
@Component
@RequiredArgsConstructor
public class ApplicantDeptLeaderAssigneeResolver implements AssigneeResolver {

    /** 管理员角色查询辅助组件。 */
    private final AdminRoleLookupService adminRoleLookupService;

    /**
     * {@inheritDoc}
     */
    @Override
    public AssigneeType supportedType() {
        return AssigneeType.APPLICANT_DEPT_LEADER;
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
