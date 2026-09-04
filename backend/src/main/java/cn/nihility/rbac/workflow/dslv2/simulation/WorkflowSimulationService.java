package cn.nihility.rbac.workflow.dslv2.simulation;

import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.common.util.JacksonUtils;
import cn.nihility.rbac.workflow.assignee.AssigneeResolveContext;
import cn.nihility.rbac.workflow.assignee.AssigneeResolverRegistry;
import cn.nihility.rbac.workflow.constant.AssigneeType;
import cn.nihility.rbac.workflow.dslv2.compiler.ConditionAstEvaluator;
import cn.nihility.rbac.workflow.dslv2.constant.AssigneeTypeV2;
import cn.nihility.rbac.workflow.dslv2.constant.EmptyPolicy;
import cn.nihility.rbac.workflow.dslv2.constant.SelfPolicy;
import cn.nihility.rbac.workflow.dslv2.dto.ApprovalNodeDslV2;
import cn.nihility.rbac.workflow.dslv2.dto.AssigneeConfigDsl;
import cn.nihility.rbac.workflow.dslv2.dto.ConditionNodeDslV2;
import cn.nihility.rbac.workflow.dslv2.dto.EdgeDslV2;
import cn.nihility.rbac.workflow.dslv2.dto.ProcessModelDslV2;
import cn.nihility.rbac.workflow.dslv2.dto.ProcessNodeDslV2;
import cn.nihility.rbac.workflow.dslv2.dto.StartNodeDslV2;
import cn.nihility.rbac.workflow.entity.ProcessDefinitionEntity;
import cn.nihility.rbac.workflow.entity.ProcessModelEntity;
import cn.nihility.rbac.workflow.mapper.ProcessDefinitionMapper;
import cn.nihility.rbac.workflow.mapper.ProcessModelMapper;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 快速预演服务：沿 DSL v2 图从 {@code START} 节点出发做静态遍历，条件节点用
 * {@link ConditionAstEvaluator} 求值分支，审批节点用 {@link AssigneeResolverRegistry} 按
 * 模拟身份解析候选人，不创建任何 Flowable 实例、不写任何 {@code tab_wf_process_instance}/
 * {@code tab_wf_approval_task} 等真实运行数据（production-approval-lifecycle change
 * design.md 第 4 节"试运行分两层"第一层，tasks.md 4.2）。
 */
@Service
@RequiredArgsConstructor
public class WorkflowSimulationService {

    /** 预演模式标记，恒为该值，区分于本轮未实现的"独立测试环境真实试运行"。 */
    private static final String MODE_QUICK_PREVIEW = "QUICK_PREVIEW";

    /** 流程模型数据访问接口。 */
    private final ProcessModelMapper processModelMapper;

    /** 流程定义数据访问接口。 */
    private final ProcessDefinitionMapper processDefinitionMapper;

    /** 审批人解析器注册表。 */
    private final AssigneeResolverRegistry assigneeResolverRegistry;

    /**
     * 对指定流程模型执行一次快速预演。
     *
     * @param modelId 流程模型 id
     * @param request 预演请求：草稿或指定 definitionId + 模拟表单值 + 模拟申请人身份
     * @return 预演报告
     */
    public SimulationResultVO simulate(Long modelId, SimulationRequest request) {
        ProcessModelDslV2 dsl = loadDsl(modelId, request.getDefinitionId());
        Map<String, ProcessNodeDslV2> nodesById = new LinkedHashMap<>();
        for (ProcessNodeDslV2 node : dsl.getNodes()) {
            nodesById.put(node.getId(), node);
        }
        Map<String, List<EdgeDslV2>> outgoingBySource = new LinkedHashMap<>();
        for (EdgeDslV2 edge : dsl.getEdges()) {
            outgoingBySource.computeIfAbsent(edge.getSource(), key -> new ArrayList<>()).add(edge);
        }
        outgoingBySource.values().forEach(edges -> edges.sort(
                Comparator.comparing(EdgeDslV2::getPriority, Comparator.nullsLast(Comparator.naturalOrder()))));

        String startId = dsl.getNodes().stream()
                .filter(StartNodeDslV2.class::isInstance)
                .map(ProcessNodeDslV2::getId)
                .findFirst()
                .orElseThrow(() -> new BusinessException("流程模型缺少开始节点，无法预演"));

        Map<String, Object> formValues = request.getFormValues() == null ? Map.of() : request.getFormValues();
        List<String> hitPath = new ArrayList<>();
        List<ApprovalNodeSimulationVO> approvalResolutions = new ArrayList<>();
        List<UncoveredBranchVO> uncoveredBranches = new ArrayList<>();
        Set<String> visited = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(startId);

        while (!queue.isEmpty()) {
            String nodeId = queue.poll();
            if (!visited.add(nodeId)) {
                // 并行汇合节点会被两条分支各引用一次，只在首次到达时展开一次下游，避免重复
                // 遍历、也避免下游被重复计入命中路径。
                continue;
            }
            hitPath.add(nodeId);
            ProcessNodeDslV2 node = nodesById.get(nodeId);
            if (node == null) {
                // 悬空边引用的节点 id 不存在：结构合法性由发布前的 ProcessModelDslV2Validator
                // 负责拦截，预演阶段对草稿容错跳过，不中断整体预演。
                continue;
            }
            if (node instanceof ApprovalNodeDslV2 approval) {
                approvalResolutions.add(resolveApprovalNode(approval, request));
            }

            List<EdgeDslV2> outgoing = outgoingBySource.getOrDefault(nodeId, List.of());
            if (node instanceof ConditionNodeDslV2) {
                branchCondition(nodeId, outgoing, formValues, queue, uncoveredBranches);
            } else {
                // START/APPROVAL/PARALLEL_SPLIT/PARALLEL_JOIN/CC/AUTO 均按全部出边展开：
                // PARALLEL_SPLIT 天然对应"两条分支都展开"，其余节点正常只有一条默认出边。
                outgoing.forEach(edge -> queue.add(edge.getTarget()));
            }
        }

        return SimulationResultVO.builder()
                .mode(MODE_QUICK_PREVIEW)
                .hitPath(hitPath)
                .approvalResolutions(approvalResolutions)
                .uncoveredBranches(uncoveredBranches)
                .build();
    }

    /**
     * 条件节点分支决策：按 {@code priority} 从小到大取第一个条件命中的分支，均未命中则取
     * 无条件的默认分支；未被选中的分支记入"未覆盖分支列表"，不展开下游。
     */
    private void branchCondition(
            String nodeId,
            List<EdgeDslV2> outgoing,
            Map<String, Object> formValues,
            Deque<String> queue,
            List<UncoveredBranchVO> uncoveredBranches) {
        if (outgoing.isEmpty()) {
            return;
        }
        EdgeDslV2 winner = outgoing.stream()
                .filter(edge -> edge.getCondition() != null)
                .filter(edge -> ConditionAstEvaluator.evaluate(edge.getCondition(), formValues))
                .findFirst()
                .orElse(null);
        if (winner == null) {
            winner = outgoing.stream().filter(edge -> edge.getCondition() == null).findFirst().orElse(null);
        }
        if (winner == null) {
            // 无命中分支且无默认分支：正常发布前会被结构校验拒绝，预演阶段对草稿容错记录、
            // 不抛异常中断整体预演。
            uncoveredBranches.add(UncoveredBranchVO.builder()
                    .sourceNodeId(nodeId)
                    .reason("全部分支均未命中且无默认分支，路径在此中断")
                    .build());
        }
        for (EdgeDslV2 edge : outgoing) {
            if (edge == winner) {
                queue.add(edge.getTarget());
            } else {
                uncoveredBranches.add(UncoveredBranchVO.builder()
                        .edgeId(edge.getId())
                        .sourceNodeId(nodeId)
                        .targetNodeId(edge.getTarget())
                        .reason(edge.getCondition() == null ? "存在优先命中分支，默认分支未展开" : "条件未命中，未展开该分支")
                        .build());
            }
        }
    }

    /**
     * 解析单个审批节点：按来源类型解析候选人，应用自审排除、空审批人策略（{@code BLOCK}/
     * {@code FALLBACK_ROLE}）兜底，与运行时 {@code NodeAssigneeResolutionService} 语义一致，
     * 但不依赖已持久化的 {@code tab_wf_node_assignee_rule} 行——快速预演操作的是尚未发布的
     * 草稿，独立实现同一套解析步骤，同时对 {@code APP_ADMIN}/{@code FORM_REFERENCE_PERSON}
     * 等 v1 无对应枚举值的来源类型容错处理为"无数据来源恒为空"，不因草稿引用了发布前会被
     * 拒绝的来源类型而抛异常中断预演。
     */
    private ApprovalNodeSimulationVO resolveApprovalNode(ApprovalNodeDslV2 approval, SimulationRequest request) {
        AssigneeConfigDsl assigneeConfig = approval.getAssignee();
        AssigneeTypeV2 typeV2 = assigneeConfig == null ? null : assigneeConfig.getType();
        StringBuilder basis = new StringBuilder();
        Set<Long> resolved;
        if (typeV2 == null) {
            resolved = Set.of();
            basis.append("未配置审批人来源");
        } else {
            AssigneeType v1Type = mapAssigneeType(typeV2);
            if (v1Type == null) {
                resolved = Set.of();
                basis.append("审批人来源类型 ").append(typeV2).append(" 当前无数据来源，解析结果恒为空");
            } else {
                AssigneeResolveContext context = new AssigneeResolveContext(
                        null, approval.getId(), assigneeConfig.getValue(),
                        request.getApplicantId(), request.getApplicantOrgId(),
                        assigneeConfig.getOrgSource(), assigneeConfig.getOrgId());
                resolved = assigneeResolverRegistry.resolve(v1Type, context);
                basis.append("来源类型=").append(typeV2)
                        .append("，取值=").append(assigneeConfig.getValue())
                        .append("，命中 ").append(resolved.size()).append(" 人");
            }
        }

        boolean selfExcluded = approval.getSelfPolicy() != SelfPolicy.ALLOW
                && request.getApplicantId() != null
                && resolved.size() == 1
                && resolved.contains(request.getApplicantId());
        if (selfExcluded) {
            resolved = Set.of();
            basis.append("；命中人为申请人本人且节点禁止自审，已排除");
        }

        boolean empty = resolved.isEmpty();
        if (empty && approval.getEmptyPolicy() == EmptyPolicy.FALLBACK_ROLE
                && StringUtils.hasText(approval.getFallbackRoleCode())) {
            Set<Long> fallback = assigneeResolverRegistry.resolve(AssigneeType.ROLE,
                    new AssigneeResolveContext(
                            null, approval.getId(), approval.getFallbackRoleCode(), null, null, null, null));
            if (!fallback.isEmpty()) {
                resolved = fallback;
                empty = false;
                basis.append("；空审批人按兜底角色 ").append(approval.getFallbackRoleCode())
                        .append(" 解析，命中 ").append(fallback.size()).append(" 人");
            } else {
                basis.append("；兜底角色 ").append(approval.getFallbackRoleCode()).append(" 仍解析为空，按 BLOCK 阻塞");
            }
        } else if (empty) {
            basis.append("；空审批人策略 BLOCK，进入待分配");
        }

        return ApprovalNodeSimulationVO.builder()
                .nodeId(approval.getId())
                .nodeName(approval.getName())
                .candidateUserIds(new ArrayList<>(resolved))
                .resolveBasis(basis.toString())
                .emptyAssignee(empty)
                .build();
    }

    /**
     * {@link AssigneeTypeV2} → v1 {@link AssigneeType} 映射；{@code APP_ADMIN}/
     * {@code FORM_REFERENCE_PERSON} 在 v1 无对应枚举值（当前无数据来源，发布前会被
     * {@code ProcessModelDslV2Validator} 拒绝），映射为 {@code null} 交由调用方按"无数据
     * 来源恒为空"处理，不直接 {@link AssigneeType#valueOf} 抛异常。
     */
    private AssigneeType mapAssigneeType(AssigneeTypeV2 type) {
        return switch (type) {
            case APP_ADMIN, FORM_REFERENCE_PERSON -> null;
            default -> AssigneeType.valueOf(type.name());
        };
    }

    /**
     * 加载待预演的 DSL v2：优先按 {@code definitionId} 取已发布版本的快照，否则取流程模型
     * 当前草稿；两者均须是 {@code schemaVersion=2}，快速预演不支持 v1 定义。
     */
    private ProcessModelDslV2 loadDsl(Long modelId, Long definitionId) {
        String modelJson;
        if (definitionId != null) {
            ProcessDefinitionEntity definition = processDefinitionMapper.selectById(definitionId);
            if (definition == null) {
                throw new BusinessException("流程定义不存在");
            }
            if (!Objects.equals(definition.getProcessModelId(), modelId)) {
                throw new BusinessException("流程定义与流程模型不匹配");
            }
            if (definition.getSchemaVersion() == null || definition.getSchemaVersion() != 2) {
                throw new BusinessException("快速预演仅支持 schemaVersion=2 的流程定义");
            }
            modelJson = definition.getModelJsonSnapshot();
        } else {
            ProcessModelEntity model = processModelMapper.selectById(modelId);
            if (model == null) {
                throw new BusinessException("流程模型不存在");
            }
            if (!StringUtils.hasText(model.getModelJson())) {
                throw new BusinessException("流程模型草稿为空，无法预演");
            }
            modelJson = model.getModelJson();
        }

        ProcessModelDslV2 dsl;
        try {
            dsl = JacksonUtils.toObj(modelJson, ProcessModelDslV2.class);
        } catch (RuntimeException ex) {
            throw new BusinessException("流程草稿 DSL 解析失败，无法预演：" + ex.getMessage());
        }
        if (dsl == null || dsl.getSchemaVersion() == null || dsl.getSchemaVersion() != 2) {
            throw new BusinessException("快速预演仅支持 schemaVersion=2 的草稿");
        }
        return dsl;
    }
}
