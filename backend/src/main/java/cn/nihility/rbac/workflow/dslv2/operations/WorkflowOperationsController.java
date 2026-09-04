package cn.nihility.rbac.workflow.dslv2.operations;

import cn.nihility.rbac.common.result.Result;
import cn.nihility.rbac.workflow.dto.ProcessInstanceExceptionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审批引擎运维查询接口（production-approval-lifecycle change tasks.md 5.4"空人可见"）。
 * 权限门控通过 {@code IdentityAuthFilter} 依据请求头 {@code menu} 编码统一校验，复用
 * {@code WorkflowDesign:model:view}（见 权限资源.txt 说明：与流程模型只读查看同属"流程设计
 * 只读查看"范畴，未单独登记新权限点），本层不重复声明权限注解。
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "流程运维查询", description = "空审批人待分配等运维异常查询接口")
public class WorkflowOperationsController {

    /** 审批引擎运维查询业务逻辑接口。 */
    private final WorkflowOperationsService workflowOperationsService;

    /** 查询当前处于空审批人待分配状态的流程实例列表。 */
    @Operation(summary = "查询空审批人待分配流程实例列表")
    @GetMapping("/api/v1/workflow/operations/exceptions")
    public Result<List<ProcessInstanceExceptionVO>> listAssigneeEmptyInstances() {
        return Result.success(workflowOperationsService.listAssigneeEmptyInstances());
    }
}
