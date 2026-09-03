package cn.nihility.rbac.workflow.designer.compiler;

import cn.nihility.rbac.workflow.constant.ApprovalMode;
import cn.nihility.rbac.workflow.constant.AssigneeType;
import cn.nihility.rbac.workflow.designer.dto.ApprovalNodeDsl;
import cn.nihility.rbac.workflow.designer.dto.ConditionNodeDsl;
import cn.nihility.rbac.workflow.designer.dto.EdgeConditionDsl;
import cn.nihility.rbac.workflow.designer.dto.EdgeDsl;
import cn.nihility.rbac.workflow.designer.dto.EndNodeDsl;
import cn.nihility.rbac.workflow.designer.dto.ProcessModelDsl;
import cn.nihility.rbac.workflow.designer.dto.ProcessNodeDsl;
import cn.nihility.rbac.workflow.designer.dto.StartNodeDsl;
import cn.nihility.rbac.workflow.exception.WorkflowModelValidationException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.util.StringUtils;

/**
 * Workflow JSON DSL 结构与业务规则校验器，发布前的权威校验（前端保存草稿时的基础校验规则
 * 与本类保持一致，避免前后端校验规则漂移，workflow-approval-engine change design.md
 * Decision 9 / specs/workflow-process-designer"发布前结构与业务规则的强制校验"Requirement）。
 * 校验规则：唯一开始节点、至少一个结束节点、节点 id 唯一、边引用的节点必须存在、开始到结束
 * 存在可达路径、条件节点存在兜底默认边、审批节点审批人来源相关必填字段完整。所有校验失败
 * 一次性收集后统一抛出，携带具体节点/连线定位信息，不是发现第一个错误就短路返回。
 */
public final class ProcessModelDslValidator {

    /** 条件比较符白名单，禁止使用者直接输入自由表达式字符串。 */
    private static final Set<String> ALLOWED_OPERATORS = Set.of("EQ", "NE", "GT", "GTE", "LT", "LTE");

    /** 工具类不允许实例化。 */
    private ProcessModelDslValidator() {
    }

    /**
     * 校验流程模型 DSL，校验失败抛出携带全部错误明细的
     * {@link WorkflowModelValidationException}。
     *
     * @param dsl 待校验的流程模型 DSL
     */
    public static void validate(ProcessModelDsl dsl) {
        List<String> errors = new ArrayList<>();
        if (dsl == null || dsl.getNodes() == null || dsl.getNodes().isEmpty()) {
            throw new WorkflowModelValidationException("流程模型 DSL 不能为空，至少需要包含节点定义");
        }
        List<ProcessNodeDsl> nodes = dsl.getNodes();
        List<EdgeDsl> edges = dsl.getEdges() == null ? List.of() : dsl.getEdges();

        Map<String, ProcessNodeDsl> nodeById = new LinkedHashMap<>();
        for (ProcessNodeDsl node : nodes) {
            if (!StringUtils.hasText(node.getId())) {
                errors.add("存在未设置 id 的节点");
                continue;
            }
            if (nodeById.containsKey(node.getId())) {
                errors.add("节点 id 重复：" + node.getId());
                continue;
            }
            nodeById.put(node.getId(), node);
        }

        long startCount = nodes.stream().filter(StartNodeDsl.class::isInstance).count();
        if (startCount != 1) {
            errors.add("流程模型必须有且仅有一个开始节点，当前数量：" + startCount);
        }
        long endCount = nodes.stream().filter(EndNodeDsl.class::isInstance).count();
        if (endCount < 1) {
            errors.add("流程模型至少需要一个结束节点");
        }

        for (EdgeDsl edge : edges) {
            if (!StringUtils.hasText(edge.getFrom()) || !nodeById.containsKey(edge.getFrom())) {
                errors.add("连线引用了不存在的起始节点：" + edge.getFrom());
            }
            if (!StringUtils.hasText(edge.getTo()) || !nodeById.containsKey(edge.getTo())) {
                errors.add("连线引用了不存在的目标节点：" + edge.getTo());
            }
        }

        // 节点/连线引用本身有问题时，后续可达性与条件校验容易产生误报，提前抛出。
        if (!errors.isEmpty()) {
            throw new WorkflowModelValidationException(errors);
        }

        Map<String, List<EdgeDsl>> outgoing = buildOutgoing(nodeById.keySet(), edges);
        Set<String> hasIncoming = new HashSet<>();
        for (EdgeDsl edge : edges) {
            hasIncoming.add(edge.getTo());
        }

        for (ProcessNodeDsl node : nodes) {
            boolean isStart = node instanceof StartNodeDsl;
            if (!isStart && !hasIncoming.contains(node.getId())) {
                errors.add("节点 " + node.getId() + " 未被任何连线指向，属于孤立节点");
            }
        }

        if (startCount == 1) {
            errors.addAll(validateReachability(nodes, nodeById, outgoing));
        }

        errors.addAll(validateConditionNodes(nodes, outgoing));
        errors.addAll(validateApprovalNodes(nodes));

        if (!errors.isEmpty()) {
            throw new WorkflowModelValidationException(errors);
        }
    }

    /**
     * 按节点 id 建立出边索引。
     */
    private static Map<String, List<EdgeDsl>> buildOutgoing(Set<String> nodeIds, List<EdgeDsl> edges) {
        Map<String, List<EdgeDsl>> outgoing = new LinkedHashMap<>();
        for (String nodeId : nodeIds) {
            outgoing.put(nodeId, new ArrayList<>());
        }
        for (EdgeDsl edge : edges) {
            outgoing.computeIfAbsent(edge.getFrom(), key -> new ArrayList<>()).add(edge);
        }
        return outgoing;
    }

    /**
     * 校验从开始节点必须存在到达任一结束节点的路径（BFS）。
     */
    private static List<String> validateReachability(
            List<ProcessNodeDsl> nodes,
            Map<String, ProcessNodeDsl> nodeById,
            Map<String, List<EdgeDsl>> outgoing) {
        String startId = nodes.stream()
                .filter(StartNodeDsl.class::isInstance)
                .map(ProcessNodeDsl::getId)
                .findFirst()
                .orElseThrow();
        Set<String> visited = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(startId);
        visited.add(startId);
        boolean reachedEnd = false;
        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (nodeById.get(current) instanceof EndNodeDsl) {
                reachedEnd = true;
            }
            for (EdgeDsl edge : outgoing.getOrDefault(current, List.of())) {
                if (visited.add(edge.getTo())) {
                    queue.add(edge.getTo());
                }
            }
        }
        return reachedEnd ? List.of() : List.of("从开始节点无法到达任何结束节点");
    }

    /**
     * 校验条件节点的出边：至少一条兜底默认分支，携带条件的出边字段/比较符/比较值完整合法。
     */
    private static List<String> validateConditionNodes(List<ProcessNodeDsl> nodes, Map<String, List<EdgeDsl>> outgoing) {
        List<String> errors = new ArrayList<>();
        for (ProcessNodeDsl node : nodes) {
            if (!(node instanceof ConditionNodeDsl)) {
                continue;
            }
            List<EdgeDsl> out = outgoing.getOrDefault(node.getId(), List.of());
            boolean hasDefault = out.stream().anyMatch(edge -> edge.getCondition() == null);
            if (!hasDefault) {
                errors.add("条件节点 " + node.getId() + " 缺少默认分支（未携带 condition 的兜底出边）");
            }
            for (EdgeDsl edge : out) {
                EdgeConditionDsl condition = edge.getCondition();
                if (condition == null) {
                    continue;
                }
                String edgeLocation = "边 " + edge.getFrom() + "->" + edge.getTo();
                if (!StringUtils.hasText(condition.getField())) {
                    errors.add(edgeLocation + " 的条件缺少字段 field");
                }
                if (!StringUtils.hasText(condition.getOperator()) || !ALLOWED_OPERATORS.contains(condition.getOperator())) {
                    errors.add(edgeLocation + " 的比较符不在允许范围内（仅支持 EQ/NE/GT/GTE/LT/LTE）：" + condition.getOperator());
                }
                if (condition.getValue() == null) {
                    errors.add(edgeLocation + " 的条件缺少比较值 value");
                }
            }
        }
        return errors;
    }

    /**
     * 校验审批节点必填字段：{@code assigneeType} 必填，{@code ROLE}/{@code USER} 类型
     * {@code assigneeValue} 必填，{@code PERCENT} 模式 {@code approvalPercent} 必填且在
     * 1~100 之间，{@code emptyAssigneeStrategy} 必填（供 {@code WorkflowAssigneeTaskListener}/
     * {@code WorkflowMultiInstanceExecutionListener} 兜底判定，缺失会导致运行时解析异常）。
     */
    private static List<String> validateApprovalNodes(List<ProcessNodeDsl> nodes) {
        List<String> errors = new ArrayList<>();
        for (ProcessNodeDsl node : nodes) {
            if (!(node instanceof ApprovalNodeDsl approval)) {
                continue;
            }
            String location = "审批节点 " + node.getId();
            if (approval.getAssigneeType() == null) {
                errors.add(location + " 未配置审批人来源 assigneeType");
            } else if ((approval.getAssigneeType() == AssigneeType.ROLE || approval.getAssigneeType() == AssigneeType.USER)
                    && !StringUtils.hasText(approval.getAssigneeValue())) {
                errors.add(location + " 的审批人来源 " + approval.getAssigneeType() + " 缺少必填的 assigneeValue");
            }
            if (approval.getApprovalMode() == ApprovalMode.PERCENT) {
                Integer percent = approval.getApprovalPercent();
                if (percent == null || percent < 1 || percent > 100) {
                    errors.add(location + " 的会签比例 approvalPercent 必须在 1~100 之间");
                }
            }
            if (approval.getEmptyAssigneeStrategy() == null) {
                errors.add(location + " 未配置空审批人策略 emptyAssigneeStrategy");
            }
        }
        return errors;
    }
}
