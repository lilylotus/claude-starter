package cn.nihility.rbac.workflow.dslv2.simulation;

import cn.nihility.rbac.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 流程模型快速预演接口（production-approval-lifecycle change design.md 第 4 节"试运行分两层"
 * 第一层，tasks.md 4.2）。只做静态路径/人员解析解释，不接触真实引擎实例、不产生任何真实
 * 任务/流程数据；"独立测试环境真实试运行"留待后续批次实现。权限门控通过
 * {@code IdentityAuthFilter} 依据请求头 {@code menu} 编码统一校验（复用
 * {@code WorkflowDesign:model:edit}，见 权限资源.txt），本层不重复声明权限注解。
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "流程快速预演", description = "草稿/已发布版本的静态路径与审批人解析预演接口")
public class WorkflowSimulationController {

    /** 快速预演业务逻辑接口。 */
    private final WorkflowSimulationService workflowSimulationService;

    /**
     * 对流程模型草稿或指定已发布版本执行一次快速预演。
     *
     * @param id      流程模型 id
     * @param request 预演请求体
     * @return 预演报告，{@code mode} 恒为 {@code QUICK_PREVIEW}
     */
    @Operation(summary = "流程模型快速预演", description = "静态解释命中路径与各审批节点解析到的候选人，不创建真实运行数据")
    @PostMapping("/api/workflow/process-models/{id}/simulations")
    public Result<SimulationResultVO> simulate(
            @Parameter(description = "流程模型 id", required = true) @PathVariable Long id,
            @Valid @RequestBody SimulationRequest request) {
        return Result.success(workflowSimulationService.simulate(id, request));
    }
}
