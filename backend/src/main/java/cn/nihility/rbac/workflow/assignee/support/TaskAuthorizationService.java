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
