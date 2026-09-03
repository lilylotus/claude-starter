package cn.nihility.rbac.workflow.service.impl;

import cn.nihility.rbac.workflow.assignee.support.AdminRoleLookupService;
import cn.nihility.rbac.workflow.constant.ApprovalAction;
import cn.nihility.rbac.workflow.constant.CandidateType;
import cn.nihility.rbac.workflow.constant.TaskStatus;
import cn.nihility.rbac.workflow.dto.ApprovalRecordVO;
import cn.nihility.rbac.workflow.dto.ApprovalTaskVO;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
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
        List<ApprovalTaskEntity> tasks = approvalTaskMapper.selectByIds(taskIds);
        return buildTaskVOList(tasks, query, true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ApprovalTaskVO> findDoneTasks(Long userId, TaskQuery query) {
        List<ApprovalRecordEntity> records = approvalRecordMapper.selectList(new LambdaQueryWrapper<ApprovalRecordEntity>()
                .eq(ApprovalRecordEntity::getOperatorId, userId)
                .isNotNull(ApprovalRecordEntity::getTaskId)
                .in(ApprovalRecordEntity::getAction, ApprovalAction.APPROVE, ApprovalAction.REJECT,
                        ApprovalAction.RETURN, ApprovalAction.TRANSFER, ApprovalAction.DELEGATE,
                        ApprovalAction.ADD_SIGN)
                .orderByDesc(ApprovalRecordEntity::getCreateTime)
                .orderByDesc(ApprovalRecordEntity::getId));
        if (records.isEmpty()) {
            return List.of();
        }
        Set<Long> taskIds = records.stream().map(ApprovalRecordEntity::getTaskId).collect(Collectors.toSet());
        Map<Long, ApprovalTaskEntity> taskById = approvalTaskMapper.selectByIds(taskIds).stream()
                .collect(Collectors.toMap(ApprovalTaskEntity::getId, task -> task, (a, b) -> a));

        Set<Long> processInstanceIds = taskById.values().stream()
                .map(ApprovalTaskEntity::getProcessInstanceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, ProcessInstanceEntity> instanceById = processInstanceIds.isEmpty()
                ? Map.of()
                : processInstanceMapper.selectByIds(processInstanceIds).stream()
                        .collect(Collectors.toMap(ProcessInstanceEntity::getId, instance -> instance, (a, b) -> a));

        Map<String, String> displayNames = resolveDisplayNames(instanceById.values());

        List<ApprovalTaskVO> result = records.stream()
                .map(ApprovalRecordEntity::getTaskId)
                .distinct()
                .map(taskById::get)
                .filter(Objects::nonNull)
                .filter(task -> query.businessType() == null
                        || matchesBusinessType(task, instanceById, query.businessType()))
                .map(task -> toVO(task, instanceById.get(task.getProcessInstanceId()), displayNames))
                .collect(Collectors.toList());
        return paginate(result, query);
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
                .startedTime(instance.getStartedTime())
                .finishedTime(instance.getFinishedTime())
                .records(recordVOs)
                .build();
    }

    /**
     * 组装待办任务列表：批量补齐流程实例信息与展示名后过滤、排序、分页。
     */
    private List<ApprovalTaskVO> buildTaskVOList(List<ApprovalTaskEntity> tasks, TaskQuery query, boolean sortDesc) {
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

        List<ApprovalTaskVO> result = tasks.stream()
                .filter(task -> query.businessType() == null
                        || matchesBusinessType(task, instanceById, query.businessType()))
                .map(task -> toVO(task, instanceById.get(task.getProcessInstanceId()), displayNames))
                .collect(Collectors.toList());
        result.sort(sortDesc
                ? Comparator.comparing(ApprovalTaskVO::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder()))
                : Comparator.comparing(ApprovalTaskVO::getCreateTime, Comparator.nullsLast(Comparator.naturalOrder())));
        return paginate(result, query);
    }

    /**
     * 按业务对象类型过滤。
     */
    private boolean matchesBusinessType(
            ApprovalTaskEntity task,
            Map<Long, ProcessInstanceEntity> instanceById,
            String businessType) {
        ProcessInstanceEntity instance = instanceById.get(task.getProcessInstanceId());
        return instance != null && Objects.equals(instance.getBusinessType(), businessType);
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

    /**
     * 按 {@link TaskQuery} 的页码/每页大小对结果列表做内存分页。
     */
    private List<ApprovalTaskVO> paginate(List<ApprovalTaskVO> list, TaskQuery query) {
        int page = query.effectivePage();
        int pageSize = query.effectivePageSize();
        int fromIndex = Math.min((page - 1) * pageSize, list.size());
        int toIndex = Math.min(fromIndex + pageSize, list.size());
        return list.subList(fromIndex, toIndex);
    }
}
