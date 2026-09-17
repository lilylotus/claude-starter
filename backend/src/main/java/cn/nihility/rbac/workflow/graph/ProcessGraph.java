package cn.nihility.rbac.workflow.graph;

import cn.nihility.rbac.workflow.dto.ProcessGraphEdgeVO;
import cn.nihility.rbac.workflow.dto.ProcessGraphNodeVO;
import java.util.List;

/**
 * {@link ProcessGraphAssembler} 解析流程定义 DSL 快照后得到的结构性节点/连线集合，节点尚未
 * 附带状态与审批轨迹（由 {@code WorkflowTaskServiceImpl} 结合运行时数据补齐）。
 *
 * @param nodes 节点列表
 * @param edges 连线列表
 */
public record ProcessGraph(List<ProcessGraphNodeVO> nodes, List<ProcessGraphEdgeVO> edges) {

    /**
     * 空图，用于流程定义缺失或快照为空时的兜底返回。
     *
     * @return 不含任何节点/连线的空图
     */
    public static ProcessGraph empty() {
        return new ProcessGraph(List.of(), List.of());
    }
}
