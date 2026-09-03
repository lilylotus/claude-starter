package cn.nihility.rbac.workflow.assignee;

import cn.nihility.rbac.workflow.constant.AssigneeType;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 流程发起人审批人解析器：直接返回流程实例快照记录的发起人。
 */
@Component
public class InitiatorAssigneeResolver implements AssigneeResolver {

    /**
     * {@inheritDoc}
     */
    @Override
    public AssigneeType supportedType() {
        return AssigneeType.INITIATOR;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<Long> resolve(AssigneeResolveContext context) {
        return context.applicantId() == null ? Set.of() : Set.of(context.applicantId());
    }
}
