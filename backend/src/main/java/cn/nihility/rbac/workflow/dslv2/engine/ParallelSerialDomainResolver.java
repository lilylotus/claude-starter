package cn.nihility.rbac.workflow.dslv2.engine;

import cn.nihility.rbac.workflow.dslv2.dto.EdgeDslV2;
import cn.nihility.rbac.workflow.dslv2.dto.ParallelJoinNodeDslV2;
import cn.nihility.rbac.workflow.dslv2.dto.ParallelSplitNodeDslV2;
import cn.nihility.rbac.workflow.dslv2.dto.ProcessModelDslV2;
import cn.nihility.rbac.workflow.dslv2.dto.ProcessNodeDslV2;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;

/**
 * 运行时判断 DSL v2 两个节点是否处于"同一串行域"的工具（production-approval-lifecycle change
 * design.md 第7节"退回只退到同一串行域中实际完成且配置可退的节点；拒绝跨并行块/跨MI边界/
 * 未经过节点"，tasks.md 6.6）。与
 * {@link cn.nihility.rbac.workflow.dslv2.compiler.ProcessModelDslV2Validator} 发布期"并行块
 * 配对/嵌套/交叉重叠"校验使用同一套"从分叉节点 BFS、遇到配对汇合节点即停止扩展"算法思路，
 * 但服务于运行时退回校验这一独立场景，独立实现、不复用其私有方法，避免相互牵连（不修改
 * validator 现有校验行为与其既有测试）。
 * <p>
 * 定义"串行域签名"：节点所处每一层嵌套并行块的 {@code 分叉节点id → 所属分支根节点id} 映射；
 * 不在任何并行块作用域内的节点签名为空映射。两个节点仅当签名完全相等时才视为同一串行域——
 * 既拒绝"跨并行块"（签名的分叉节点 id 集合不同），也拒绝"同一并行块内跨分支"（签名某一层的
 * 分支根节点不同——并行分支彼此并发执行，各自是独立的串行域，退回跨分支会破坏未参与退回的
 * 那条分支仍在运行的 token，design.md Non-Goals 明确排除）。
 */
public final class ParallelSerialDomainResolver {

    /** 工具类不允许实例化。 */
    private ParallelSerialDomainResolver() {
    }

    /**
     * 判断两个节点是否处于同一串行域（同一节点视为同一串行域）。
     *
     * @param dsl     流程模型 DSL v2（发布时刻快照），{@code null} 或无节点时视为不存在并行块，
     *                一律判定同一串行域
     * @param nodeIdA 节点 A id
     * @param nodeIdB 节点 B id
     * @return {@code true} 表示同一串行域，允许退回；{@code false} 表示跨串行域，应当拒绝
     */
    public static boolean sameSerialDomain(ProcessModelDslV2 dsl, String nodeIdA, String nodeIdB) {
        if (nodeIdA.equals(nodeIdB)) {
            return true;
        }
        return domainSignature(dsl, nodeIdA).equals(domainSignature(dsl, nodeIdB));
    }

    /**
     * 计算节点的串行域签名：从外到内每一层并行块的 {@code 分叉节点id → 所属分支根节点id}。
     */
    private static Map<String, String> domainSignature(ProcessModelDslV2 dsl, String nodeId) {
        Map<String, String> signature = new LinkedHashMap<>();
        if (dsl == null || dsl.getNodes() == null || dsl.getNodes().isEmpty()) {
            return signature;
        }
        Map<String, ProcessNodeDslV2> nodeById = new LinkedHashMap<>();
        for (ProcessNodeDslV2 node : dsl.getNodes()) {
            if (node != null && StringUtils.hasText(node.getId())) {
                nodeById.put(node.getId(), node);
            }
        }
        Map<String, List<EdgeDslV2>> outgoing = new HashMap<>();
        List<EdgeDslV2> edges = dsl.getEdges() == null ? List.of() : dsl.getEdges();
        for (EdgeDslV2 edge : edges) {
            if (edge == null || edge.getSource() == null || edge.getTarget() == null) {
                continue;
            }
            outgoing.computeIfAbsent(edge.getSource(), key -> new ArrayList<>()).add(edge);
        }

        for (ProcessNodeDslV2 node : nodeById.values()) {
            if (!(node instanceof ParallelSplitNodeDslV2 split)) {
                continue;
            }
            ProcessNodeDslV2 joinNode = nodeById.get(split.getJoinNodeId());
            if (!(joinNode instanceof ParallelJoinNodeDslV2 join) || !split.getId().equals(join.getSplitNodeId())) {
                // 未正确配对的分叉/汇合节点在发布期已被 ProcessModelDslV2Validator 拒绝，
                // 运行时只做防御性跳过，不重复报错。
                continue;
            }
            String owner = computeBranchOwners(split.getId(), join.getId(), outgoing).get(nodeId);
            if (owner != null) {
                signature.put(split.getId(), owner);
            }
        }
        return signature;
    }

    /**
     * 从并行分叉的每条直接分支各自做一次 BFS（不越过配对的汇合节点、不跨越其余分支），
     * 划分并行块作用域内每个节点归属的分支根节点 id。
     */
    private static Map<String, String> computeBranchOwners(
            String splitId, String joinId, Map<String, List<EdgeDslV2>> outgoing) {
        Map<String, String> owner = new HashMap<>();
        for (EdgeDslV2 branch : outgoing.getOrDefault(splitId, List.of())) {
            String branchRoot = branch.getTarget();
            if (branchRoot.equals(joinId) || owner.containsKey(branchRoot)) {
                continue;
            }
            owner.put(branchRoot, branchRoot);
            Deque<String> queue = new ArrayDeque<>();
            queue.add(branchRoot);
            while (!queue.isEmpty()) {
                String current = queue.poll();
                for (EdgeDslV2 edge : outgoing.getOrDefault(current, List.of())) {
                    String next = edge.getTarget();
                    if (next.equals(joinId) || owner.containsKey(next)) {
                        continue;
                    }
                    owner.put(next, branchRoot);
                    queue.add(next);
                }
            }
        }
        return owner;
    }
}
