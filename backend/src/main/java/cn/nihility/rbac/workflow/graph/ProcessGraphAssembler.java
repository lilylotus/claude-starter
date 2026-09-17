package cn.nihility.rbac.workflow.graph;

import cn.nihility.rbac.common.util.JacksonUtils;
import cn.nihility.rbac.workflow.designer.dto.EdgeConditionDsl;
import cn.nihility.rbac.workflow.designer.dto.EdgeDsl;
import cn.nihility.rbac.workflow.designer.dto.ProcessModelDsl;
import cn.nihility.rbac.workflow.designer.dto.ProcessNodeDsl;
import cn.nihility.rbac.workflow.dslv2.dto.ConditionAstDsl;
import cn.nihility.rbac.workflow.dslv2.dto.ConditionItemDsl;
import cn.nihility.rbac.workflow.dslv2.dto.EdgeDslV2;
import cn.nihility.rbac.workflow.dslv2.dto.ProcessModelDslV2;
import cn.nihility.rbac.workflow.dslv2.dto.ProcessNodeDslV2;
import cn.nihility.rbac.workflow.dto.ConditionItemVO;
import cn.nihility.rbac.workflow.dto.ProcessGraphEdgeVO;
import cn.nihility.rbac.workflow.dto.ProcessGraphNodeVO;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 把流程定义发布快照（{@code tab_wf_process_definition.model_json_snapshot}）解析为精简的
 * 只读节点/连线图。按 {@code schemaVersion} 区分 v1（{@link ProcessModelDsl}）与 v2
 * （{@link ProcessModelDslV2}）两套 DSL，各自独立映射，不强行合并成一套参数化方法
 * （add-approval-remark-and-process-flowchart change design.md Decision 4，参照
 * fix-approval-zero-task-process-completion change design.md"两个方法各自职责单一"的
 * 同类取舍）。只做结构解析，不计算节点状态——状态需要结合流程实例的运行时数据，由调用方
 * （{@code WorkflowTaskServiceImpl}）在解析结果之上补齐。
 * <p>
 * 条件分支不在此拼接可读文案，只产出结构化条件数据（{@link ConditionItemVO}），翻译成可读
 * 文案的工作交给前端复用已加载的业务对象表单渲染元数据完成（design.md Decision 9）。
 */
@Component
public class ProcessGraphAssembler {

    /** DSL v2 的 {@code schemaVersion} 取值。 */
    private static final int SCHEMA_VERSION_V2 = 2;

    /** DSL v1 比较符字面量 → v2 比较符字面量的归一化映射，两套 DSL 对"大于等于/小于等于"用
     *  了不同的字面量拼写（v1 {@code GTE}/{@code LTE}，v2 {@code GE}/{@code LE}），其余取值
     *  两套 DSL 完全一致，不需要映射。 */
    private static final Map<String, String> V1_OPERATOR_NORMALIZATION = Map.of(
            "GTE", "GE",
            "LTE", "LE");

    /**
     * 解析流程定义快照为只读节点/连线图。
     *
     * @param schemaVersion     DSL schemaVersion，{@code null}/非 2 均按 v1 处理
     * @param modelJsonSnapshot 流程定义发布快照 JSON，为空时返回空图
     * @param instanceBizType   流程实例绑定的业务对象类型（{@code
     *                          ProcessInstanceEntity.businessType}），仅 DSL v2 条件项用作
     *                          {@code fieldBizType} 兜底（v2 {@link ConditionItemDsl} 本身
     *                          不携带该字段，v1 条件本身已显式携带，不需要此兜底）
     * @return 只读节点/连线图
     */
    public ProcessGraph assemble(Integer schemaVersion, String modelJsonSnapshot, String instanceBizType) {
        if (!StringUtils.hasText(modelJsonSnapshot)) {
            return ProcessGraph.empty();
        }
        if (Objects.equals(schemaVersion, SCHEMA_VERSION_V2)) {
            return assembleV2(modelJsonSnapshot, instanceBizType);
        }
        return assembleV1(modelJsonSnapshot);
    }

    /**
     * 解析 DSL v1 快照。
     */
    private ProcessGraph assembleV1(String modelJsonSnapshot) {
        ProcessModelDsl dsl = JacksonUtils.toObj(modelJsonSnapshot, ProcessModelDsl.class);
        return new ProcessGraph(mapNodesV1(dsl), mapEdgesV1(dsl));
    }

    /**
     * 解析 DSL v2 快照。
     */
    private ProcessGraph assembleV2(String modelJsonSnapshot, String instanceBizType) {
        ProcessModelDslV2 dsl = JacksonUtils.toObj(modelJsonSnapshot, ProcessModelDslV2.class);
        return new ProcessGraph(mapNodesV2(dsl), mapEdgesV2(dsl, instanceBizType));
    }

    /**
     * DSL v1 节点列表：无画布坐标，仅结构信息（id/类型/名称）。
     */
    private List<ProcessGraphNodeVO> mapNodesV1(ProcessModelDsl dsl) {
        if (dsl.getNodes() == null) {
            return List.of();
        }
        return dsl.getNodes().stream()
                .map(this::toNodeVOV1)
                .toList();
    }

    /**
     * 转换单个 v1 节点。
     */
    private ProcessGraphNodeVO toNodeVOV1(ProcessNodeDsl node) {
        return ProcessGraphNodeVO.builder()
                .id(node.getId())
                .type(node.getType())
                .name(node.getName())
                .build();
    }

    /**
     * DSL v1 连线列表：原生结构没有连线 id，由服务端按顺序合成一个同一次解析内唯一的 id。
     */
    private List<ProcessGraphEdgeVO> mapEdgesV1(ProcessModelDsl dsl) {
        if (dsl.getEdges() == null) {
            return List.of();
        }
        List<ProcessGraphEdgeVO> edges = new ArrayList<>();
        int index = 0;
        for (EdgeDsl edge : dsl.getEdges()) {
            edges.add(ProcessGraphEdgeVO.builder()
                    .id("edge-" + index++)
                    .source(edge.getFrom())
                    .target(edge.getTo())
                    .conditions(toConditionItemsV1(edge.getCondition()))
                    .conditionLogic(null)
                    .build());
        }
        return edges;
    }

    /**
     * 把 v1 分支条件转换为结构化条件项列表，无条件（兜底默认分支）返回空列表。v1 一条边只有
     * 一个条件项，{@code conditionLogic} 恒不设置（单条件项无逻辑连接符意义）。
     */
    private List<ConditionItemVO> toConditionItemsV1(EdgeConditionDsl condition) {
        if (condition == null) {
            return List.of();
        }
        return List.of(ConditionItemVO.builder()
                .fieldBizType(condition.getFieldBizType())
                .field(condition.getField())
                .operator(normalizeV1Operator(condition.getOperator()))
                .value(condition.getValue())
                .build());
    }

    /**
     * 归一化 v1 比较符字面量为 v2 的字面量拼写（{@code GTE}→{@code GE}，{@code LTE}→
     * {@code LE}），其余取值原样返回。
     */
    private String normalizeV1Operator(String operator) {
        return V1_OPERATOR_NORMALIZATION.getOrDefault(operator, operator);
    }

    /**
     * DSL v2 节点列表：携带画布坐标（仅前端往返使用，此处原样透出）。
     */
    private List<ProcessGraphNodeVO> mapNodesV2(ProcessModelDslV2 dsl) {
        if (dsl.getNodes() == null) {
            return List.of();
        }
        return dsl.getNodes().stream()
                .map(this::toNodeVOV2)
                .toList();
    }

    /**
     * 转换单个 v2 节点。
     */
    private ProcessGraphNodeVO toNodeVOV2(ProcessNodeDslV2 node) {
        return ProcessGraphNodeVO.builder()
                .id(node.getId())
                .type(node.getType())
                .name(node.getName())
                .x(node.getPosition() == null ? null : node.getPosition().getX())
                .y(node.getPosition() == null ? null : node.getPosition().getY())
                .build();
    }

    /**
     * DSL v2 连线列表：原生结构自带连线 id，直接透出。
     */
    private List<ProcessGraphEdgeVO> mapEdgesV2(ProcessModelDslV2 dsl, String instanceBizType) {
        if (dsl.getEdges() == null) {
            return List.of();
        }
        return dsl.getEdges().stream()
                .map(edge -> ProcessGraphEdgeVO.builder()
                        .id(edge.getId())
                        .source(edge.getSource())
                        .target(edge.getTarget())
                        .conditions(toConditionItemsV2(edge.getCondition(), instanceBizType))
                        .conditionLogic(edge.getCondition() == null || edge.getCondition().getLogic() == null
                                ? null
                                : edge.getCondition().getLogic().name())
                        .build())
                .toList();
    }

    /**
     * 把 v2 条件 AST（{@code logic + items}）转换为结构化条件项列表，无条件（兜底默认分支）
     * 返回空列表。v2 {@link ConditionItemDsl} 本身不携带 {@code fieldBizType}（核实过，
     * {@code cn.nihility.rbac.workflow.dslv2.dto} 包下搜不到该字段，
     * {@link cn.nihility.rbac.workflow.dslv2.compiler.ProcessModelDslV2Validator} 也不校验
     * 条件字段归属的业务对象类型），按该流程绑定的业务对象类型兜底（design.md Decision 9）。
     */
    private List<ConditionItemVO> toConditionItemsV2(ConditionAstDsl condition, String instanceBizType) {
        if (condition == null || condition.getItems() == null || condition.getItems().isEmpty()) {
            return List.of();
        }
        return condition.getItems().stream()
                .map(item -> ConditionItemVO.builder()
                        .fieldBizType(instanceBizType)
                        .field(item.getField())
                        .operator(item.getOp() == null ? null : item.getOp().name())
                        .value(item.getValue())
                        .build())
                .toList();
    }
}
