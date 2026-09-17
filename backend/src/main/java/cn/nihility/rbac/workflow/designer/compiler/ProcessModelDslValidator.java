package cn.nihility.rbac.workflow.designer.compiler;

import cn.nihility.rbac.formfield.constant.FormFieldBizType;
import cn.nihility.rbac.formfield.constant.FormFieldControlType;
import cn.nihility.rbac.formfield.dto.FormFieldRenderItemVO;
import cn.nihility.rbac.formfield.service.FormFieldDefinitionService;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Workflow JSON DSL 结构与业务规则校验器，发布前的权威校验（前端保存草稿时的基础校验规则
 * 与本类保持一致，避免前后端校验规则漂移，workflow-approval-engine change design.md
 * Decision 9 / specs/workflow-process-designer"发布前结构与业务规则的强制校验"Requirement）。
 * 校验规则：唯一开始节点、至少一个结束节点、节点 id 唯一、边引用的节点必须存在、开始到结束
 * 存在可达路径、条件节点已配置的带条件出边引用字段真实存在于对应业务类型的启用表单字段定义中
 * （workflow-condition-payload-fields change design.md Decision 2/4）、审批节点审批人来源
 * 相关必填字段完整。条件节点的兜底默认分支不要求使用者手动配置，缺失时由编译器自动补全，
 * 不属于本校验器的拒绝发布项（workflow-condition-auto-default-branch change design.md
 * Decision 1）。所有校验失败一次性收集后统一抛出，携带具体节点/连线定位信息，不是发现
 * 第一个错误就短路返回。
 * <p>
 * 注入 {@link FormFieldDefinitionService} 校验条件字段的真实存在性，不再是纯静态工具类。
 */
@Component
@RequiredArgsConstructor
public class ProcessModelDslValidator {

    /** 条件比较符白名单，禁止使用者直接输入自由表达式字符串。 */
    private static final Set<String> ALLOWED_OPERATORS = Set.of("EQ", "NE", "GT", "GTE", "LT", "LTE");

    /** 文本框/字典下拉字段仅允许的比较符：只能判等/判不等，大小比较对这两类控件类型无意义。 */
    private static final Set<String> TEXT_LIKE_ALLOWED_OPERATORS = Set.of("EQ", "NE");

    /** 允许作为条件字段所属的业务对象类型。 */
    private static final Set<String> ALLOWED_FIELD_BIZ_TYPES = Set.of(
            FormFieldBizType.ORG, FormFieldBizType.USER, FormFieldBizType.POSITION, FormFieldBizType.APP);

    /** 表单字段定义业务逻辑接口，用于校验条件字段真实存在于对应业务类型的启用字段定义中。 */
    private final FormFieldDefinitionService formFieldDefinitionService;

    /**
     * 校验流程模型 DSL，校验失败抛出携带全部错误明细的
     * {@link WorkflowModelValidationException}。
     *
     * @param dsl 待校验的流程模型 DSL
     */
    public void validate(ProcessModelDsl dsl) {
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
     * 校验条件节点已存在的带条件出边：字段/比较符/比较值完整合法，字段须存在于对应业务类型
     * 的启用表单字段定义中且非多选字典，比较符按字段控件类型收窄（workflow-condition-
     * payload-fields change design.md Decision 2/4）。条件节点的兜底默认分支不再作为拒绝
     * 发布的校验项——缺少无条件出边时由 {@code WorkflowModelCompilerImpl} 在编译期自动补全
     * （workflow-condition-auto-default-branch change design.md Decision 1）。
     */
    private List<String> validateConditionNodes(List<ProcessNodeDsl> nodes, Map<String, List<EdgeDsl>> outgoing) {
        List<String> errors = new ArrayList<>();
        Map<String, List<FormFieldRenderItemVO>> renderSchemaCache = new HashMap<>();
        for (ProcessNodeDsl node : nodes) {
            if (!(node instanceof ConditionNodeDsl)) {
                continue;
            }
            List<EdgeDsl> out = outgoing.getOrDefault(node.getId(), List.of());
            for (EdgeDsl edge : out) {
                EdgeConditionDsl condition = edge.getCondition();
                if (condition == null) {
                    continue;
                }
                String edgeLocation = "边 " + edge.getFrom() + "->" + edge.getTo();
                errors.addAll(validateCondition(edgeLocation, condition, renderSchemaCache));
            }
        }
        return errors;
    }

    /**
     * 校验单条条件边的字段/比较符/比较值。
     */
    private List<String> validateCondition(
            String edgeLocation,
            EdgeConditionDsl condition,
            Map<String, List<FormFieldRenderItemVO>> renderSchemaCache) {
        List<String> errors = new ArrayList<>();
        if (!StringUtils.hasText(condition.getFieldBizType())
                || !ALLOWED_FIELD_BIZ_TYPES.contains(condition.getFieldBizType())) {
            errors.add(edgeLocation + " 的条件字段所属业务对象类型 fieldBizType 不合法（仅支持 ORG/USER/POSITION/APP）："
                    + condition.getFieldBizType());
        }
        if (!StringUtils.hasText(condition.getField())) {
            errors.add(edgeLocation + " 的条件缺少字段 field");
        }
        if (!StringUtils.hasText(condition.getOperator()) || !ALLOWED_OPERATORS.contains(condition.getOperator())) {
            errors.add(edgeLocation + " 的比较符不在允许范围内（仅支持 EQ/NE/GT/GTE/LT/LTE）：" + condition.getOperator());
        }
        if (condition.getValue() == null) {
            errors.add(edgeLocation + " 的条件缺少比较值 value");
        }

        if (!errors.isEmpty()) {
            // 字段所属业务对象类型或字段本身缺失/非法时，无法继续查找字段定义，避免连带误报。
            return errors;
        }

        FormFieldRenderItemVO field = findField(condition.getFieldBizType(), condition.getField(), renderSchemaCache);
        if (field == null) {
            errors.add(edgeLocation + " 引用的字段 " + condition.getFieldBizType() + "." + condition.getField()
                    + " 不存在于该业务类型的启用表单字段定义中");
            return errors;
        }
        if (Integer.valueOf(FormFieldControlType.MULTI_DICT).equals(field.getControlType())) {
            errors.add(edgeLocation + " 引用的字段 " + condition.getField() + " 是多选字典类型，不能作为条件字段");
            return errors;
        }
        if (isTextLikeControlType(field.getControlType())
                && ALLOWED_OPERATORS.contains(condition.getOperator())
                && !TEXT_LIKE_ALLOWED_OPERATORS.contains(condition.getOperator())) {
            errors.add(edgeLocation + " 引用的字段 " + condition.getField()
                    + " 是文本框/字典下拉类型，比较符仅允许 EQ/NE：" + condition.getOperator());
        }
        return errors;
    }

    /**
     * 判断控件类型是否为文本框或字典下拉（比较符限定为 EQ/NE）。
     */
    private boolean isTextLikeControlType(Integer controlType) {
        return Integer.valueOf(FormFieldControlType.TEXT).equals(controlType)
                || Integer.valueOf(FormFieldControlType.DICT).equals(controlType);
    }

    /**
     * 按业务类型 + 字段标识查找字段渲染元数据，业务类型维度的查询结果按调用方传入的缓存
     * 复用，避免同一业务类型的多条条件边重复查询。
     */
    private FormFieldRenderItemVO findField(
            String fieldBizType, String fieldCode, Map<String, List<FormFieldRenderItemVO>> renderSchemaCache) {
        List<FormFieldRenderItemVO> schema = renderSchemaCache.computeIfAbsent(
                fieldBizType, formFieldDefinitionService::buildRenderSchema);
        return schema.stream()
                .filter(item -> fieldCode.equals(item.getFieldCode()))
                .findFirst()
                .orElse(null);
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
