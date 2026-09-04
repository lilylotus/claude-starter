package cn.nihility.rbac.workflow.assignee;

import cn.nihility.rbac.user.service.PositionService;
import cn.nihility.rbac.workflow.constant.AssigneeType;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 指定岗位审批人解析器：按 {@code assigneeValue} 指定的岗位类型编码查询当前状态启用的任职
 * 用户（本项目 schema 未落地独立的"岗位"主数据表，"岗位编码"实际对应
 * {@link cn.nihility.rbac.user.entity.UserPositionEntity#getPositionType()} 任职类型编码，
 * 见 {@link PositionService#findActiveUserIdsByPositionType(String)}，
 * production-approval-lifecycle change tasks.md 5.3）。
 */
@Component
@RequiredArgsConstructor
public class PositionAssigneeResolver implements AssigneeResolver {

    /** 任职管理业务逻辑接口，复用其对 {@code tab_user_position} 的查询能力，不在本类直接写
     *  SQL。 */
    private final PositionService positionService;

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
        return positionService.findActiveUserIdsByPositionType(context.assigneeValue());
    }
}
