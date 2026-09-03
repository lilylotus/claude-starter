package cn.nihility.rbac.workflow.assignee;

import cn.nihility.rbac.workflow.constant.AssigneeType;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 指定岗位审批人解析器：当前项目尚未落地独立的"岗位"主数据模块，解析结果恒为空集合，
 * 交由节点配置的空审批人策略处理（workflow-approval-engine change design.md Non-Goals）。
 */
@Slf4j
@Component
public class PositionAssigneeResolver implements AssigneeResolver {

    /**
     * {@inheritDoc}
     */
    @Override
    public AssigneeType supportedType() {
        return AssigneeType.POSITION;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<Long> resolve(AssigneeResolveContext context) {
        log.info("POSITION 类型审批人规则暂无岗位数据源支持，节点 {} 按空审批人处理", context.nodeId());
        return Set.of();
    }
}
