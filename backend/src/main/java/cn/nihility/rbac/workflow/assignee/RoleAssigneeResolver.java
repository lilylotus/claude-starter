package cn.nihility.rbac.workflow.assignee;

import cn.nihility.rbac.workflow.assignee.support.AdminRoleLookupService;
import cn.nihility.rbac.workflow.constant.AssigneeType;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 指定角色审批人解析器：{@code assigneeValue} 为角色编码，解析为当前持有该角色、状态启用的
 * 全部管理员关联用户 id。
 */
@Component
@RequiredArgsConstructor
public class RoleAssigneeResolver implements AssigneeResolver {

    /** 管理员角色查询辅助组件。 */
    private final AdminRoleLookupService adminRoleLookupService;

    /**
     * {@inheritDoc}
     */
    @Override
    public AssigneeType supportedType() {
        return AssigneeType.ROLE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<Long> resolve(AssigneeResolveContext context) {
        return adminRoleLookupService.findUserIdsByRoleCode(context.assigneeValue());
    }
}
