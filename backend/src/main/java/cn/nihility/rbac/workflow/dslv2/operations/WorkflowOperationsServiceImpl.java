package cn.nihility.rbac.workflow.dslv2.operations;

import cn.nihility.rbac.workflow.constant.ProcessInstanceStatus;
import cn.nihility.rbac.workflow.dto.ProcessInstanceExceptionVO;
import cn.nihility.rbac.workflow.entity.ProcessInstanceEntity;
import cn.nihility.rbac.workflow.mapper.ProcessInstanceMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * {@link WorkflowOperationsService} 实现，单表查询直接复用 {@link ProcessInstanceMapper}。
 */
@Service
@RequiredArgsConstructor
public class WorkflowOperationsServiceImpl implements WorkflowOperationsService {

    /** 运维阻塞原因码：空审批人待分配。 */
    private static final String EXCEPTION_CODE_ASSIGNEE_EMPTY = "ASSIGNEE_EMPTY";

    /** 流程实例数据访问接口。 */
    private final ProcessInstanceMapper processInstanceMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ProcessInstanceExceptionVO> listAssigneeEmptyInstances() {
        List<ProcessInstanceEntity> instances = processInstanceMapper.selectList(
                new LambdaQueryWrapper<ProcessInstanceEntity>()
                        .eq(ProcessInstanceEntity::getExceptionCode, EXCEPTION_CODE_ASSIGNEE_EMPTY)
                        .eq(ProcessInstanceEntity::getStatus, ProcessInstanceStatus.RUNNING)
                        .orderByAsc(ProcessInstanceEntity::getStartedTime)
                        .orderByAsc(ProcessInstanceEntity::getId));
        return instances.stream()
                .map(instance -> ProcessInstanceExceptionVO.builder()
                        .id(instance.getId())
                        .businessType(instance.getBusinessType())
                        .businessId(instance.getBusinessId())
                        .applicantId(instance.getApplicantId())
                        .currentNodeId(instance.getCurrentNodeId())
                        .currentNodeName(instance.getCurrentNodeName())
                        .exceptionCode(instance.getExceptionCode())
                        .startedTime(instance.getStartedTime())
                        .build())
                .toList();
    }
}
