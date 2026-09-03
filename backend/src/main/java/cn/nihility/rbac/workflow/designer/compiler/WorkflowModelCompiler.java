package cn.nihility.rbac.workflow.designer.compiler;

import cn.nihility.rbac.workflow.designer.dto.ProcessModelDsl;
import cn.nihility.rbac.workflow.exception.WorkflowModelValidationException;

/**
 * DSL → Flowable {@code BpmnModel} 编译器：先执行结构与业务规则校验（见
 * {@link ProcessModelDslValidator}），再把 Workflow JSON DSL 编译为 Flowable BPMN 对象模型，
 * 并派生出节点审批人规则草稿（workflow-approval-engine change design.md Decision 10）。
 */
public interface WorkflowModelCompiler {

    /**
     * 编译流程模型 DSL。
     *
     * @param dsl 流程模型 DSL
     * @return 编译产物：BPMN 对象模型 + 节点审批人规则草稿列表
     * @throws WorkflowModelValidationException 结构或业务规则校验不通过时抛出
     */
    CompiledProcess compile(ProcessModelDsl dsl);
}
