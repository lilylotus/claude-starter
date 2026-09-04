package cn.nihility.rbac.workflow.dslv2.simulation;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

/**
 * 单个审批节点的快速预演解析结果（tasks.md 4.2）。
 */
@Getter
@Builder
public class ApprovalNodeSimulationVO {

    /** 节点 id。 */
    @Schema(description = "节点 id")
    private String nodeId;

    /** 节点名称。 */
    @Schema(description = "节点名称")
    private String nodeName;

    /** 解析到的候选人用户 id 列表；为空表示空审批人（见 {@link #emptyAssignee}）。 */
    @Schema(description = "候选人用户 id 列表")
    private List<Long> candidateUserIds;

    /** 解析依据说明，如来源类型/取值/命中人数/自审排除/空人策略兜底情况。 */
    @Schema(description = "解析依据说明")
    private String resolveBasis;

    /** 是否为空审批人节点（需在报告中显式标注，不能让前端误以为静默跳过）。 */
    @Schema(description = "是否为空审批人节点")
    private boolean emptyAssignee;
}
