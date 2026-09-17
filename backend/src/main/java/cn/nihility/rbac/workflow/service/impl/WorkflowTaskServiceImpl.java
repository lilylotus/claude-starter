package cn.nihility.rbac.workflow.service.impl;

import cn.nihility.rbac.workflow.assignee.support.AdminRoleLookupService;
import cn.nihility.rbac.workflow.assignee.support.TaskAuthorizationService;
import cn.nihility.rbac.workflow.constant.ApprovalAction;
import cn.nihility.rbac.workflow.constant.CandidateType;
import cn.nihility.rbac.workflow.constant.ProcessGraphNodeStatus;
import cn.nihility.rbac.workflow.constant.TaskStatus;
import cn.nihility.rbac.workflow.dto.ApprovalRecordVO;
import cn.nihility.rbac.workflow.dto.ApprovalTaskVO;
import cn.nihility.rbac.workflow.dto.CurrentApproverVO;
import cn.nihility.rbac.workflow.dto.OpenNodeVO;
import cn.nihility.rbac.workflow.dto.ProcessGraphEdgeVO;
import cn.nihility.rbac.workflow.dto.ProcessGraphNodeVO;
import cn.nihility.rbac.workflow.dto.ProcessInstanceDetailVO;
import cn.nihility.rbac.workflow.dto.TaskQuery;
import cn.nihility.rbac.workflow.entity.ApprovalRecordEntity;
import cn.nihility.rbac.workflow.entity.ApprovalTaskCandidateEntity;
import cn.nihility.rbac.workflow.entity.ApprovalTaskEntity;
import cn.nihility.rbac.workflow.entity.ProcessDefinitionEntity;
import cn.nihility.rbac.workflow.entity.ProcessInstanceEntity;
import cn.nihility.rbac.workflow.graph.ProcessGraph;
import cn.nihility.rbac.workflow.graph.ProcessGraphAssembler;
import cn.nihility.rbac.workflow.mapper.ApprovalRecordMapper;
import cn.nihility.rbac.workflow.mapper.ApprovalTaskCandidateMapper;
import cn.nihility.rbac.workflow.mapper.ApprovalTaskMapper;
import cn.nihility.rbac.workflow.mapper.ProcessDefinitionMapper;
import cn.nihility.rbac.workflow.mapper.ProcessInstanceMapper;
import cn.nihility.rbac.workflow.mapstruct.WorkflowConvert;
import cn.nihility.rbac.workflow.service.WorkflowTaskService;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.role.entity.RoleEntity;
import cn.nihility.rbac.role.mapper.RoleMapper;
import cn.nihility.rbac.user.service.UserDisplayService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * {@link WorkflowTaskService} 实现：全部基于自有业务表查询，不依赖 Flowable 运行时/历史表。
 */
@Service
@RequiredArgsConstructor
public class WorkflowTaskServiceImpl implements WorkflowTaskService {

    /** 计入"已办"的动作类型集合。 */
    private static final List<String> DONE_ACTIONS = List.of(
            ApprovalAction.APPROVE, ApprovalAction.REJECT, ApprovalAction.RETURN,
            ApprovalAction.TRANSFER, ApprovalAction.DELEGATE, ApprovalAction.ADD_SIGN);

    /** 审批任务数据访问接口。 */
    private final ApprovalTaskMapper approvalTaskMapper;

    /** 审批任务候选人明细数据访问接口。 */
    private final ApprovalTaskCandidateMapper approvalTaskCandidateMapper;

    /** 审批轨迹数据访问接口。 */
    private final ApprovalRecordMapper approvalRecordMapper;

    /** 流程实例数据访问接口。 */
    private final ProcessInstanceMapper processInstanceMapper;

    /** 流程定义（不可变发布版本快照）数据访问接口，用于读取完整节点/连线图的 DSL 快照。 */
    private final ProcessDefinitionMapper processDefinitionMapper;

    /** 管理员角色查询辅助组件，用于候选角色维度匹配。 */
    private final AdminRoleLookupService adminRoleLookupService;

    /** 单任务候选人越权校验组件，复用于流程实例详情的参与关系校验。 */
    private final TaskAuthorizationService taskAuthorizationService;

    /** 流程定义 DSL 快照 → 只读节点/连线图解析组件。 */
    private final ProcessGraphAssembler processGraphAssembler;

    /** 用户展示名解析服务。 */
    private final UserDisplayService userDisplayService;

    /** 角色数据访问接口，用于解析 {@code ROLE} 类型候选人的角色展示名称。 */
    private final RoleMapper roleMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ApprovalTaskVO> findTodoTasks(Long userId, TaskQuery query) {
        Set<Long> taskIds = new HashSet<>();

        List<ApprovalTaskEntity> assigneeTasks = approvalTaskMapper.selectList(new LambdaQueryWrapper<ApprovalTaskEntity>()
                .eq(ApprovalTaskEntity::getAssigneeId, userId)
                .in(ApprovalTaskEntity::getStatus, TaskStatus.PENDING, TaskStatus.CLAIMED));
        assigneeTasks.forEach(task -> taskIds.add(task.getId()));

        List<ApprovalTaskCandidateEntity> userCandidates = approvalTaskCandidateMapper.selectList(
                new LambdaQueryWrapper<ApprovalTaskCandidateEntity>()
                        .eq(ApprovalTaskCandidateEntity::getCandidateType, CandidateType.USER)
                        .eq(ApprovalTaskCandidateEntity::getCandidateValue, String.valueOf(userId)));
        Set<Long> candidateTaskIds = userCandidates.stream().map(ApprovalTaskCandidateEntity::getTaskId)
                .collect(Collectors.toCollection(HashSet::new));

        List<ApprovalTaskCandidateEntity> roleCandidates = approvalTaskCandidateMapper.selectList(
                new LambdaQueryWrapper<ApprovalTaskCandidateEntity>()
                        .eq(ApprovalTaskCandidateEntity::getCandidateType, CandidateType.ROLE));
        roleCandidates.stream()
                .filter(candidate -> adminRoleLookupService.userHasRoleCode(userId, candidate.getCandidateValue()))
                .forEach(candidate -> candidateTaskIds.add(candidate.getTaskId()));

        if (!candidateTaskIds.isEmpty()) {
            List<ApprovalTaskEntity> unclaimedCandidateTasks = approvalTaskMapper.selectList(
                    new LambdaQueryWrapper<ApprovalTaskEntity>()
                            .in(ApprovalTaskEntity::getId, candidateTaskIds)
                            .isNull(ApprovalTaskEntity::getAssigneeId)
                            .eq(ApprovalTaskEntity::getStatus, TaskStatus.PENDING));
            unclaimedCandidateTasks.forEach(task -> taskIds.add(task.getId()));
        }

        if (taskIds.isEmpty()) {
            return List.of();
        }
        List<ApprovalTaskEntity> tasks = approvalTaskMapper.selectTodoPage(
                taskIds, query.businessType(), offset(query), query.effectivePageSize());
        return buildTaskVOList(tasks);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ApprovalTaskVO> findDoneTasks(Long userId, TaskQuery query) {
        List<ApprovalTaskEntity> tasks = approvalTaskMapper.selectDonePage(
                userId, DONE_ACTIONS, query.businessType(), offset(query), query.effectivePageSize());
        if (tasks.isEmpty()) {
            return List.of();
        }
        return buildTaskVOList(tasks);
    }

    /**
     * 按 {@link TaskQuery} 的页码/每页大小换算数据库分页偏移量。
     */
    private int offset(TaskQuery query) {
        return (query.effectivePage() - 1) * query.effectivePageSize();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ProcessInstanceDetailVO getProcessDetail(Long processInstanceId, Long viewerId) {
        ProcessInstanceEntity instance = processInstanceMapper.selectById(processInstanceId);
        if (instance == null) {
            throw new BusinessException("流程实例不存在");
        }
        List<ApprovalRecordEntity> records = approvalRecordMapper.selectList(new LambdaQueryWrapper<ApprovalRecordEntity>()
                .eq(ApprovalRecordEntity::getProcessInstanceId, processInstanceId)
                .orderByAsc(ApprovalRecordEntity::getCreateTime)
                .orderByAsc(ApprovalRecordEntity::getId));
        List<ApprovalTaskEntity> openTasks = findOpenTasks(processInstanceId);
        requireViewer(instance, viewerId, records, openTasks);

        Set<String> userIdTexts = new HashSet<>();
        if (instance.getApplicantId() != null) {
            userIdTexts.add(instance.getApplicantId().toString());
        }
        records.forEach(record -> {
            if (record.getOperatorId() != null) {
                userIdTexts.add(record.getOperatorId().toString());
            }
            if (record.getFromUserId() != null) {
                userIdTexts.add(record.getFromUserId().toString());
            }
        });
        Map<String, String> displayNames = userDisplayService.resolveDisplayNames(userIdTexts);

        List<ApprovalRecordVO> recordVOs = records.stream().map(record -> {
            ApprovalRecordVO vo = WorkflowConvert.INSTANCE.toRecordVO(record);
            if (record.getOperatorId() != null) {
                vo.setOperatorName(displayNames.get(record.getOperatorId().toString()));
            }
            if (record.getFromUserId() != null) {
                vo.setFromUserName(displayNames.get(record.getFromUserId().toString()));
            }
            return vo;
        }).toList();

        List<ProcessGraphNodeVO> nodes = resolveGraphNodes(instance, recordVOs, openTasks);
        List<ProcessGraphEdgeVO> edges = resolveGraphEdges(instance);

        return ProcessInstanceDetailVO.builder()
                .id(instance.getId())
                .flowableInstanceId(instance.getFlowableInstanceId())
                .businessType(instance.getBusinessType())
                .businessId(instance.getBusinessId())
                .title(instance.getTitle())
                .applicantId(instance.getApplicantId())
                .applicantName(instance.getApplicantId() == null
                        ? null
                        : displayNames.get(instance.getApplicantId().toString()))
                .status(instance.getStatus())
                .currentNodeId(instance.getCurrentNodeId())
                .currentNodeName(instance.getCurrentNodeName())
                .openNodes(toOpenNodeVOs(openTasks))
                .startedTime(instance.getStartedTime())
                .finishedTime(instance.getFinishedTime())
                .records(recordVOs)
                .nodes(nodes)
                .edges(edges)
                .build();
    }

    /**
     * 流程实例详情参与关系校验：仅申请人本人、审批轨迹中出现过的操作人/转办来源人、当前任一
     * 开放任务的指定处理人或候选人三者之一可查看，其余用户拒绝访问，落实
     * approval-runtime-safety 能力"操作授权和访问控制"需求"详情...数据 SHALL 按参与
     * 关系...返回"这句既有约束（add-approval-remark-and-process-flowchart change design.md
     * Decision 3）。
     *
     * @param instance  流程实例
     * @param viewerId  当前查看者用户 id
     * @param records   该实例的完整审批轨迹
     * @param openTasks 该实例当前全部开放任务
     */
    private void requireViewer(
            ProcessInstanceEntity instance,
            Long viewerId,
            List<ApprovalRecordEntity> records,
            List<ApprovalTaskEntity> openTasks) {
        if (Objects.equals(instance.getApplicantId(), viewerId)) {
            return;
        }
        boolean historyInvolved = records.stream()
                .anyMatch(record -> Objects.equals(record.getOperatorId(), viewerId)
                        || Objects.equals(record.getFromUserId(), viewerId));
        if (historyInvolved) {
            return;
        }
        boolean currentCandidate = openTasks.stream()
                .anyMatch(task -> taskAuthorizationService.isAuthorized(task, viewerId));
        if (currentCandidate) {
            return;
        }
        throw new BusinessException("无权限查看该流程实例详情");
    }

    /**
     * 查询流程实例当前全部开放任务：状态为 {@code PENDING}/{@code CLAIMED}，流程已结束时
     * 结果为空列表。
     */
    private List<ApprovalTaskEntity> findOpenTasks(Long processInstanceId) {
        return approvalTaskMapper.selectList(new LambdaQueryWrapper<ApprovalTaskEntity>()
                .eq(ApprovalTaskEntity::getProcessInstanceId, processInstanceId)
                .in(ApprovalTaskEntity::getStatus, TaskStatus.PENDING, TaskStatus.CLAIMED));
    }

    /**
     * 聚合流程实例当前全部开放节点：按 {@code (nodeId, nodeName)} 去重；并行分叉场景下同一
     * 时刻可能同时存在多个节点各自的开放任务。
     */
    private List<OpenNodeVO> toOpenNodeVOs(List<ApprovalTaskEntity> openTasks) {
        Map<String, OpenNodeVO> openNodeByKey = new LinkedHashMap<>();
        for (ApprovalTaskEntity task : openTasks) {
            String key = task.getNodeId() + "::" + task.getNodeName();
            openNodeByKey.putIfAbsent(key,
                    OpenNodeVO.builder().nodeId(task.getNodeId()).nodeName(task.getNodeName()).build());
        }
        return new ArrayList<>(openNodeByKey.values());
    }

    /**
     * 解析流程定义快照并补齐每个节点的三态状态与关联审批轨迹：审批轨迹中出现过的节点为
     * {@code COMPLETED}，当前存在开放任务的节点为 {@code CURRENT}（并补齐候选审批人/已认领
     * 处理人信息，见 {@link #resolveCurrentApprovers}），其余为 {@code PENDING}（design.md
     * Decision 4/7）。
     */
    private List<ProcessGraphNodeVO> resolveGraphNodes(
            ProcessInstanceEntity instance, List<ApprovalRecordVO> recordVOs, List<ApprovalTaskEntity> openTasks) {
        ProcessGraph graph = resolveGraph(instance);
        Map<String, List<ApprovalRecordVO>> recordsByNode = recordVOs.stream()
                .filter(record -> record.getNodeId() != null)
                .collect(Collectors.groupingBy(ApprovalRecordVO::getNodeId, LinkedHashMap::new, Collectors.toList()));
        Map<String, List<ApprovalTaskEntity>> openTasksByNode = openTasks.stream()
                .filter(task -> task.getNodeId() != null)
                .collect(Collectors.groupingBy(ApprovalTaskEntity::getNodeId, LinkedHashMap::new, Collectors.toList()));
        return graph.nodes().stream().map(node -> {
            List<ApprovalRecordVO> nodeRecords = recordsByNode.get(node.getId());
            if (nodeRecords != null) {
                node.setStatus(ProcessGraphNodeStatus.COMPLETED);
                node.setRecords(nodeRecords);
                node.setCurrentApprovers(List.of());
            } else if (openTasksByNode.containsKey(node.getId())) {
                node.setStatus(ProcessGraphNodeStatus.CURRENT);
                node.setCurrentApprovers(resolveCurrentApprovers(openTasksByNode.get(node.getId())));
            } else {
                node.setStatus(ProcessGraphNodeStatus.PENDING);
                node.setCurrentApprovers(List.of());
            }
            return node;
        }).toList();
    }

    /**
     * 解析某个 {@code CURRENT} 节点的候选审批人/已认领处理人：任务已被认领（{@code
     * assigneeId} 非空）生成一条 {@code assigned=true} 记录；未认领的任务查
     * {@code tab_wf_approval_task_candidate} 候选人明细，{@code USER} 类型解析用户展示名，
     * {@code ROLE} 类型查角色展示名，均生成 {@code assigned=false} 记录，不展开角色候选人
     * 背后的具体人员列表，也不透出候选人解析依据说明（design.md Decision 7）。
     *
     * @param nodeTasks 该节点当前全部开放任务（可能因会签/并行存在多条）
     * @return 候选审批人/处理人列表
     */
    private List<CurrentApproverVO> resolveCurrentApprovers(List<ApprovalTaskEntity> nodeTasks) {
        List<Long> unassignedTaskIds = nodeTasks.stream()
                .filter(task -> task.getAssigneeId() == null)
                .map(ApprovalTaskEntity::getId)
                .toList();
        List<ApprovalTaskCandidateEntity> candidates = unassignedTaskIds.isEmpty()
                ? List.of()
                : approvalTaskCandidateMapper.selectList(new LambdaQueryWrapper<ApprovalTaskCandidateEntity>()
                        .in(ApprovalTaskCandidateEntity::getTaskId, unassignedTaskIds));

        Set<String> userIdTexts = new HashSet<>();
        nodeTasks.stream()
                .map(ApprovalTaskEntity::getAssigneeId)
                .filter(Objects::nonNull)
                .forEach(assigneeId -> userIdTexts.add(assigneeId.toString()));
        candidates.stream()
                .filter(candidate -> CandidateType.USER.equals(candidate.getCandidateType()))
                .map(ApprovalTaskCandidateEntity::getCandidateValue)
                .forEach(userIdTexts::add);
        Map<String, String> userDisplayNames = userDisplayService.resolveDisplayNames(userIdTexts);

        Set<String> roleCodes = candidates.stream()
                .filter(candidate -> CandidateType.ROLE.equals(candidate.getCandidateType()))
                .map(ApprovalTaskCandidateEntity::getCandidateValue)
                .collect(Collectors.toSet());
        Map<String, String> roleNameByCode = roleCodes.isEmpty()
                ? Map.of()
                : roleMapper.selectList(new LambdaQueryWrapper<RoleEntity>().in(RoleEntity::getCode, roleCodes))
                        .stream()
                        .collect(Collectors.toMap(RoleEntity::getCode, RoleEntity::getName, (first, second) -> first));

        List<CurrentApproverVO> approvers = new ArrayList<>();
        nodeTasks.stream()
                .filter(task -> task.getAssigneeId() != null)
                .forEach(task -> approvers.add(CurrentApproverVO.builder()
                        .userId(task.getAssigneeId())
                        .userName(userDisplayNames.get(task.getAssigneeId().toString()))
                        .assigned(true)
                        .build()));
        for (ApprovalTaskCandidateEntity candidate : candidates) {
            if (CandidateType.USER.equals(candidate.getCandidateType())) {
                approvers.add(CurrentApproverVO.builder()
                        .userId(parseUserId(candidate.getCandidateValue()))
                        .userName(userDisplayNames.get(candidate.getCandidateValue()))
                        .assigned(false)
                        .build());
            } else if (CandidateType.ROLE.equals(candidate.getCandidateType())) {
                approvers.add(CurrentApproverVO.builder()
                        .roleCode(candidate.getCandidateValue())
                        .roleName(roleNameByCode.get(candidate.getCandidateValue()))
                        .assigned(false)
                        .build());
            }
        }
        return approvers;
    }

    /**
     * 把候选人明细里 {@code USER} 类型的 {@code candidateValue} 文本解析为用户 id；解析失败
     * （历史脏数据）时返回 {@code null}，调用方按"未知用户"展示，不影响整体列表组装。
     */
    private Long parseUserId(String candidateValue) {
        try {
            return Long.valueOf(candidateValue);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /**
     * 解析流程定义快照得到的完整连线列表，结构性数据，不涉及状态计算。
     */
    private List<ProcessGraphEdgeVO> resolveGraphEdges(ProcessInstanceEntity instance) {
        return resolveGraph(instance).edges();
    }

    /**
     * 按流程实例绑定的流程定义 id 查询发布快照并解析为只读节点/连线图；流程定义缺失（理论上
     * 不应发生，防御性兜底）时返回空图，不影响详情接口整体可用。
     */
    private ProcessGraph resolveGraph(ProcessInstanceEntity instance) {
        if (instance.getProcessDefinitionId() == null) {
            return ProcessGraph.empty();
        }
        ProcessDefinitionEntity definition = processDefinitionMapper.selectById(instance.getProcessDefinitionId());
        if (definition == null) {
            return ProcessGraph.empty();
        }
        return processGraphAssembler.assemble(
                definition.getSchemaVersion(), definition.getModelJsonSnapshot(), instance.getBusinessType());
    }

    /**
     * 组装任务视图列表：批量补齐流程实例信息与展示名。排序/过滤/分页已在
     * {@link ApprovalTaskMapper#selectTodoPage}/{@link ApprovalTaskMapper#selectDonePage} 的
     * SQL 层完成，这里只做 VO 组装，不再重复过滤或排序，保持数据库返回的顺序。
     */
    private List<ApprovalTaskVO> buildTaskVOList(List<ApprovalTaskEntity> tasks) {
        Set<Long> processInstanceIds = tasks.stream()
                .map(ApprovalTaskEntity::getProcessInstanceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, ProcessInstanceEntity> instanceById = processInstanceIds.isEmpty()
                ? Map.of()
                : processInstanceMapper.selectByIds(processInstanceIds).stream()
                        .collect(Collectors.toMap(ProcessInstanceEntity::getId, instance -> instance, (a, b) -> a));

        Map<String, String> displayNames = resolveDisplayNames(instanceById.values());
        for (ApprovalTaskEntity task : tasks) {
            if (task.getAssigneeId() != null) {
                displayNames.putIfAbsent(task.getAssigneeId().toString(),
                        userDisplayService.resolveDisplayNames(Set.of(task.getAssigneeId().toString()))
                                .get(task.getAssigneeId().toString()));
            }
        }

        return tasks.stream()
                .map(task -> toVO(task, instanceById.get(task.getProcessInstanceId()), displayNames))
                .collect(Collectors.toList());
    }

    /**
     * 转换为视图对象，补填流程实例相关的展示字段。
     */
    private ApprovalTaskVO toVO(
            ApprovalTaskEntity task,
            ProcessInstanceEntity instance,
            Map<String, String> displayNames) {
        ApprovalTaskVO vo = WorkflowConvert.INSTANCE.toTaskVO(task);
        if (instance != null) {
            vo.setBusinessType(instance.getBusinessType());
            vo.setBusinessId(instance.getBusinessId());
            vo.setTitle(instance.getTitle());
            vo.setApplicantId(instance.getApplicantId());
            if (instance.getApplicantId() != null) {
                vo.setApplicantName(displayNames.get(instance.getApplicantId().toString()));
            }
        }
        if (task.getAssigneeId() != null) {
            vo.setAssigneeName(displayNames.get(task.getAssigneeId().toString()));
        }
        return vo;
    }

    /**
     * 批量解析涉及流程实例发起人的展示名。
     */
    private Map<String, String> resolveDisplayNames(Iterable<ProcessInstanceEntity> instances) {
        Set<String> userIdTexts = new HashSet<>();
        instances.forEach(instance -> {
            if (instance.getApplicantId() != null) {
                userIdTexts.add(instance.getApplicantId().toString());
            }
        });
        if (userIdTexts.isEmpty()) {
            return new HashMap<>();
        }
        return new HashMap<>(userDisplayService.resolveDisplayNames(userIdTexts));
    }
}
