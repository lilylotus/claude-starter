package cn.nihility.rbac.workflow.designer.controller;

import cn.nihility.rbac.auth.context.CurrentUserContext;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.common.result.Result;
import cn.nihility.rbac.workflow.designer.dto.ProcessDefinitionVersionVO;
import cn.nihility.rbac.workflow.designer.dto.CreateProcessModelRequest;
import cn.nihility.rbac.workflow.designer.dto.CopyProcessModelRequest;
import cn.nihility.rbac.workflow.designer.dto.ProcessModelVO;
import cn.nihility.rbac.workflow.designer.dto.PublishResultVO;
import cn.nihility.rbac.workflow.designer.dto.SaveDraftRequest;
import cn.nihility.rbac.workflow.designer.dto.SetModelEnabledRequest;
import cn.nihility.rbac.workflow.designer.service.WorkflowProcessModelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 流程模型草稿/发布/下线/启用/版本历史接口（workflow-approval-engine change design.md
 * Decision 11）。权限门控通过 {@code IdentityAuthFilter} 依据请求头 {@code menu} 编码统一
 * 校验（{@code WorkflowDesign:model:view/edit/publish/disable}，见 权限资源.txt），本层
 * 不重复声明权限注解。
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "流程设计器", description = "流程模型草稿保存/发布/下线/启用/版本历史接口")
public class WorkflowProcessModelController {

    /** 流程模型生命周期业务逻辑接口。 */
    private final WorkflowProcessModelService workflowProcessModelService;

    /** 查询流程模型列表。 */
    @Operation(summary = "查询流程模型列表")
    @GetMapping("/api/workflow/process-models")
    public Result<List<ProcessModelVO>> listModels() {
        return Result.success(workflowProcessModelService.listModels());
    }

    /** 查询流程模型详情。 */
    @Operation(summary = "查询流程模型详情")
    @GetMapping("/api/workflow/process-models/{id}")
    public Result<ProcessModelVO> getModel(@PathVariable Long id) {
        return Result.success(workflowProcessModelService.getModel(id));
    }

    /** 创建流程模型草稿。 */
    @Operation(summary = "创建流程模型")
    @PostMapping("/api/workflow/process-models")
    public Result<ProcessModelVO> createModel(@Valid @RequestBody CreateProcessModelRequest request) {
        return Result.success(workflowProcessModelService.createModel(
                request.getProcessCode(), request.getProcessName(), requireCurrentUserId()));
    }

    /** 复制一个流程模型到新草稿，不复制任何发布定义。 */
    @Operation(summary = "复制流程模型")
    @PostMapping("/api/workflow/process-models/{id}/copy")
    public Result<ProcessModelVO> copyModel(
            @PathVariable Long id,
            @Valid @RequestBody CopyProcessModelRequest request) {
        return Result.success(workflowProcessModelService.copyModel(
                id, request.getProcessCode(), request.getProcessName(), requireCurrentUserId()));
    }

    /**
     * 保存流程模型草稿：仅更新草稿内容，不触发部署，不影响运行中版本。对应权限点
     * {@code WorkflowDesign:model:edit}。
     *
     * @param id      流程模型 id
     * @param request 请求体
     * @return 无业务数据的成功响应
     */
    @Operation(summary = "保存流程模型草稿")
    @PutMapping("/api/workflow/process-models/{id}/draft")
    public Result<Void> saveDraft(
            @Parameter(description = "流程模型 id", required = true) @PathVariable Long id,
            @Valid @RequestBody SaveDraftRequest request) {
        workflowProcessModelService.saveDraft(id, request.getModelJson(), request.getExpectedRevision());
        return Result.success();
    }

    /**
     * 发布流程模型：编译当前草稿并部署为一个新的不可变版本。对应权限点
     * {@code WorkflowDesign:model:publish}。
     *
     * @param id 流程模型 id
     * @return 发布结果
     */
    @Operation(summary = "发布流程模型")
    @PostMapping("/api/workflow/process-models/{id}/publish")
    public Result<PublishResultVO> publish(
            @Parameter(description = "流程模型 id", required = true) @PathVariable Long id) {
        return Result.success(workflowProcessModelService.publish(id, requireCurrentUserId()));
    }

    /**
     * 下线流程模型当前生效版本：拒绝新发起，不影响运行中实例。对应权限点
     * {@code WorkflowDesign:model:disable}。
     *
     * @param id 流程模型 id
     * @return 无业务数据的成功响应
     */
    @Operation(summary = "下线流程模型")
    @PostMapping("/api/workflow/process-models/{id}/disable")
    public Result<Void> disable(@Parameter(description = "流程模型 id", required = true) @PathVariable Long id) {
        workflowProcessModelService.disable(id);
        return Result.success();
    }

    /**
     * 重新启用流程模型当前生效版本。对应权限点 {@code WorkflowDesign:model:disable}
     * （与下线复用同一权限点，两者是同一按钮的启/停两态）。
     *
     * @param id 流程模型 id
     * @return 无业务数据的成功响应
     */
    @Operation(summary = "重新启用流程模型")
    @PostMapping("/api/workflow/process-models/{id}/enable")
    public Result<Void> enable(@Parameter(description = "流程模型 id", required = true) @PathVariable Long id) {
        workflowProcessModelService.enable(id);
        return Result.success();
    }

    /**
     * 查询流程模型版本历史（按版本号倒序）。对应权限点 {@code WorkflowDesign:model:view}。
     *
     * @param id 流程模型 id
     * @return 版本历史列表
     */
    @Operation(summary = "查询流程模型版本历史")
    @GetMapping("/api/workflow/process-models/{id}/versions")
    public Result<List<ProcessDefinitionVersionVO>> versions(
            @Parameter(description = "流程模型 id", required = true) @PathVariable Long id) {
        return Result.success(workflowProcessModelService.listVersions(id));
    }

    /**
     * 独立设置流程模型是否接受新发起，与 {@link #disable}/{@link #enable}（版本级下线/重新
     * 上线）语义解耦：本接口只影响"是否接受新发起"，不改动 {@code status}/
     * {@code currentDefinitionId}，也不挂起/激活 Flowable 流程定义（tasks.md 4.6"模型级
     * 启停"）。复用 {@code WorkflowDesign:model:disable} 权限点（同一批"能操作流程模型上下线
     * 相关开关"的运维人员）。
     *
     * @param id      流程模型 id
     * @param request 请求体，携带目标启用状态
     * @return 无业务数据的成功响应
     */
    @Operation(summary = "设置流程模型是否接受新发起", description = "与版本级下线/重新上线语义解耦，仅控制 tab_wf_process_model.enabled")
    @PostMapping("/api/workflow/process-models/{id}/enabled")
    public Result<Void> setModelEnabled(
            @Parameter(description = "流程模型 id", required = true) @PathVariable Long id,
            @Valid @RequestBody SetModelEnabledRequest request) {
        workflowProcessModelService.setModelEnabled(id, Boolean.TRUE.equals(request.getEnabled()), requireCurrentUserId());
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
