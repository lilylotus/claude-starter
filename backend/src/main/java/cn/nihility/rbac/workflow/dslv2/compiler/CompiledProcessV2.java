package cn.nihility.rbac.workflow.dslv2.compiler;

import cn.nihility.rbac.workflow.designer.compiler.NodeAssigneeRuleDraft;
import java.util.List;
import java.util.Map;
import org.flowable.bpmn.model.BpmnModel;

/**
 * {@link WorkflowModelCompilerV2} 编译产物：可直接用于 Flowable 部署的 {@link BpmnModel}，
 * 派生出的节点审批人规则草稿列表（复用 v1 {@link NodeAssigneeRuleDraft}，共享既有发布持久化
 * 路径），以及供审计/试运行报告使用的节点 id → BPMN activityId 映射（DSL v2 本轮节点 id 与
 * activityId 恒等，映射仍显式产出以兼容未来编译策略变化，不依赖调用方自行假设）
 * （production-approval-lifecycle change design.md Decision 3/9）。
 *
 * @param bpmnModel     编译产物 BPMN 对象模型
 * @param assigneeRules 从 {@code APPROVAL} 节点派生的节点审批人规则草稿列表
 * @param nodeMapping   节点 id → BPMN activityId 映射
 */
public record CompiledProcessV2(
        BpmnModel bpmnModel,
        List<NodeAssigneeRuleDraft> assigneeRules,
        Map<String, String> nodeMapping) {
}
