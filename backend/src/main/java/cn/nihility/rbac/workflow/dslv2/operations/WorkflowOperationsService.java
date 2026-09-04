package cn.nihility.rbac.workflow.dslv2.operations;

import cn.nihility.rbac.workflow.dto.ProcessInstanceExceptionVO;
import java.util.List;

/**
 * 审批引擎运维查询业务逻辑接口（production-approval-lifecycle change tasks.md 5.4"空人
 * 可见"）。
 */
public interface WorkflowOperationsService {

    /**
     * 查询当前处于 {@code exceptionCode=ASSIGNEE_EMPTY} 状态（空审批人待分配）的流程实例
     * 列表，供运维排查、定位后续需要 {@code WorkflowV2ReassignmentService#reassign} 处理的
     * 目标。
     *
     * @return 异常流程实例列表，按启动时间升序排列
     */
    List<ProcessInstanceExceptionVO> listAssigneeEmptyInstances();
}
