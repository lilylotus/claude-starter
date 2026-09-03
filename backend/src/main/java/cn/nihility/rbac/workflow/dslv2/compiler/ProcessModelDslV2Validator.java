package cn.nihility.rbac.workflow.dslv2.compiler;

import cn.nihility.rbac.workflow.dslv2.constant.AssigneeTypeV2;
import cn.nihility.rbac.workflow.dslv2.constant.ConditionOperator;
import cn.nihility.rbac.workflow.dslv2.constant.EmptyPolicy;
import cn.nihility.rbac.workflow.dslv2.constant.RejectPolicy;
import cn.nihility.rbac.workflow.dslv2.constant.VoteMode;
import cn.nihility.rbac.workflow.dslv2.dto.ApprovalNodeDslV2;
import cn.nihility.rbac.workflow.dslv2.dto.AutoNodeDslV2;
import cn.nihility.rbac.workflow.dslv2.dto.CcNodeDslV2;
import cn.nihility.rbac.workflow.dslv2.dto.ConditionAstDsl;
import cn.nihility.rbac.workflow.dslv2.dto.ConditionNodeDslV2;
import cn.nihility.rbac.workflow.dslv2.dto.EdgeDslV2;
import cn.nihility.rbac.workflow.dslv2.dto.EndNodeDslV2;
import cn.nihility.rbac.workflow.dslv2.dto.ParallelJoinNodeDslV2;
import cn.nihility.rbac.workflow.dslv2.dto.ParallelSplitNodeDslV2;
import cn.nihility.rbac.workflow.dslv2.dto.ProcessModelDslV2;
import cn.nihility.rbac.workflow.dslv2.dto.ProcessNodeDslV2;
import cn.nihility.rbac.workflow.dslv2.dto.StartNodeDslV2;
import cn.nihility.rbac.workflow.exception.WorkflowModelValidationException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.util.StringUtils;

/**
 * DSL v2 结构与业务规则校验器，v1 {@link cn.nihility.rbac.workflow.designer.compiler.ProcessModelDslValidator}
 * 的姊妹实现，规则更丰富（并行块配对、条件 AST、抄送/自动任务节点），彼此独立、互不调用
 * （production-approval-lifecycle change design.md Decision 3）。当前无数据来源的审批人类型
 * （{@code APP_ADMIN}/{@code FORM_REFERENCE_PERSON}）以及本轮未实现的
 * {@code rejectPolicy=THRESHOLD} 在此拒绝，而不是留到运行时才失败。
 */
public final class ProcessModelDslV2Validator {

    /** 单模型最大节点数（design.md Decision 3 建议初始值）。 */
    private static final int MAX_NODES = 200;

    /** 单条件组最大嵌套/条件项数量，防止畸形超大 DSL。 */
    private static final int MAX_CONDITION_ITEMS = 50;

    /** 当前无数据来源、发布即拒绝的审批人来源类型（design.md Decision 5"无数据来源的规则
     *  保持禁用"）。 */
    private static final Set<AssigneeTypeV2> NO_RESOLVER_TYPES = Set.of(
            AssigneeTypeV2.APP_ADMIN, AssigneeTypeV2.FORM_REFERENCE_PERSON, AssigneeTypeV2.POSITION);

    /** 工具类不允许实例化。 */
    private ProcessModelDslV2Validator() {
    }

    /**
     * 校验流程模型 DSL v2，校验失败抛出携带全部错误明细的
     * {@link WorkflowModelValidationException}。
     *
     * @param dsl 待校验的流程模型 DSL v2
     */
    public static void validate(ProcessModelDslV2 dsl) {
        List<String> errors = new ArrayList<>();
        if (dsl == null || dsl.getNodes() == null || dsl.getNodes().isEmpty()) {
            throw new WorkflowModelValidationException("流程模型 DSL 不能为空，至少需要包含节点定义");
        }
        if (dsl.getSchemaVersion() == null || dsl.getSchemaVersion() != 2) {
            throw new WorkflowModelValidationException("schemaVersion 必须为 2");
        }
        List<ProcessNodeDslV2> nodes = dsl.getNodes();
        List<EdgeDslV2> edges = dsl.getEdges() == null ? List.of() : dsl.getEdges();
        if (nodes.size() > MAX_NODES) {
            throw new WorkflowModelValidationException("节点数量超过上限 " + MAX_NODES);
        }

        Map<String, ProcessNodeDslV2> nodeById = new LinkedHashMap<>();
        for (ProcessNodeDslV2 node : nodes) {
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

        long startCount = nodes.stream().filter(StartNodeDslV2.class::isInstance).count();
        if (startCount != 1) {
            errors.add("流程模型必须有且仅有一个开始节点，当前数量：" + startCount);
        }
        long endCount = nodes.stream().filter(EndNodeDslV2.class::isInstance).count();
        if (endCount < 1) {
            errors.add("流程模型至少需要一个结束节点");
        }

        Set<String> edgeIds = new HashSet<>();
        for (EdgeDslV2 edge : edges) {
            if (!StringUtils.hasText(edge.getId())) {
                errors.add("存在未设置 id 的连线");
            } else if (!edgeIds.add(edge.getId())) {
                errors.add("连线 id 重复：" + edge.getId());
            }
            if (!StringUtils.hasText(edge.getSource()) || !nodeById.containsKey(edge.getSource())) {
                errors.add("连线引用了不存在的起始节点：" + edge.getSource());
            }
            if (!StringUtils.hasText(edge.getTarget()) || !nodeById.containsKey(edge.getTarget())) {
                errors.add("连线引用了不存在的目标节点：" + edge.getTarget());
            }
        }

        if (!errors.isEmpty()) {
            throw new WorkflowModelValidationException(errors);
        }

        Map<String, List<EdgeDslV2>> outgoing = buildOutgoing(nodeById.keySet(), edges);
        Set<String> hasIncoming = new HashSet<>();
        for (EdgeDslV2 edge : edges) {
            hasIncoming.add(edge.getTarget());
        }
        for (ProcessNodeDslV2 node : nodes) {
            if (!(node instanceof StartNodeDslV2) && !hasIncoming.contains(node.getId())) {
                errors.add("节点 " + node.getId() + " 未被任何连线指向，属于孤立节点");
            }
        }

        if (startCount == 1) {
            errors.addAll(validateReachability(nodes, nodeById, outgoing));
        }
        errors.addAll(validateConditionNodes(nodes, outgoing));
        errors.addAll(validateApprovalNodes(nodes));
        errors.addAll(validateCcNodes(nodes));
        errors.addAll(validateAutoNodes(nodes));
        errors.addAll(validateEndNodes(nodes));
        errors.addAll(validateParallelBlocks(nodeById, outgoing));

        if (!errors.isEmpty()) {
            throw new WorkflowModelValidationException(errors);
        }
    }

    private static Map<String, List<EdgeDslV2>> buildOutgoing(Set<String> nodeIds, List<EdgeDslV2> edges) {
        Map<String, List<EdgeDslV2>> outgoing = new LinkedHashMap<>();
        for (String nodeId : nodeIds) {
            outgoing.put(nodeId, new ArrayList<>());
        }
        for (EdgeDslV2 edge : edges) {
            outgoing.computeIfAbsent(edge.getSource(), key -> new ArrayList<>()).add(edge);
        }
        return outgoing;
    }

    /**
     * 校验从开始节点必须存在到达任一结束节点的路径（BFS）。
     */
    private static List<String> validateReachability(
            List<ProcessNodeDslV2> nodes,
            Map<String, ProcessNodeDslV2> nodeById,
            Map<String, List<EdgeDslV2>> outgoing) {
        String startId = nodes.stream()
                .filter(StartNodeDslV2.class::isInstance)
                .map(ProcessNodeDslV2::getId)
                .findFirst()
                .orElseThrow();
        Set<String> visited = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(startId);
        visited.add(startId);
        boolean reachedEnd = false;
        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (nodeById.get(current) instanceof EndNodeDslV2) {
                reachedEnd = true;
            }
            for (EdgeDslV2 edge : outgoing.getOrDefault(current, List.of())) {
                if (visited.add(edge.getTarget())) {
                    queue.add(edge.getTarget());
                }
            }
        }
        return reachedEnd ? List.of() : List.of("从开始节点无法到达任何结束节点");
    }

    /**
     * 校验条件节点：至少一条兜底默认分支（无 condition）且 priority 最大；携带条件的出边
     * priority 在节点内唯一；条件 AST 结构合法。
     */
    private static List<String> validateConditionNodes(List<ProcessNodeDslV2> nodes, Map<String, List<EdgeDslV2>> outgoing) {
        List<String> errors = new ArrayList<>();
        for (ProcessNodeDslV2 node : nodes) {
            if (!(node instanceof ConditionNodeDslV2)) {
                continue;
            }
            List<EdgeDslV2> out = outgoing.getOrDefault(node.getId(), List.of());
            List<EdgeDslV2> withCondition = out.stream().filter(edge -> edge.getCondition() != null).toList();
            List<EdgeDslV2> withoutCondition = out.stream().filter(edge -> edge.getCondition() == null).toList();
            if (withoutCondition.isEmpty()) {
                errors.add("条件节点 " + node.getId() + " 缺少默认分支（未携带 condition 的兜底出边）");
            } else if (withoutCondition.size() > 1) {
                errors.add("条件节点 " + node.getId() + " 存在多条默认分支，只能有一条");
            }

            Set<Integer> priorities = new HashSet<>();
            for (EdgeDslV2 edge : out) {
                if (edge.getPriority() == null) {
                    errors.add("边 " + edge.getId() + " 缺少分支优先级 priority");
                } else if (!priorities.add(edge.getPriority())) {
                    errors.add("条件节点 " + node.getId() + " 存在重复的分支优先级：" + edge.getPriority());
                }
            }
            if (!withoutCondition.isEmpty() && !withCondition.isEmpty()) {
                int defaultPriority = withoutCondition.get(0).getPriority() == null ? Integer.MIN_VALUE : withoutCondition.get(0).getPriority();
                int maxConditionalPriority = withCondition.stream()
                        .map(EdgeDslV2::getPriority)
                        .filter(java.util.Objects::nonNull)
                        .max(Integer::compareTo)
                        .orElse(Integer.MIN_VALUE);
                if (defaultPriority < maxConditionalPriority) {
                    errors.add("条件节点 " + node.getId() + " 的默认分支 priority 必须大于其余条件分支");
                }
            }

            for (EdgeDslV2 edge : withCondition) {
                errors.addAll(validateConditionAst("边 " + edge.getId(), edge.getCondition()));
            }
        }
        return errors;
    }

    /**
     * 校验条件 AST 结构：逻辑连接符非空、条件项非空且不超过上限、每项字段/比较符/比较值合法。
     */
    private static List<String> validateConditionAst(String location, ConditionAstDsl ast) {
        List<String> errors = new ArrayList<>();
        if (ast.getLogic() == null) {
            errors.add(location + " 的条件缺少逻辑连接符 logic");
        }
        if (ast.getItems() == null || ast.getItems().isEmpty()) {
            errors.add(location + " 的条件至少需要一个条件项");
            return errors;
        }
        if (ast.getItems().size() > MAX_CONDITION_ITEMS) {
            errors.add(location + " 的条件项数量超过上限 " + MAX_CONDITION_ITEMS);
        }
        ast.getItems().forEach(item -> {
            if (!StringUtils.hasText(item.getField())) {
                errors.add(location + " 的条件项缺少字段 field");
            }
            if (item.getOp() == null) {
                errors.add(location + " 的条件项缺少比较符 op");
            }
            if (item.getOp() != ConditionOperator.IS_NULL && item.getValue() == null) {
                errors.add(location + " 的条件项缺少比较值 value");
            }
        });
        return errors;
    }

    /**
     * 校验审批节点：审批人来源类型合法且必填取值完整；无数据来源类型直接拒绝；会签比例范围；
     * 空审批人策略必填，{@code FALLBACK_ROLE} 须携带兜底角色编码；本轮
     * {@code rejectPolicy=THRESHOLD} 未实现，明确拒绝而非静默按 VETO 处理。
     */
    private static List<String> validateApprovalNodes(List<ProcessNodeDslV2> nodes) {
        List<String> errors = new ArrayList<>();
        for (ProcessNodeDslV2 node : nodes) {
            if (!(node instanceof ApprovalNodeDslV2 approval)) {
                continue;
            }
            String location = "审批节点 " + node.getId();
            if (approval.getAssignee() == null || approval.getAssignee().getType() == null) {
                errors.add(location + " 未配置审批人来源 assignee.type");
            } else {
                AssigneeTypeV2 type = approval.getAssignee().getType();
                if (NO_RESOLVER_TYPES.contains(type)) {
                    errors.add(location + " 的审批人来源 " + type + " 当前无数据来源，禁止发布");
                } else if ((type == AssigneeTypeV2.ROLE || type == AssigneeTypeV2.USER
                        || type == AssigneeTypeV2.FORM_REFERENCE_PERSON)
                        && !StringUtils.hasText(approval.getAssignee().getValue())) {
                    errors.add(location + " 的审批人来源 " + type + " 缺少必填的 assignee.value");
                }
            }
            if (approval.getVote() != null) {
                if (approval.getVote().getMode() == VoteMode.PERCENT) {
                    Integer percent = approval.getVote().getPercent();
                    if (percent == null || percent < 1 || percent > 100) {
                        errors.add(location + " 的会签比例 vote.percent 必须在 1~100 之间");
                    }
                }
                if (approval.getVote().getRejectPolicy() == RejectPolicy.THRESHOLD) {
                    errors.add(location + " 的反对票策略 THRESHOLD 本轮未实现，请使用 VETO");
                }
            }
            EmptyPolicy emptyPolicy = approval.getEmptyPolicy();
            if (emptyPolicy == EmptyPolicy.FALLBACK_ROLE && !StringUtils.hasText(approval.getFallbackRoleCode())) {
                errors.add(location + " 空审批人策略为 FALLBACK_ROLE 时必须配置 fallbackRoleCode");
            }
        }
        return errors;
    }

    /**
     * 校验抄送节点：接收人来源类型/取值必填。
     */
    private static List<String> validateCcNodes(List<ProcessNodeDslV2> nodes) {
        List<String> errors = new ArrayList<>();
        for (ProcessNodeDslV2 node : nodes) {
            if (!(node instanceof CcNodeDslV2 cc)) {
                continue;
            }
            if (!StringUtils.hasText(cc.getRecipientType())) {
                errors.add("抄送节点 " + node.getId() + " 未配置接收人来源 recipientType");
            }
        }
        return errors;
    }

    /**
     * 校验自动任务节点：{@code actionCode} 须在白名单注册表中存在（design.md Decision
     * 3/10"首轮仅内置可幂等动作，外部通用 HTTP 节点不开放"）。
     */
    private static List<String> validateAutoNodes(List<ProcessNodeDslV2> nodes) {
        List<String> errors = new ArrayList<>();
        for (ProcessNodeDslV2 node : nodes) {
            if (!(node instanceof AutoNodeDslV2 auto)) {
                continue;
            }
            if (!StringUtils.hasText(auto.getActionCode())) {
                errors.add("自动任务节点 " + node.getId() + " 未配置 actionCode");
            } else if (!AutoActionRegistry.isRegistered(auto.getActionCode())) {
                errors.add("自动任务节点 " + node.getId() + " 的 actionCode=" + auto.getActionCode() + " 不在白名单注册表中");
            }
        }
        return errors;
    }

    /**
     * 校验结束节点：{@code outcome} 必须是 {@code APPROVED}/{@code REJECTED}。
     */
    private static List<String> validateEndNodes(List<ProcessNodeDslV2> nodes) {
        List<String> errors = new ArrayList<>();
        for (ProcessNodeDslV2 node : nodes) {
            if (!(node instanceof EndNodeDslV2 end)) {
                continue;
            }
            if (!"APPROVED".equals(end.getOutcome()) && !"REJECTED".equals(end.getOutcome())) {
                errors.add("结束节点 " + node.getId() + " 的 outcome 必须是 APPROVED 或 REJECTED，当前：" + end.getOutcome());
            }
        }
        return errors;
    }

    /**
     * 校验并行块配对：分叉/汇合互相指向一致；块内节点集合（分叉到汇合之间、不越过汇合）
     * 与其余并行块要么不相交要么完全嵌套，禁止交叉重叠（design.md Decision 3/7"嵌套并行只
     * 允许配对块""禁止跨块连接"）。
     */
    private static List<String> validateParallelBlocks(
            Map<String, ProcessNodeDslV2> nodeById,
            Map<String, List<EdgeDslV2>> outgoing) {
        List<String> errors = new ArrayList<>();
        List<ParallelSplitNodeDslV2> splits = nodeById.values().stream()
                .filter(ParallelSplitNodeDslV2.class::isInstance)
                .map(ParallelSplitNodeDslV2.class::cast)
                .toList();

        Map<String, Set<String>> blockScopes = new LinkedHashMap<>();
        for (ParallelSplitNodeDslV2 split : splits) {
            String location = "并行分叉节点 " + split.getId();
            if (!StringUtils.hasText(split.getJoinNodeId()) || !nodeById.containsKey(split.getJoinNodeId())) {
                errors.add(location + " 未指定有效的配对汇合节点 joinNodeId");
                continue;
            }
            ProcessNodeDslV2 joinNode = nodeById.get(split.getJoinNodeId());
            if (!(joinNode instanceof ParallelJoinNodeDslV2 join)) {
                errors.add(location + " 指向的 joinNodeId=" + split.getJoinNodeId() + " 不是并行汇合节点");
                continue;
            }
            if (!split.getId().equals(join.getSplitNodeId())) {
                errors.add(location + " 与汇合节点 " + join.getId() + " 的配对指针不一致");
                continue;
            }
            List<EdgeDslV2> branches = outgoing.getOrDefault(split.getId(), List.of());
            if (branches.size() < 2) {
                errors.add(location + " 至少需要两条并行分支");
            }
            Set<String> scope = computeBlockScope(split.getId(), join.getId(), outgoing);
            if (!allBranchesReachJoin(split.getId(), join.getId(), outgoing, scope)) {
                errors.add(location + " 存在分支未汇合到配对的汇合节点 " + join.getId());
            }
            blockScopes.put(split.getId(), scope);
        }

        List<String> scopeKeys = new ArrayList<>(blockScopes.keySet());
        for (int i = 0; i < scopeKeys.size(); i++) {
            for (int j = i + 1; j < scopeKeys.size(); j++) {
                Set<String> a = blockScopes.get(scopeKeys.get(i));
                Set<String> b = blockScopes.get(scopeKeys.get(j));
                boolean disjoint = a.stream().noneMatch(b::contains);
                boolean nested = a.containsAll(b) || b.containsAll(a);
                if (!disjoint && !nested) {
                    errors.add("并行块 " + scopeKeys.get(i) + " 与 " + scopeKeys.get(j) + " 存在交叉重叠，仅允许不相交或完全嵌套");
                }
            }
        }
        return errors;
    }

    /**
     * 计算并行块作用域：从分叉节点 BFS，遇到配对汇合节点即停止扩展（不越过汇合），收集到的
     * 全部节点 id（不含分叉/汇合自身）。
     */
    private static Set<String> computeBlockScope(String splitId, String joinId, Map<String, List<EdgeDslV2>> outgoing) {
        Set<String> scope = new LinkedHashSet<>();
        Set<String> visited = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        for (EdgeDslV2 edge : outgoing.getOrDefault(splitId, List.of())) {
            if (!edge.getTarget().equals(joinId)) {
                queue.add(edge.getTarget());
                visited.add(edge.getTarget());
            }
        }
        while (!queue.isEmpty()) {
            String current = queue.poll();
            scope.add(current);
            for (EdgeDslV2 edge : outgoing.getOrDefault(current, List.of())) {
                if (!edge.getTarget().equals(joinId) && visited.add(edge.getTarget())) {
                    queue.add(edge.getTarget());
                }
            }
        }
        return scope;
    }

    /**
     * 校验分叉的每条直接分支最终都能到达配对的汇合节点，不遗留分支直接导向块外或普通结束。
     */
    private static boolean allBranchesReachJoin(
            String splitId,
            String joinId,
            Map<String, List<EdgeDslV2>> outgoing,
            Set<String> scope) {
        for (EdgeDslV2 branch : outgoing.getOrDefault(splitId, List.of())) {
            if (branch.getTarget().equals(joinId)) {
                continue;
            }
            if (!canReach(branch.getTarget(), joinId, outgoing, scope)) {
                return false;
            }
        }
        return true;
    }

    private static boolean canReach(
            String from,
            String joinId,
            Map<String, List<EdgeDslV2>> outgoing,
            Set<String> scope) {
        Set<String> visited = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(from);
        visited.add(from);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (current.equals(joinId)) {
                return true;
            }
            for (EdgeDslV2 edge : outgoing.getOrDefault(current, List.of())) {
                String next = edge.getTarget();
                if (!next.equals(joinId) && !scope.contains(next)) {
                    continue;
                }
                if (visited.add(next)) {
                    queue.add(next);
                }
            }
        }
        return false;
    }
}
