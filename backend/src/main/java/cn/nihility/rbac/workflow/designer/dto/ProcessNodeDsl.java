package cn.nihility.rbac.workflow.designer.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Getter;
import lombok.Setter;

/**
 * Workflow JSON DSL 节点基类，只暴露"开始/审批/条件/结束"四种业务语言节点类型，不向使用者
 * 暴露 BPMN 原生概念（workflow-approval-engine change design.md Decision 9）。按
 * {@code type} 字段做多态反序列化，具体类型见 {@link StartNodeDsl}/{@link ApprovalNodeDsl}/
 * {@link ConditionNodeDsl}/{@link EndNodeDsl}。
 */
@Getter
@Setter
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type", visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = StartNodeDsl.class, name = "START"),
        @JsonSubTypes.Type(value = ApprovalNodeDsl.class, name = "APPROVAL"),
        @JsonSubTypes.Type(value = ConditionNodeDsl.class, name = "CONDITION"),
        @JsonSubTypes.Type(value = EndNodeDsl.class, name = "END")
})
public abstract class ProcessNodeDsl {

    /** 节点标识，同一流程模型内唯一。 */
    private String id;

    /** 节点类型字面量：{@code START}/{@code APPROVAL}/{@code CONDITION}/{@code END}。 */
    private String type;

    /** 节点展示名称，开始/结束/条件节点可为空。 */
    private String name;
}
