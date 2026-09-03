package cn.nihility.rbac.workflow.dslv2.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Getter;
import lombok.Setter;

/**
 * DSL v2 节点基类，在 v1 四种业务语言节点（开始/审批/条件/结束）基础上新增"并行分叉"
 * "并行汇合""抄送""自动任务"，仍不向使用者暴露 BPMN 原生概念
 * （production-approval-lifecycle change design.md Decision 3）。按 {@code type} 字段做
 * 多态反序列化。
 */
@Getter
@Setter
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type", visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = StartNodeDslV2.class, name = "START"),
        @JsonSubTypes.Type(value = ApprovalNodeDslV2.class, name = "APPROVAL"),
        @JsonSubTypes.Type(value = ConditionNodeDslV2.class, name = "CONDITION"),
        @JsonSubTypes.Type(value = ParallelSplitNodeDslV2.class, name = "PARALLEL_SPLIT"),
        @JsonSubTypes.Type(value = ParallelJoinNodeDslV2.class, name = "PARALLEL_JOIN"),
        @JsonSubTypes.Type(value = CcNodeDslV2.class, name = "CC"),
        @JsonSubTypes.Type(value = AutoNodeDslV2.class, name = "AUTO"),
        @JsonSubTypes.Type(value = EndNodeDslV2.class, name = "END")
})
public abstract class ProcessNodeDslV2 {

    /** 节点标识，同一流程模型内唯一。 */
    private String id;

    /** 节点类型字面量。 */
    private String type;

    /** 节点展示名称。 */
    private String name;

    /** 画布坐标，仅前端往返使用，不参与编译。 */
    private PositionDsl position;
}
