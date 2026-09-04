package cn.nihility.rbac.workflow.dslv2.simulation;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

/**
 * 快速预演报告（production-approval-lifecycle change design.md 第 4 节"试运行分两层"第一层，
 * tasks.md 4.2）。{@code mode} 恒为 {@code QUICK_PREVIEW}，显式区分于"独立测试环境真实试
 * 运行"（本轮未实现，留待后续批次），避免前端误以为这是真实试运行报告。
 */
@Getter
@Builder
public class SimulationResultVO {

    /** 预演模式，恒为 {@code QUICK_PREVIEW}。 */
    @Schema(description = "预演模式，恒为 QUICK_PREVIEW", example = "QUICK_PREVIEW")
    private String mode;

    /** 命中路径：从开始节点出发按遍历顺序访问到的节点 id 列表；并行分叉的两条分支均展开，
     *  条件节点只展开命中分支，是遍历顺序而非严格的单线程执行时间线。 */
    @Schema(description = "命中路径（节点 id 有序列表）")
    private List<String> hitPath;

    /** 每个审批节点的解析结果。 */
    @Schema(description = "审批节点解析结果列表")
    private List<ApprovalNodeSimulationVO> approvalResolutions;

    /** 条件节点未展开（未命中）的分支列表。 */
    @Schema(description = "未覆盖分支列表")
    private List<UncoveredBranchVO> uncoveredBranches;
}
