package cn.nihility.rbac.workflow.assignee;

import cn.nihility.rbac.workflow.constant.ApprovalAction;
import cn.nihility.rbac.workflow.constant.AssigneeType;
import cn.nihility.rbac.workflow.entity.ApprovalRecordEntity;
import cn.nihility.rbac.workflow.mapper.ApprovalRecordMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 上一节点审批人解析器：取当前流程实例最近一条"通过"审批轨迹的操作人。流程实例上下文缺失，
 * 或此前尚无任何通过记录（如作为流程第一个节点误配置本类型）时返回空集合。
 */
@Component
@RequiredArgsConstructor
public class PreviousApproverAssigneeResolver implements AssigneeResolver {

    /** 审批轨迹数据访问接口。 */
    private final ApprovalRecordMapper approvalRecordMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    public AssigneeType supportedType() {
        return AssigneeType.PREVIOUS_APPROVER;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<Long> resolve(AssigneeResolveContext context) {
        if (context.processInstanceId() == null) {
            return Set.of();
        }
        List<ApprovalRecordEntity> records = approvalRecordMapper.selectList(
                new LambdaQueryWrapper<ApprovalRecordEntity>()
                        .eq(ApprovalRecordEntity::getProcessInstanceId, context.processInstanceId())
                        .eq(ApprovalRecordEntity::getAction, ApprovalAction.APPROVE)
                        .orderByDesc(ApprovalRecordEntity::getCreateTime)
                        .orderByDesc(ApprovalRecordEntity::getId)
                        .last("LIMIT 1"));
        if (records.isEmpty() || records.get(0).getOperatorId() == null) {
            return Set.of();
        }
        return Set.of(records.get(0).getOperatorId());
    }
}
