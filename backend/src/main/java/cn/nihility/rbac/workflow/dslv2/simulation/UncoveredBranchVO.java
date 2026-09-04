package cn.nihility.rbac.workflow.dslv2.simulation;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

/**
 * 条件节点未展开（未命中）的分支说明（tasks.md 4.2"未覆盖分支列表"）。
 */
@Getter
@Builder
public class UncoveredBranchVO {

    /** 连线 id，无命中且无默认分支导致路径中断时可能为空。 */
    @Schema(description = "连线 id")
    private String edgeId;

    /** 分支所属条件节点 id。 */
    @Schema(description = "条件节点 id")
    private String sourceNodeId;

    /** 未展开分支指向的目标节点 id，路径中断场景可能为空。 */
    @Schema(description = "目标节点 id")
    private String targetNodeId;

    /** 未覆盖原因说明，如"条件未命中"/"存在命中分支，默认分支未展开"。 */
    @Schema(description = "未覆盖原因")
    private String reason;
}
