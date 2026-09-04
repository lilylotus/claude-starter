package cn.nihility.rbac.workflow.dslv2.binding;

import cn.nihility.rbac.workflow.entity.ProcessBindingEntity;
import cn.nihility.rbac.workflow.entity.ProcessDefinitionEntity;

/**
 * {@link ProcessBindingResolutionService#resolveForStart} 的解析结果：命中且已通过启动前
 * 全部校验（绑定启用、流程定义已发布、所属模型接受新发起、执行模式非
 * {@code RELIABLE_ASYNC}）的业务绑定及其指向的流程定义（production-approval-lifecycle
 * change design.md Decision 4）。调用方据此构造
 * {@link cn.nihility.rbac.workflow.dto.StartProcessCommand} 并委托
 * {@link cn.nihility.rbac.workflow.engine.WorkflowService#start} 启动。
 *
 * @param binding    命中的业务绑定
 * @param definition 绑定指向的、已确认为已发布状态的流程定义
 */
public record ResolvedProcessBinding(ProcessBindingEntity binding, ProcessDefinitionEntity definition) {
}
