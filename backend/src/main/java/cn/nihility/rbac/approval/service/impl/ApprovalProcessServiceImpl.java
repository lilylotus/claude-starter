package cn.nihility.rbac.approval.service.impl;

import cn.nihility.rbac.approval.dto.ApprovalProcessInstance;
import cn.nihility.rbac.approval.service.ApprovalProcessService;
import cn.nihility.rbac.common.exception.BusinessException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 基于 Flowable RuntimeService/TaskService 的审批流程操作实现。
 */
@Service
@RequiredArgsConstructor
public class ApprovalProcessServiceImpl implements ApprovalProcessService {

    /** BPMN 流程定义 key。 */
    private static final String PROCESS_DEFINITION_KEY = "masterDataApprovalProcess";

    /** Flowable 运行时服务。 */
    private final RuntimeService runtimeService;

    /** Flowable 用户任务服务。 */
    private final TaskService taskService;

    /**
     * {@inheritDoc}
     */
    @Override
    public ApprovalProcessInstance start(Long requestId) {
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
                PROCESS_DEFINITION_KEY,
                requestId.toString(),
                Map.of("requestId", requestId));
        Task task = taskService.createTaskQuery()
                .processInstanceId(processInstance.getId())
                .singleResult();
        if (task == null) {
            throw new BusinessException("审批流程未生成待处理任务");
        }
        return new ApprovalProcessInstance(processInstance.getId(), task.getId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void complete(String taskId, Long approverId, boolean approved) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new BusinessException("审批任务不存在或已处理");
        }
        taskService.claim(taskId, approverId.toString());
        taskService.complete(taskId, Map.of("approved", approved));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void terminate(String processInstanceId) {
        if (!StringUtils.hasText(processInstanceId)) {
            return;
        }
        ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        if (processInstance != null) {
            runtimeService.deleteProcessInstance(processInstanceId, "申请人撤回审批申请");
        }
    }
}
