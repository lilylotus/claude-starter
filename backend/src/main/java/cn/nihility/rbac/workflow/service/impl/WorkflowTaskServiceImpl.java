package cn.nihility.rbac.workflow.service.impl;

import cn.nihility.rbac.workflow.assignee.support.AdminRoleLookupService;
import cn.nihility.rbac.workflow.constant.ApprovalAction;
import cn.nihility.rbac.workflow.constant.CandidateType;
import cn.nihility.rbac.workflow.constant.TaskStatus;
import cn.nihility.rbac.workflow.dto.ApprovalRecordVO;
import cn.nihility.rbac.workflow.dto.ApprovalTaskVO;
import cn.nihility.rbac.workflow.dto.OpenNodeVO;
import cn.nihility.rbac.workflow.dto.ProcessInstanceDetailVO;
import cn.nihility.rbac.workflow.dto.TaskQuery;
import cn.nihility.rbac.workflow.entity.ApprovalRecordEntity;
import cn.nihility.rbac.workflow.entity.ApprovalTaskCandidateEntity;
import cn.nihility.rbac.workflow.entity.ApprovalTaskEntity;
import cn.nihility.rbac.workflow.entity.ProcessInstanceEntity;
import cn.nihility.rbac.workflow.mapper.ApprovalRecordMapper;
import cn.nihility.rbac.workflow.mapper.ApprovalTaskCandidateMapper;
import cn.nihility.rbac.workflow.mapper.ApprovalTaskMapper;
import cn.nihility.rbac.workflow.mapper.ProcessInstanceMapper;
import cn.nihility.rbac.workflow.mapstruct.WorkflowConvert;
import cn.nihility.rbac.workflow.service.WorkflowTaskService;
import cn.nihility.rbac.common.exception.BusinessException;
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

    /** 管理员角色查询辅助组件，用于候选角色维度匹配。 */
    private final AdminRoleLookupService adminRoleLookupService;

    /** 用户展示名解析服务。 */
    private final UserDisplayService userDisplayService;

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
    public ProcessInstanceDetailVO getProcessDetail(Long processInstanceId) {
        ProcessInstanceEntity instance = processInstanceMapper.selectById(processInstanceId);
        if (instance == null) {
            throw new BusinessException("流程实例不存在");
        }
        List<ApprovalRecordEntity> records = approvalRecordMapper.selectList(new LambdaQueryWrapper<ApprovalRecordEntity>()
                .eq(ApprovalRecordEntity::getProcessInstanceId, processInstanceId)
                .orderByAsc(ApprovalRecordEntity::getCreateTime)
                .orderByAsc(ApprovalRecordEntity::getId));

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
                .openNodes(resolveOpenNodes(processInstanceId))
                .startedTime(instance.getStartedTime())
                .finishedTime(instance.getFinishedTime())
                .records(recordVOs)
                .build();
    }

    /**
     * 聚合流程实例当前全部开放节点：查询状态为 {@code PENDING}/{@code CLAIMED} 的审批任务，
     * 按 {@code (nodeId, nodeName)} 去重；并行分叉场景下同一时刻可能同时存在多个节点各自的
     * 开放任务，流程已结束时结果为空列表。
     */
    private List<OpenNodeVO> resolveOpenNodes(Long processInstanceId) {
        List<ApprovalTaskEntity> openTasks = approvalTaskMapper.selectList(new LambdaQueryWrapper<ApprovalTaskEntity>()
                .eq(ApprovalTaskEntity::getProcessInstanceId, processInstanceId)
                .in(ApprovalTaskEntity::getStatus, TaskStatus.PENDING, TaskStatus.CLAIMED));
        Map<String, OpenNodeVO> openNodeByKey = new LinkedHashMap<>();
        for (ApprovalTaskEntity task : openTasks) {
            String key = task.getNodeId() + "::" + task.getNodeName();
            openNodeByKey.putIfAbsent(key,
                    OpenNodeVO.builder().nodeId(task.getNodeId()).nodeName(task.getNodeName()).build());
        }
        return new ArrayList<>(openNodeByKey.values());
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
