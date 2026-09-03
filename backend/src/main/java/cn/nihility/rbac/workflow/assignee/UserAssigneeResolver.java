package cn.nihility.rbac.workflow.assignee;

import cn.nihility.rbac.workflow.constant.AssigneeType;
import java.util.HashSet;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 指定人员审批人解析器：{@code assigneeValue} 为一个或多个（逗号分隔）用户 id。
 */
@Slf4j
@Component
public class UserAssigneeResolver implements AssigneeResolver {

    /**
     * {@inheritDoc}
     */
    @Override
    public AssigneeType supportedType() {
        return AssigneeType.USER;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<Long> resolve(AssigneeResolveContext context) {
        String value = context.assigneeValue();
        if (!StringUtils.hasText(value)) {
            return Set.of();
        }
        Set<Long> userIds = new HashSet<>();
        for (String part : value.split(",")) {
            if (!StringUtils.hasText(part)) {
                continue;
            }
            try {
                userIds.add(Long.valueOf(part.trim()));
            } catch (NumberFormatException ex) {
                log.warn("USER 类型审批人规则配置非法，无法解析为用户 id：{}", part);
            }
        }
        return userIds;
    }
}
