package cn.nihility.rbac.workflow.controller;

import cn.nihility.rbac.auth.context.CurrentUserContext;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.common.result.Result;
import cn.nihility.rbac.workflow.dto.AddSignCommand;
import cn.nihility.rbac.workflow.dto.AddSignRequest;
import cn.nihility.rbac.workflow.dto.ApproveCommand;
import cn.nihility.rbac.workflow.dto.ApprovalTaskVO;
import cn.nihility.rbac.workflow.dto.ApproveRequest;
import cn.nihility.rbac.workflow.dto.DelegateCommand;
import cn.nihility.rbac.workflow.dto.DelegateRequest;
import cn.nihility.rbac.workflow.dto.DisagreeCommand;
import cn.nihility.rbac.workflow.dto.DisagreeRequest;
import cn.nihility.rbac.workflow.dto.ProcessInstanceDetailVO;
import cn.nihility.rbac.workflow.dto.RejectCommand;
import cn.nihility.rbac.workflow.dto.RejectRequest;
import cn.nihility.rbac.workflow.dto.ReturnTaskCommand;
import cn.nihility.rbac.workflow.dto.ReturnTaskRequest;
import cn.nihility.rbac.workflow.dto.TaskQuery;
import cn.nihility.rbac.workflow.dto.TransferCommand;
import cn.nihility.rbac.workflow.dto.TransferRequest;
import cn.nihility.rbac.workflow.dto.WithdrawCommand;
import cn.nihility.rbac.workflow.dto.WithdrawRequest;
import cn.nihility.rbac.workflow.engine.WorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Workflow 引擎待办/已办查询与通用任务处理接口。审批类写操作的幂等键取自
 * {@code X-Request-Id} 请求头，可为空（workflow-approval-engine change design.md
 * Decision 6）。
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "审批引擎", description = "Workflow 引擎待办/已办查询与审批任务处理接口")
public class WorkflowTaskController {

    /** Workflow 引擎抽象接口。 */
    private final WorkflowService workflowService;

    /**
     * 查询我的待办。
     *
     * @param businessType 业务对象类型过滤，可为空
     * @param page         页码
     * @param pageSize     每页大小
     * @return 待办任务列表
     */
    @Operation(summary = "查询我的待办")
    @GetMapping("/api/v1/workflow/tasks/todo")
    public Result<List<ApprovalTaskVO>> todo(
            @RequestParam(required = false) String businessType,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        return Result.success(workflowService.findTodoTasks(requireCurrentUserId(),
                new TaskQuery(businessType, page, pageSize)));
    }

    /**
     * 查询我的已办。
     *
     * @param businessType 业务对象类型过滤，可为空
     * @param page         页码
     * @param pageSize     每页大小
     * @return 已办任务列表
     */
    @Operation(summary = "查询我的已办")
    @GetMapping("/api/v1/workflow/tasks/done")
    public Result<List<ApprovalTaskVO>> done(
            @RequestParam(required = false) String businessType,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        return Result.success(workflowService.findDoneTasks(requireCurrentUserId(),
                new TaskQuery(businessType, page, pageSize)));
    }

    /**
     * 查询流程实例详情。
     *
     * @param processInstanceId 流程实例 id
     * @return 流程实例详情
     */
    @Operation(summary = "查询流程实例详情")
    @GetMapping("/api/v1/workflow/process-instances/{processInstanceId}")
    public Result<ProcessInstanceDetailVO> processDetail(
            @Parameter(description = "流程实例 id", required = true) @PathVariable Long processInstanceId) {
        return Result.success(workflowService.getProcessDetail(processInstanceId));
    }

    /**
     * 审批通过。
     *
     * @param taskId    审批任务 id
     * @param request   请求体
     * @param requestId 幂等键，可为空
     * @return 无业务数据的成功响应
     */
    @Operation(summary = "审批通过")
    @PostMapping("/api/v1/workflow/tasks/{taskId}/approve")
    public Result<Void> approve(
            @Parameter(description = "审批任务 id", required = true) @PathVariable Long taskId,
            @Valid @RequestBody(required = false) ApproveRequest request,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        workflowService.approve(new ApproveCommand(taskId, requireCurrentUserId(),
                request == null ? null : request.getRemark(), requestId));
        return Result.success();
    }

    /**
     * 审批拒绝（驳回）。
     *
     * @param taskId    审批任务 id
     * @param request   请求体
     * @param requestId 幂等键，可为空
     * @return 无业务数据的成功响应
     */
    @Operation(summary = "审批拒绝")
    @PostMapping("/api/v1/workflow/tasks/{taskId}/reject")
    public Result<Void> reject(
            @Parameter(description = "审批任务 id", required = true) @PathVariable Long taskId,
            @Valid @RequestBody RejectRequest request,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        workflowService.reject(new RejectCommand(taskId, requireCurrentUserId(), request.getRemark(), requestId));
        return Result.success();
    }

    /**
     * 反对（阈值制会签节点专用反对票）：只计入反对票数，不立即终止流程，只有反对票数使该
     * 节点已不可能达到通过阈值时才会触发流程终止；用在非阈值制会签节点上会被拒绝
     * （production-approval-lifecycle change design.md 第7节，tasks.md 6.3）。
     *
     * @param taskId    审批任务 id
     * @param request   请求体
     * @param requestId 幂等键，可为空
     * @return 无业务数据的成功响应
     */
    @Operation(summary = "审批反对（阈值制会签节点反对票）")
    @PostMapping("/api/v1/workflow/tasks/{taskId}/disagree")
    public Result<Void> disagree(
            @Parameter(description = "审批任务 id", required = true) @PathVariable Long taskId,
            @Valid @RequestBody(required = false) DisagreeRequest request,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        workflowService.disagree(new DisagreeCommand(taskId, requireCurrentUserId(),
                request == null ? null : request.getRemark(), requestId));
        return Result.success();
    }

    /**
     * 退回历史节点。
     *
     * @param taskId    审批任务 id
     * @param request   请求体
     * @param requestId 幂等键，可为空
     * @return 无业务数据的成功响应
     */
    @Operation(summary = "退回历史节点")
    @PostMapping("/api/v1/workflow/tasks/{taskId}/return")
    public Result<Void> returnTask(
            @Parameter(description = "审批任务 id", required = true) @PathVariable Long taskId,
            @Valid @RequestBody ReturnTaskRequest request,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        workflowService.returnTask(new ReturnTaskCommand(taskId, requireCurrentUserId(), request.getTargetNodeId(),
                request.getRemark(), requestId));
        return Result.success();
    }

    /**
     * 转办。
     *
     * @param taskId    审批任务 id
     * @param request   请求体
     * @param requestId 幂等键，可为空
     * @return 无业务数据的成功响应
     */
    @Operation(summary = "转办")
    @PostMapping("/api/v1/workflow/tasks/{taskId}/transfer")
    public Result<Void> transfer(
            @Parameter(description = "审批任务 id", required = true) @PathVariable Long taskId,
            @Valid @RequestBody TransferRequest request,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        workflowService.transfer(new TransferCommand(taskId, requireCurrentUserId(), request.getTargetUserId(),
                request.getRemark(), requestId));
        return Result.success();
    }

    /**
     * 委派。
     *
     * @param taskId    审批任务 id
     * @param request   请求体
     * @param requestId 幂等键，可为空
     * @return 无业务数据的成功响应
     */
    @Operation(summary = "委派")
    @PostMapping("/api/v1/workflow/tasks/{taskId}/delegate")
    public Result<Void> delegate(
            @Parameter(description = "审批任务 id", required = true) @PathVariable Long taskId,
            @Valid @RequestBody DelegateRequest request,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        workflowService.delegate(new DelegateCommand(taskId, requireCurrentUserId(), request.getTargetUserId(),
                request.getRemark(), requestId));
        return Result.success();
    }

    /**
     * 加签。
     *
     * @param taskId    审批任务 id
     * @param request   请求体
     * @param requestId 幂等键，可为空
     * @return 无业务数据的成功响应
     */
    @Operation(summary = "加签")
    @PostMapping("/api/v1/workflow/tasks/{taskId}/add-sign")
    public Result<Void> addSign(
            @Parameter(description = "审批任务 id", required = true) @PathVariable Long taskId,
            @Valid @RequestBody AddSignRequest request,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        workflowService.addSign(new AddSignCommand(taskId, requireCurrentUserId(), request.getAddUserIds(),
                request.getRemark(), requestId));
        return Result.success();
    }

    /**
     * 撤回流程实例。
     *
     * @param processInstanceId 流程实例 id
     * @param request           请求体
     * @param requestId         幂等键，可为空
     * @return 无业务数据的成功响应
     */
    @Operation(summary = "撤回流程实例")
    @PostMapping("/api/v1/workflow/process-instances/{processInstanceId}/withdraw")
    public Result<Void> withdraw(
            @Parameter(description = "流程实例 id", required = true) @PathVariable Long processInstanceId,
            @Valid @RequestBody(required = false) WithdrawRequest request,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        workflowService.withdraw(new WithdrawCommand(processInstanceId, requireCurrentUserId(),
                request == null ? null : request.getRemark(), requestId));
        return Result.success();
    }

    /**
     * 读取当前登录用户 id。
     */
    private Long requireCurrentUserId() {
        Long userId = CurrentUserContext.getUserId();
        if (userId == null) {
            throw new BusinessException("当前用户未登录");
        }
        return userId;
    }
}
