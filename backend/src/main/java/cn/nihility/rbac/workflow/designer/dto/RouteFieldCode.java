package cn.nihility.rbac.workflow.designer.dto;

/**
 * 条件分支引用的路由字段标识：业务对象类型 + 字段标识（fieldCode）。流程模型发布时从全部
 * 条件边提取去重后的清单以 JSON 数组形式落库到
 * {@code tab_wf_process_definition.route_field_codes} 列，运行时审批提交发起流程实例时据此
 * 从表单数据取值转换为 Flowable 流程变量，不需要重新解析整个 DSL 快照
 * （workflow-condition-payload-fields change design.md Decision 1）。
 *
 * @param bizType   字段所属业务对象类型：{@code ORG}/{@code USER}/{@code POSITION}/{@code APP}
 * @param fieldCode 字段标识，对应 {@code FormFieldRenderItemVO#fieldCode}
 */
public record RouteFieldCode(String bizType, String fieldCode) {
}
