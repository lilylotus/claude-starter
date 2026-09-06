package cn.nihility.rbac.workflow.assignee.support;

import cn.nihility.rbac.workflow.constant.CandidateType;
import cn.nihility.rbac.workflow.entity.ApprovalTaskCandidateEntity;
import cn.nihility.rbac.workflow.entity.ApprovalTaskEntity;
import cn.nihility.rbac.workflow.mapper.ApprovalTaskCandidateMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 任务处理越权校验服务：完成、转办、委派、加签、退回前均需校验当前操作人满足
 * {@code assignee}/{@code candidateUser}/{@code candidateGroup} 三维度中至少一项
 * （workflow-approval-engine change design.md Decision 6，spec.md "任务处理越权校验"
 * Requirement）。抽成独立组件，便于脱离 Flowable 引擎单元测试。
 */
@Component
@RequiredArgsConstructor
public class TaskAuthorizationService {

    /** 审批任务候选人明细数据访问接口。 */
    private final ApprovalTaskCandidateMapper approvalTaskCandidateMapper;

    /** 管理员角色查询辅助组件，用于 {@code candidateGroup} 角色维度匹配。 */
    private final AdminRoleLookupService adminRoleLookupService;

    /**
     * 判断当前操作人是否有权处理该任务。
     *
     * @param task       审批任务
     * @param operatorId 操作人用户 id
     * @return 是否有权处理
     */
    public boolean isAuthorized(ApprovalTaskEntity task, Long operatorId) {
        if (Objects.equals(task.getAssigneeId(), operatorId)) {
            return true;
        }
        if (task.getAssigneeId() != null) {
            // 任务已经分配给他人（认领、审批人解析直接命中单人、转办、委派均会写入
            // assigneeId）：候选人身份仅对"未分配"任务有权（design.md 第8节"候选人只对未分配
            // 任务有权；认领后原候选人不能抢着完成"），此处必须直接拒绝，不能再落到下面的候选人
            // 表查询——否则任何仍留在 tab_wf_approval_task_candidate 里的原候选人（认领/分配后
            // 从不清理该表）都能对一个已经属于别人的任务发起 transfer/delegate/addSign/return
            // 等操作，是真实越权（tasks.md 6.5，此前实现遗漏）。
            return false;
        }
        boolean userCandidateHit = approvalTaskCandidateMapper.exists(new LambdaQueryWrapper<ApprovalTaskCandidateEntity>()
                .eq(ApprovalTaskCandidateEntity::getTaskId, task.getId())
                .eq(ApprovalTaskCandidateEntity::getCandidateType, CandidateType.USER)
                .eq(ApprovalTaskCandidateEntity::getCandidateValue, String.valueOf(operatorId)));
        if (userCandidateHit) {
            return true;
        }
        List<ApprovalTaskCandidateEntity> roleCandidates = approvalTaskCandidateMapper.selectList(
                new LambdaQueryWrapper<ApprovalTaskCandidateEntity>()
                        .eq(ApprovalTaskCandidateEntity::getTaskId, task.getId())
                        .eq(ApprovalTaskCandidateEntity::getCandidateType, CandidateType.ROLE));
        for (ApprovalTaskCandidateEntity candidate : roleCandidates) {
            if (adminRoleLookupService.userHasRoleCode(operatorId, candidate.getCandidateValue())) {
                return true;
            }
        }
        return false;
    }
}
