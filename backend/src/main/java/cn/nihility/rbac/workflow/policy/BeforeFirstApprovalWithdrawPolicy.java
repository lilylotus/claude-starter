package cn.nihility.rbac.workflow.policy;

import cn.nihility.rbac.workflow.constant.ApprovalAction;
import cn.nihility.rbac.workflow.entity.ApprovalRecordEntity;
import cn.nihility.rbac.workflow.mapper.ApprovalRecordMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 默认撤回策略：仅允许在流程实例尚未产生任何"通过"或"驳回"审批记录时撤回，一旦已有审批
 * 记录即拒绝撤回（workflow-approval-engine change design.md Decision 7）。是否为流程发起人
 * 本人的校验由调用方（{@code FlowableWorkflowService.withdraw}）负责，本类只判断"是否已有
 * 审批记录"这一条件。
 */
@Component
@RequiredArgsConstructor
public class BeforeFirstApprovalWithdrawPolicy implements WithdrawPolicy {

    /** 审批轨迹数据访问接口。 */
    private final ApprovalRecordMapper approvalRecordMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean canWithdraw(Long processInstanceId, Long operatorId) {
        if (processInstanceId == null) {
            return false;
        }
        List<ApprovalRecordEntity> records = approvalRecordMapper.selectList(
                new LambdaQueryWrapper<ApprovalRecordEntity>()
                        .eq(ApprovalRecordEntity::getProcessInstanceId, processInstanceId)
                        .in(ApprovalRecordEntity::getAction, ApprovalAction.APPROVE, ApprovalAction.REJECT)
                        .last("LIMIT 1"));
        return records.isEmpty();
    }
}
