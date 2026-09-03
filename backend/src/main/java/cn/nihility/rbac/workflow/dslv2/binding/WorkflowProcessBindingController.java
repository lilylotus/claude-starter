package cn.nihility.rbac.workflow.dslv2.binding;

import cn.nihility.rbac.auth.context.CurrentUserContext;
import cn.nihility.rbac.common.result.Result;
import cn.nihility.rbac.workflow.dslv2.dto.ProcessBindingRequest;
import cn.nihility.rbac.workflow.dslv2.dto.ProcessBindingVO;
import io.swagger.v3.oas.annotations.Operation;
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
 * 业务绑定管理接口（production-approval-lifecycle change design.md Decision 4/12）。权限
 * 门控通过 {@code IdentityAuthFilter} 依据请求头 {@code menu} 编码统一校验
 * （{@code WorkflowDesign:binding:view/edit}，见 权限资源.txt），本层不重复声明权限注解。
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "流程业务绑定", description = "业务绑定新建/切换版本/启停接口")
public class WorkflowProcessBindingController {

    /** 业务绑定生命周期管理服务。 */
    private final WorkflowProcessBindingService workflowProcessBindingService;

    /** 查询业务绑定列表。 */
    @Operation(summary = "查询业务绑定列表")
    @GetMapping("/api/workflow/process-bindings")
    public Result<List<ProcessBindingVO>> listBindings() {
        return Result.success(workflowProcessBindingService.listBindings());
    }

    /** 查询单条业务绑定。 */
    @Operation(summary = "查询业务绑定详情")
    @GetMapping("/api/workflow/process-bindings/{bindingId}")
    public Result<ProcessBindingVO> getBinding(@PathVariable Long bindingId) {
        return Result.success(workflowProcessBindingService.getBinding(bindingId));
    }

    /** 新建业务绑定。 */
    @Operation(summary = "新建业务绑定")
    @PostMapping("/api/workflow/process-bindings")
    public Result<ProcessBindingVO> createBinding(@Valid @RequestBody ProcessBindingRequest request) {
        return Result.success(workflowProcessBindingService.createBinding(request, requireCurrentUserId()));
    }

    /** 切换业务绑定指向的流程定义版本（含显式回滚）。 */
    @Operation(summary = "切换业务绑定版本")
    @PutMapping("/api/workflow/process-bindings/{bindingId}")
    public Result<ProcessBindingVO> switchDefinition(
            @PathVariable Long bindingId, @Valid @RequestBody ProcessBindingRequest request) {
        return Result.success(workflowProcessBindingService.switchDefinition(bindingId, request, requireCurrentUserId()));
    }

    /** 启用业务绑定。 */
    @Operation(summary = "启用业务绑定")
    @PostMapping("/api/workflow/process-bindings/{bindingId}/enable")
    public Result<Void> enable(@PathVariable Long bindingId) {
        workflowProcessBindingService.setEnabled(bindingId, true, requireCurrentUserId());
        return Result.success();
    }

    /** 禁用业务绑定。 */
    @Operation(summary = "禁用业务绑定")
    @PostMapping("/api/workflow/process-bindings/{bindingId}/disable")
    public Result<Void> disable(@PathVariable Long bindingId) {
        workflowProcessBindingService.setEnabled(bindingId, false, requireCurrentUserId());
        return Result.success();
    }

    /** 读取当前登录用户 id。 */
    private Long requireCurrentUserId() {
        return CurrentUserContext.getUserId();
    }
}
