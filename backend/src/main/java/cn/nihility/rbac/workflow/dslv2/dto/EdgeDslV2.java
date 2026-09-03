package cn.nihility.rbac.workflow.dslv2.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DSL v2 连线：{@code condition} 仅在源节点为"条件"节点时有意义，为空表示该边是无条件流转的
 * 兜底默认分支；多条分支按 {@code priority} 从小到大取第一个命中的分支，默认分支
 * {@code priority} 必须最大（design.md Decision 3）。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EdgeDslV2 {

    /** 连线 id，同一流程模型内唯一。 */
    private String id;

    /** 起始节点 id。 */
    private String source;

    /** 目标节点 id。 */
    private String target;

    /** 分支条件，为空表示无条件流转（条件节点的兜底默认分支）。 */
    private ConditionAstDsl condition;

    /** 分支优先级，值越小越先判定；条件节点的多条出边内须唯一。 */
    private Integer priority;
}
