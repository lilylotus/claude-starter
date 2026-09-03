package cn.nihility.rbac.workflow.designer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Workflow JSON DSL 连线：连接两个节点，{@code condition} 仅在源节点为"条件"节点时有意义，
 * 为空表示该边是无条件流转的兜底默认分支（workflow-approval-engine change design.md
 * Decision 9）。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EdgeDsl {

    /** 起始节点 id。 */
    private String from;

    /** 目标节点 id。 */
    private String to;

    /** 分支条件，为空表示无条件流转（条件节点的兜底默认分支）。 */
    private EdgeConditionDsl condition;
}
