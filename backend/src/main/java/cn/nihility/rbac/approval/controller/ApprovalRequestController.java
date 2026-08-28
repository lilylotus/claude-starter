package cn.nihility.rbac.approval.controller;

import cn.nihility.rbac.approval.dto.ApprovalOpinionRequest;
import cn.nihility.rbac.approval.dto.ApprovalRejectRequest;
import cn.nihility.rbac.approval.dto.ApprovalRequestVO;
import cn.nihility.rbac.approval.dto.ApprovalSubmitRequest;
import cn.nihility.rbac.approval.dto.WriteOperationResultVO;
import cn.nihility.rbac.approval.service.ApprovalRequestService;
import cn.nihility.rbac.approval.service.ApprovalSwitchService;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.common.result.PageResult;
import cn.nihility.rbac.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 主数据变更审批申请接口。
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "审批管理", description = "主数据变更申请提交、审批、撤回和查询接口")
public class ApprovalRequestController {

    /** 审批申请业务接口。 */
    private final ApprovalRequestService approvalRequestService;

    /** 审批开关业务接口。 */
    private final ApprovalSwitchService approvalSwitchService;

    /**
     * 通过通用入口提交主数据写操作。
     *
     * @param request 提交请求
     * @return 写操作结果
     */
    @Operation(summary = "提交审批申请", description = "仅用于已启用审批的业务对象；未启用时请调用原业务写接口")
    @PostMapping("/api/approval-requests")
    public WriteOperationResultVO<?> submit(@Valid @RequestBody ApprovalSubmitRequest request) {
        if (!approvalSwitchService.isEnabled(request.getBizType())) {
            throw new BusinessException("当前业务对象未启用审批，请调用原业务接口");
        }
        return approvalRequestService.submit(request);
    }

    /**
     * 审批通过。
     *
     * @param id      申请 id
     * @param request 审批意见请求
     * @return 无业务数据的成功响应
     */
    @Operation(summary = "审批通过")
    @PostMapping("/api/approval-requests/{id}/approve")
    public Result<Void> approve(
            @Parameter(description = "审批申请 id", required = true) @PathVariable Long id,
            @Valid @RequestBody(required = false) ApprovalOpinionRequest request) {
        approvalRequestService.approve(id, request == null ? null : request.getOpinion());
        return Result.success();
    }

    /**
     * 审批拒绝。
     *
     * @param id      申请 id
     * @param request 拒绝意见请求
     * @return 无业务数据的成功响应
     */
    @Operation(summary = "审批拒绝")
    @PostMapping("/api/approval-requests/{id}/reject")
    public Result<Void> reject(
            @Parameter(description = "审批申请 id", required = true) @PathVariable Long id,
            @Valid @RequestBody ApprovalRejectRequest request) {
        approvalRequestService.reject(id, request.getOpinion());
        return Result.success();
    }

    /**
     * 撤回本人提交的待审批申请。
     *
     * @param id 申请 id
     * @return 无业务数据的成功响应
     */
    @Operation(summary = "撤回审批申请")
    @PostMapping("/api/approval-requests/{id}/cancel")
    public Result<Void> cancel(
            @Parameter(description = "审批申请 id", required = true) @PathVariable Long id) {
        approvalRequestService.cancel(id);
        return Result.success();
    }

    /**
     * 分页查询当前用户提交的申请。
     */
    @Operation(summary = "查询我的申请")
    @GetMapping("/api/approval-requests/mine")
    public PageResult<ApprovalRequestVO> mine(
            @RequestParam(required = false) String bizType,
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        return approvalRequestService.pageMine(bizType, operationType, status, page, pageSize);
    }

    /**
     * 分页查询全部待审批申请。
     */
    @Operation(summary = "查询待我审批")
    @GetMapping("/api/approval-requests/pending")
    public PageResult<ApprovalRequestVO> pending(
            @RequestParam(required = false) String bizType,
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        return approvalRequestService.pagePending(bizType, operationType, page, pageSize);
    }
}
