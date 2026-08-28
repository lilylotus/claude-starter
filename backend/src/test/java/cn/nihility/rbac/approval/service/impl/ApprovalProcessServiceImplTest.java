package cn.nihility.rbac.approval.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.approval.dto.ApprovalProcessInstance;
import java.util.Map;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.runtime.ProcessInstanceQuery;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link ApprovalProcessServiceImpl} 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class ApprovalProcessServiceImplTest {

    @Mock
    private RuntimeService runtimeService;

    @Mock
    private TaskService taskService;

    @Mock
    private ProcessInstance processInstance;

    @Mock
    private TaskQuery taskQuery;

    @Mock
    private Task task;

    @Mock
    private ProcessInstanceQuery processInstanceQuery;

    private ApprovalProcessServiceImpl service;

    /** 构造被测服务。 */
    @BeforeEach
    void setUp() {
        service = new ApprovalProcessServiceImpl(runtimeService, taskService);
    }

    /** 启动流程后应返回运行时流程实例和用户任务 id。 */
    @Test
    void start_shouldReturnProcessAndTaskIds() {
        when(runtimeService.startProcessInstanceByKey(
                "masterDataApprovalProcess",
                "10",
                Map.of("requestId", 10L))).thenReturn(processInstance);
        when(processInstance.getId()).thenReturn("process-1");
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.processInstanceId("process-1")).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(task);
        when(task.getId()).thenReturn("task-1");

        ApprovalProcessInstance result = service.start(10L);

        assertThat(result.processInstanceId()).isEqualTo("process-1");
        assertThat(result.taskId()).isEqualTo("task-1");
    }

    /** 完成审批任务时应认领任务并传入 approved 流程变量。 */
    @Test
    void complete_shouldClaimAndCompleteTask() {
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.taskId("task-1")).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(task);

        service.complete("task-1", 2L, true);

        verify(taskService).claim("task-1", "2");
        verify(taskService).complete("task-1", Map.of("approved", true));
    }

    /** 撤回时应终止仍在运行的流程实例。 */
    @Test
    void terminate_shouldDeleteRunningProcessInstance() {
        when(runtimeService.createProcessInstanceQuery()).thenReturn(processInstanceQuery);
        when(processInstanceQuery.processInstanceId("process-1")).thenReturn(processInstanceQuery);
        when(processInstanceQuery.singleResult()).thenReturn(processInstance);

        service.terminate("process-1");

        verify(runtimeService).deleteProcessInstance("process-1", "申请人撤回审批申请");
    }
}
