package cn.nihility.rbac.workflow.graph;

import static org.assertj.core.api.Assertions.assertThat;

import cn.nihility.rbac.workflow.dto.ConditionItemVO;
import cn.nihility.rbac.workflow.dto.ProcessGraphEdgeVO;
import cn.nihility.rbac.workflow.dto.ProcessGraphNodeVO;
import org.junit.jupiter.api.Test;

/**
 * {@link ProcessGraphAssembler} 单元测试：验证 DSL v1/v2 快照各自独立解析为只读节点/连线图的
 * 结构正确性（add-approval-remark-and-process-flowchart change tasks.md 3.4/3b）。不涉及
 * 节点状态计算——状态由 {@code WorkflowTaskServiceImpl} 结合运行时数据在解析结果之上补齐。
 */
class ProcessGraphAssemblerTest {

    /** DSL v1 快照：开始 -&gt; 审批 -&gt; 条件（高/低风险两条分支）-&gt; 结束。 */
    private static final String V1_SNAPSHOT = """
            {
              "processCode": "TEST_V1",
              "processName": "v1 测试流程",
              "nodes": [
                {"id": "start", "type": "START"},
                {"id": "approve1", "type": "APPROVAL", "name": "审批一"},
                {"id": "cond", "type": "CONDITION"},
                {"id": "approveHigh", "type": "APPROVAL", "name": "高风险审批"},
                {"id": "approveLow", "type": "APPROVAL", "name": "低风险审批"},
                {"id": "end", "type": "END"}
              ],
              "edges": [
                {"from": "start", "to": "approve1"},
                {"from": "approve1", "to": "cond"},
                {"from": "cond", "to": "approveHigh",
                 "condition": {"fieldBizType": "ORG", "field": "riskLevel", "operator": "EQ", "value": "HIGH"}},
                {"from": "cond", "to": "approveLow",
                 "condition": {"fieldBizType": "ORG", "field": "riskScore", "operator": "GTE", "value": 60}},
                {"from": "approveHigh", "to": "end"},
                {"from": "approveLow", "to": "end"}
              ]
            }
            """;

    /** DSL v1 快照：单个条件边使用 {@code LTE} 比较符，覆盖与 {@code GTE} 不同的归一化分支。 */
    private static final String V1_SNAPSHOT_WITH_LTE = """
            {
              "processCode": "TEST_V1_LTE",
              "processName": "v1 LTE 测试流程",
              "nodes": [
                {"id": "start", "type": "START"},
                {"id": "cond", "type": "CONDITION"},
                {"id": "approveA", "type": "APPROVAL", "name": "审批A"},
                {"id": "approveB", "type": "APPROVAL", "name": "审批B"},
                {"id": "end", "type": "END"}
              ],
              "edges": [
                {"from": "start", "to": "cond"},
                {"from": "cond", "to": "approveA",
                 "condition": {"fieldBizType": "USER", "field": "age", "operator": "LTE", "value": 30}},
                {"from": "cond", "to": "approveB"},
                {"from": "approveA", "to": "end"},
                {"from": "approveB", "to": "end"}
              ]
            }
            """;

    /** DSL v2 快照：单个条件边使用 {@code OR} 逻辑连接符，覆盖与 {@code AND} 不同的组合分支。 */
    private static final String V2_SNAPSHOT_WITH_OR = """
            {
              "schemaVersion": 2,
              "processCode": "TEST_V2_OR",
              "processName": "v2 OR 测试流程",
              "nodes": [
                {"id": "start", "type": "START"},
                {"id": "cond", "type": "CONDITION"},
                {"id": "approveA", "type": "APPROVAL", "name": "审批A"},
                {"id": "approveB", "type": "APPROVAL", "name": "审批B"},
                {"id": "end", "type": "END"}
              ],
              "edges": [
                {"id": "e1", "source": "start", "target": "cond"},
                {"id": "e2", "source": "cond", "target": "approveA", "priority": 1,
                 "condition": {"logic": "OR", "items": [
                   {"field": "riskLevel", "op": "EQ", "value": "HIGH"},
                   {"field": "riskLevel", "op": "EQ", "value": "MEDIUM"}
                 ]}},
                {"id": "e3", "source": "cond", "target": "approveB", "priority": 2},
                {"id": "e4", "source": "approveA", "target": "end"},
                {"id": "e5", "source": "approveB", "target": "end"}
              ]
            }
            """;

    /** DSL v2 快照：开始 -&gt; 并行分叉（两条并行审批分支）-&gt; 汇合 -&gt; 条件分支 -&gt; 结束。
     *  条件边携带 {@code AND} 逻辑连接符下的两个条件项，覆盖多条件项归并的解析路径。 */
    private static final String V2_SNAPSHOT = """
            {
              "schemaVersion": 2,
              "processCode": "TEST_V2",
              "processName": "v2 测试流程",
              "nodes": [
                {"id": "start", "type": "START", "position": {"x": 0, "y": 0}},
                {"id": "split", "type": "PARALLEL_SPLIT", "joinNodeId": "join", "position": {"x": 100, "y": 0}},
                {"id": "branchA", "type": "APPROVAL", "name": "分支A审批", "position": {"x": 200, "y": -50}},
                {"id": "branchB", "type": "APPROVAL", "name": "分支B审批", "position": {"x": 200, "y": 50}},
                {"id": "join", "type": "PARALLEL_JOIN", "splitNodeId": "split", "position": {"x": 300, "y": 0}},
                {"id": "cond", "type": "CONDITION", "position": {"x": 400, "y": 0}},
                {"id": "approveHigh", "type": "APPROVAL", "name": "高风险审批", "position": {"x": 500, "y": -50}},
                {"id": "approveLow", "type": "APPROVAL", "name": "低风险审批", "position": {"x": 500, "y": 50}},
                {"id": "end", "type": "END", "position": {"x": 600, "y": 0}}
              ],
              "edges": [
                {"id": "e1", "source": "start", "target": "split"},
                {"id": "e2", "source": "split", "target": "branchA"},
                {"id": "e3", "source": "split", "target": "branchB"},
                {"id": "e4", "source": "branchA", "target": "join"},
                {"id": "e5", "source": "branchB", "target": "join"},
                {"id": "e6", "source": "join", "target": "cond"},
                {"id": "e7", "source": "cond", "target": "approveHigh", "priority": 1,
                 "condition": {"logic": "AND", "items": [
                   {"field": "riskLevel", "op": "EQ", "value": "HIGH"},
                   {"field": "age", "op": "GT", "value": 18}
                 ]}},
                {"id": "e8", "source": "cond", "target": "approveLow", "priority": 2},
                {"id": "e9", "source": "approveHigh", "target": "end"},
                {"id": "e10", "source": "approveLow", "target": "end"}
              ]
            }
            """;

    /** 被测组件。 */
    private final ProcessGraphAssembler assembler = new ProcessGraphAssembler();

    /** {@code schemaVersion=null}（历史默认）应按 v1 处理，节点/连线结构解析正确，比较符
     *  {@code GTE} 归一化为 {@code GE}。 */
    @Test
    void assemble_shouldParseV1Snapshot_whenSchemaVersionIsNull() {
        ProcessGraph graph = assembler.assemble(null, V1_SNAPSHOT, "ORG");

        assertThat(graph.nodes()).extracting(ProcessGraphNodeVO::getId)
                .containsExactly("start", "approve1", "cond", "approveHigh", "approveLow", "end");
        assertThat(graph.nodes()).extracting(ProcessGraphNodeVO::getType)
                .containsExactly("START", "APPROVAL", "CONDITION", "APPROVAL", "APPROVAL", "END");
        // v1 无画布坐标，全部为空
        assertThat(graph.nodes()).allSatisfy(node -> {
            assertThat(node.getX()).isNull();
            assertThat(node.getY()).isNull();
        });

        assertThat(graph.edges()).hasSize(6);
        assertThat(graph.edges()).extracting(ProcessGraphEdgeVO::getSource, ProcessGraphEdgeVO::getTarget)
                .contains(
                        org.assertj.core.groups.Tuple.tuple("cond", "approveHigh"),
                        org.assertj.core.groups.Tuple.tuple("cond", "approveLow"));
        // v1 连线原生无 id，服务端按顺序合成，同一次解析内互不重复
        assertThat(graph.edges()).extracting(ProcessGraphEdgeVO::getId).doesNotHaveDuplicates();

        ProcessGraphEdgeVO conditionalEdge = graph.edges().stream()
                .filter(edge -> "cond".equals(edge.getSource()) && "approveHigh".equals(edge.getTarget()))
                .findFirst().orElseThrow();
        assertThat(conditionalEdge.getConditions()).hasSize(1);
        ConditionItemVO conditionItem = conditionalEdge.getConditions().get(0);
        assertThat(conditionItem.getFieldBizType()).isEqualTo("ORG");
        assertThat(conditionItem.getField()).isEqualTo("riskLevel");
        assertThat(conditionItem.getOperator()).isEqualTo("EQ");
        assertThat(conditionItem.getValue()).isEqualTo("HIGH");
        assertThat(conditionalEdge.getConditionLogic()).isNull();

        // GTE 归一化为 GE
        ProcessGraphEdgeVO gteEdge = graph.edges().stream()
                .filter(edge -> "cond".equals(edge.getSource()) && "approveLow".equals(edge.getTarget()))
                .findFirst().orElseThrow();
        assertThat(gteEdge.getConditions()).hasSize(1);
        assertThat(gteEdge.getConditions().get(0).getOperator()).isEqualTo("GE");
        assertThat(gteEdge.getConditions().get(0).getField()).isEqualTo("riskScore");

        // 无条件（普通边）conditions 为空列表，不是 null
        ProcessGraphEdgeVO plainEdge = graph.edges().stream()
                .filter(edge -> "start".equals(edge.getSource())).findFirst().orElseThrow();
        assertThat(plainEdge.getConditions()).isNotNull().isEmpty();
    }

    /** {@code schemaVersion=2} 应按 v2 解析，含并行分叉/汇合节点类型与画布坐标，多条件项按
     *  {@code AND} 归并、{@code fieldBizType} 按流程实例业务对象类型兜底。 */
    @Test
    void assemble_shouldParseV2Snapshot_withParallelAndCondition() {
        ProcessGraph graph = assembler.assemble(2, V2_SNAPSHOT, "USER");

        assertThat(graph.nodes()).extracting(ProcessGraphNodeVO::getId)
                .containsExactly("start", "split", "branchA", "branchB", "join", "cond", "approveHigh",
                        "approveLow", "end");
        assertThat(graph.nodes()).extracting(ProcessGraphNodeVO::getType)
                .contains("PARALLEL_SPLIT", "PARALLEL_JOIN");

        ProcessGraphNodeVO branchA = graph.nodes().stream()
                .filter(node -> "branchA".equals(node.getId())).findFirst().orElseThrow();
        assertThat(branchA.getX()).isEqualTo(200d);
        assertThat(branchA.getY()).isEqualTo(-50d);

        assertThat(graph.edges()).hasSize(10);
        assertThat(graph.edges()).extracting(ProcessGraphEdgeVO::getId)
                .contains("e1", "e7", "e8");

        ProcessGraphEdgeVO conditionalEdge = graph.edges().stream()
                .filter(edge -> "e7".equals(edge.getId())).findFirst().orElseThrow();
        assertThat(conditionalEdge.getConditionLogic()).isEqualTo("AND");
        assertThat(conditionalEdge.getConditions()).hasSize(2);
        assertThat(conditionalEdge.getConditions()).extracting(ConditionItemVO::getField)
                .containsExactly("riskLevel", "age");
        assertThat(conditionalEdge.getConditions()).extracting(ConditionItemVO::getOperator)
                .containsExactly("EQ", "GT");
        assertThat(conditionalEdge.getConditions()).extracting(ConditionItemVO::getValue)
                .containsExactly("HIGH", 18);
        // v2 条件项无原生 fieldBizType，按流程实例绑定的业务对象类型兜底
        assertThat(conditionalEdge.getConditions()).extracting(ConditionItemVO::getFieldBizType)
                .containsExactly("USER", "USER");

        ProcessGraphEdgeVO defaultEdge = graph.edges().stream()
                .filter(edge -> "e8".equals(edge.getId())).findFirst().orElseThrow();
        assertThat(defaultEdge.getConditions()).isNotNull().isEmpty();
        assertThat(defaultEdge.getConditionLogic()).isNull();

        ProcessGraphEdgeVO parallelEdge = graph.edges().stream()
                .filter(edge -> "e2".equals(edge.getId())).findFirst().orElseThrow();
        assertThat(parallelEdge.getConditions()).isNotNull().isEmpty();
    }

    /** v1 {@code LTE} 比较符应归一化为 {@code LE}。 */
    @Test
    void assemble_shouldNormalizeLteOperator_forV1Condition() {
        ProcessGraph graph = assembler.assemble(null, V1_SNAPSHOT_WITH_LTE, "USER");

        ProcessGraphEdgeVO lteEdge = graph.edges().stream()
                .filter(edge -> "cond".equals(edge.getSource()) && "approveA".equals(edge.getTarget()))
                .findFirst().orElseThrow();
        assertThat(lteEdge.getConditions()).hasSize(1);
        ConditionItemVO conditionItem = lteEdge.getConditions().get(0);
        assertThat(conditionItem.getOperator()).isEqualTo("LE");
        assertThat(conditionItem.getFieldBizType()).isEqualTo("USER");
        assertThat(conditionItem.getField()).isEqualTo("age");
        assertThat(conditionItem.getValue()).isEqualTo(30);

        ProcessGraphEdgeVO defaultEdge = graph.edges().stream()
                .filter(edge -> "cond".equals(edge.getSource()) && "approveB".equals(edge.getTarget()))
                .findFirst().orElseThrow();
        assertThat(defaultEdge.getConditions()).isNotNull().isEmpty();
    }

    /** v2 {@code OR} 逻辑连接符下的多条件项应正确产出 {@code conditionLogic=OR}。 */
    @Test
    void assemble_shouldParseOrConditionLogic_forV2Condition() {
        ProcessGraph graph = assembler.assemble(2, V2_SNAPSHOT_WITH_OR, "ORG");

        ProcessGraphEdgeVO orEdge = graph.edges().stream()
                .filter(edge -> "e2".equals(edge.getId())).findFirst().orElseThrow();
        assertThat(orEdge.getConditionLogic()).isEqualTo("OR");
        assertThat(orEdge.getConditions()).hasSize(2);
        assertThat(orEdge.getConditions()).extracting(ConditionItemVO::getValue)
                .containsExactly("HIGH", "MEDIUM");
        assertThat(orEdge.getConditions()).extracting(ConditionItemVO::getFieldBizType)
                .containsExactly("ORG", "ORG");
    }

    /** 快照为空时应返回空图，不抛异常。 */
    @Test
    void assemble_shouldReturnEmptyGraph_whenSnapshotBlank() {
        ProcessGraph graph = assembler.assemble(1, null, "ORG");

        assertThat(graph.nodes()).isEmpty();
        assertThat(graph.edges()).isEmpty();
    }
}
