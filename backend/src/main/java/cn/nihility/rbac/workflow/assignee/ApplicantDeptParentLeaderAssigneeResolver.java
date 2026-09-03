package cn.nihility.rbac.workflow.assignee;

import cn.nihility.rbac.workflow.assignee.support.AdminRoleLookupService;
import cn.nihility.rbac.workflow.constant.AssigneeType;
import cn.nihility.rbac.workflow.constant.WorkflowConstants;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 发起人部门上级负责人审批人解析器：发起人所属组织没有可解析出的负责人时，沿组织路径向上
 * 查找，取第一个能解析出负责人的上级组织对应的用户。
 */
@Component
@RequiredArgsConstructor
public class ApplicantDeptParentLeaderAssigneeResolver implements AssigneeResolver {

    /** 管理员角色查询辅助组件。 */
    private final AdminRoleLookupService adminRoleLookupService;

    /**
     * {@inheritDoc}
     */
    @Override
    public AssigneeType supportedType() {
        return AssigneeType.APPLICANT_DEPT_PARENT_LEADER;
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
        return adminRoleLookupService.findParentOrgLeaderUserIds(context.applicantOrgId(), roleCode);
    }
}
