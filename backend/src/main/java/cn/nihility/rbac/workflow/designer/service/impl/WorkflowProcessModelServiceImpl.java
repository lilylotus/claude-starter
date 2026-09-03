package cn.nihility.rbac.workflow.designer.service.impl;

import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.common.util.JacksonUtils;
import cn.nihility.rbac.workflow.constant.ProcessModelStatus;
import cn.nihility.rbac.workflow.designer.compiler.CompiledProcess;
import cn.nihility.rbac.workflow.designer.compiler.NodeAssigneeRuleDraft;
import cn.nihility.rbac.workflow.designer.compiler.WorkflowModelCompiler;
import cn.nihility.rbac.workflow.designer.dto.ProcessDefinitionVersionVO;
import cn.nihility.rbac.workflow.designer.dto.ProcessModelVO;
import cn.nihility.rbac.workflow.designer.dto.ProcessModelDsl;
import cn.nihility.rbac.workflow.designer.dto.PublishResultVO;
import cn.nihility.rbac.workflow.designer.service.WorkflowProcessModelService;
import cn.nihility.rbac.workflow.entity.NodeAssigneeRuleEntity;
import cn.nihility.rbac.workflow.entity.ProcessDefinitionEntity;
import cn.nihility.rbac.workflow.entity.ProcessModelEntity;
import cn.nihility.rbac.workflow.mapper.NodeAssigneeRuleMapper;
import cn.nihility.rbac.workflow.mapper.ProcessDefinitionMapper;
import cn.nihility.rbac.workflow.mapper.ProcessModelMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * {@link WorkflowProcessModelService} 默认实现（workflow-approval-engine change design.md
 * Decision 11）。发布动作是同步的"编译 + 部署 + 落库"，不引入 Outbox/MQ 异步任务队列。
 */
@Service
@RequiredArgsConstructor
public class WorkflowProcessModelServiceImpl implements WorkflowProcessModelService {

    /** 流程模型数据访问接口。 */
    private final ProcessModelMapper processModelMapper;

    /** 流程定义（不可变发布版本快照）数据访问接口。 */
    private final ProcessDefinitionMapper processDefinitionMapper;

    /** 节点审批人规则数据访问接口。 */
    private final NodeAssigneeRuleMapper nodeAssigneeRuleMapper;

    /** DSL → BPMN 编译器。 */
    private final WorkflowModelCompiler workflowModelCompiler;

    /** Flowable 流程仓库服务。 */
    private final RepositoryService repositoryService;

    /** {@inheritDoc} */
    @Override
    public List<ProcessModelVO> listModels() {
        return processModelMapper.selectList(new LambdaQueryWrapper<ProcessModelEntity>()
                        .orderByDesc(ProcessModelEntity::getUpdateTime)
                        .orderByDesc(ProcessModelEntity::getId))
                .stream().map(this::toModelVO).toList();
    }

    /** {@inheritDoc} */
    @Override
    public ProcessModelVO getModel(Long modelId) {
        return toModelVO(requireModel(modelId));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public ProcessModelVO createModel(String processCode, String processName, Long operatorId) {
        requireAvailableCode(processCode);
        LocalDateTime now = LocalDateTime.now();
        String operatorText = operatorId == null ? null : operatorId.toString();
        ProcessModelEntity model = ProcessModelEntity.builder()
                .processCode(processCode)
                .processName(processName)
                .modelJson(null)
                .status(ProcessModelStatus.DRAFT)
                .createBy(operatorText).createTime(now).updateBy(operatorText).updateTime(now)
                .build();
        processModelMapper.insert(model);
        return toModelVO(model);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public ProcessModelVO copyModel(Long sourceModelId, String processCode, String processName, Long operatorId) {
        ProcessModelEntity source = requireModel(sourceModelId);
        requireAvailableCode(processCode);
        LocalDateTime now = LocalDateTime.now();
        String operatorText = operatorId == null ? null : operatorId.toString();
        ProcessModelEntity copy = ProcessModelEntity.builder()
                .processCode(processCode).processName(processName).modelJson(source.getModelJson())
                .status(ProcessModelStatus.DRAFT).createBy(operatorText).createTime(now)
                .updateBy(operatorText).updateTime(now).build();
        processModelMapper.insert(copy);
        return toModelVO(copy);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void saveDraft(Long modelId, String modelJson) {
        ProcessModelEntity model = requireModel(modelId);
        model.setModelJson(modelJson);
        model.setUpdateTime(LocalDateTime.now());
        processModelMapper.updateById(model);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public PublishResultVO publish(Long modelId, Long operatorId) {
        ProcessModelEntity model = requireModel(modelId);
        if (!StringUtils.hasText(model.getModelJson())) {
            throw new BusinessException("流程模型草稿为空，无法发布");
        }
        ProcessModelDsl dsl = JacksonUtils.toObj(model.getModelJson(), ProcessModelDsl.class);
        CompiledProcess compiled = workflowModelCompiler.compile(dsl);

        int nextVersion = nextVersion(modelId);
        String operatorText = operatorId == null ? null : operatorId.toString();
        LocalDateTime now = LocalDateTime.now();

        String resourceName = model.getProcessCode() + "-v" + nextVersion + ".bpmn20.xml";
        Deployment deployment = repositoryService.createDeployment()
                .name(model.getProcessCode() + " v" + nextVersion)
                .addBpmnModel(resourceName, compiled.bpmnModel())
                .deploy();
        ProcessDefinition flowableDefinition = repositoryService.createProcessDefinitionQuery()
                .deploymentId(deployment.getId())
                .singleResult();
        if (flowableDefinition == null) {
            throw new BusinessException("流程发布失败：未能获取部署产生的流程定义");
        }

        ProcessDefinitionEntity definition = ProcessDefinitionEntity.builder()
                .processModelId(modelId)
                .processCode(model.getProcessCode())
                .version(nextVersion)
                .flowableDefinitionKey(flowableDefinition.getKey())
                .flowableDefinitionId(flowableDefinition.getId())
                .modelJsonSnapshot(model.getModelJson())
                .status(ProcessModelStatus.PUBLISHED)
                .publishedBy(operatorText)
                .publishedTime(now)
                .createBy(operatorText)
                .createTime(now)
                .updateBy(operatorText)
                .updateTime(now)
                .build();
        processDefinitionMapper.insert(definition);

        insertAssigneeRules(definition.getId(), compiled.assigneeRules(), operatorText, now);

        model.setCurrentDefinitionId(definition.getId());
        model.setStatus(ProcessModelStatus.PUBLISHED);
        model.setUpdateBy(operatorText);
        model.setUpdateTime(now);
        processModelMapper.updateById(model);

        return PublishResultVO.builder()
                .processDefinitionId(definition.getId())
                .version(nextVersion)
                .flowableDefinitionKey(flowableDefinition.getKey())
                .flowableDefinitionId(flowableDefinition.getId())
                .publishedTime(now)
                .build();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void disable(Long modelId) {
        ProcessModelEntity model = requireModel(modelId);
        ProcessDefinitionEntity definition = requireCurrentDefinition(model);
        if (ProcessModelStatus.DISABLED.equals(definition.getStatus())) {
            throw new BusinessException("该流程当前版本已处于下线状态");
        }
        if (StringUtils.hasText(definition.getFlowableDefinitionId())) {
            repositoryService.suspendProcessDefinitionById(definition.getFlowableDefinitionId());
        }

        LocalDateTime now = LocalDateTime.now();
        definition.setStatus(ProcessModelStatus.DISABLED);
        definition.setUpdateTime(now);
        processDefinitionMapper.updateById(definition);

        model.setStatus(ProcessModelStatus.DISABLED);
        model.setUpdateTime(now);
        processModelMapper.updateById(model);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void enable(Long modelId) {
        ProcessModelEntity model = requireModel(modelId);
        ProcessDefinitionEntity definition = requireCurrentDefinition(model);
        if (!ProcessModelStatus.DISABLED.equals(definition.getStatus())) {
            throw new BusinessException("该流程当前版本未处于下线状态，无需重新启用");
        }
        if (StringUtils.hasText(definition.getFlowableDefinitionId())) {
            repositoryService.activateProcessDefinitionById(definition.getFlowableDefinitionId());
        }

        LocalDateTime now = LocalDateTime.now();
        definition.setStatus(ProcessModelStatus.PUBLISHED);
        definition.setUpdateTime(now);
        processDefinitionMapper.updateById(definition);

        model.setStatus(ProcessModelStatus.PUBLISHED);
        model.setUpdateTime(now);
        processModelMapper.updateById(model);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ProcessDefinitionVersionVO> listVersions(Long modelId) {
        requireModel(modelId);
        List<ProcessDefinitionEntity> definitions = processDefinitionMapper.selectList(
                new LambdaQueryWrapper<ProcessDefinitionEntity>()
                        .eq(ProcessDefinitionEntity::getProcessModelId, modelId)
                        .orderByDesc(ProcessDefinitionEntity::getVersion));
        return definitions.stream()
                .map(entity -> ProcessDefinitionVersionVO.builder()
                        .id(entity.getId())
                        .version(entity.getVersion())
                        .status(entity.getStatus())
                        .publishedBy(entity.getPublishedBy())
                        .publishedTime(entity.getPublishedTime())
                        .modelJsonSnapshot(entity.getModelJsonSnapshot())
                        .build())
                .toList();
    }

    /**
     * 批量落库编译产物派生的节点审批人规则。
     */
    private void insertAssigneeRules(
            Long processDefinitionId,
            List<NodeAssigneeRuleDraft> drafts,
            String operatorText,
            LocalDateTime now) {
        for (NodeAssigneeRuleDraft draft : drafts) {
            nodeAssigneeRuleMapper.insert(NodeAssigneeRuleEntity.builder()
                    .processDefinitionId(processDefinitionId)
                    .nodeId(draft.nodeId())
                    .nodeName(draft.nodeName())
                    .nodeOrder(draft.nodeOrder())
                    .assigneeType(draft.assigneeType() == null ? null : draft.assigneeType().name())
                    .assigneeValue(draft.assigneeValue())
                    .approvalMode(draft.approvalMode() == null ? null : draft.approvalMode().name())
                    .approvalPercent(draft.approvalPercent())
                    .emptyAssigneeStrategy(
                            draft.emptyAssigneeStrategy() == null ? null : draft.emptyAssigneeStrategy().name())
                    .allowSelfApproval(draft.allowSelfApproval())
                    .allowTransfer(draft.allowTransfer())
                    .allowDelegate(draft.allowDelegate())
                    .allowAddSign(draft.allowAddSign())
                    .allowReturn(draft.allowReturn())
                    .createBy(operatorText)
                    .createTime(now)
                    .updateBy(operatorText)
                    .updateTime(now)
                    .build());
        }
    }

    /**
     * 计算该流程模型下一个版本号：当前最大版本号 + 1，不存在任何历史版本则为 1。
     */
    private int nextVersion(Long modelId) {
        List<ProcessDefinitionEntity> definitions = processDefinitionMapper.selectList(
                new LambdaQueryWrapper<ProcessDefinitionEntity>()
                        .eq(ProcessDefinitionEntity::getProcessModelId, modelId));
        return definitions.stream()
                .map(ProcessDefinitionEntity::getVersion)
                .max(Integer::compareTo)
                .orElse(0) + 1;
    }

    /**
     * 查询流程模型，不存在时抛出业务异常。
     */
    private ProcessModelEntity requireModel(Long modelId) {
        ProcessModelEntity model = processModelMapper.selectById(modelId);
        if (model == null) {
            throw new BusinessException("流程模型不存在");
        }
        return model;
    }

    /**
     * 查询流程模型当前生效版本，不存在时抛出业务异常。
     */
    private ProcessDefinitionEntity requireCurrentDefinition(ProcessModelEntity model) {
        if (model.getCurrentDefinitionId() == null) {
            throw new BusinessException("流程模型尚未发布任何版本");
        }
        ProcessDefinitionEntity definition = processDefinitionMapper.selectById(model.getCurrentDefinitionId());
        if (definition == null) {
            throw new BusinessException("当前生效版本记录不存在");
        }
        return definition;
    }

    /** 将持久化实体转换为对外返回对象。 */
    private ProcessModelVO toModelVO(ProcessModelEntity model) {
        return ProcessModelVO.builder()
                .id(model.getId()).processCode(model.getProcessCode()).processName(model.getProcessName())
                .modelJson(model.getModelJson()).status(model.getStatus())
                .currentDefinitionId(model.getCurrentDefinitionId()).createBy(model.getCreateBy())
                .createTime(model.getCreateTime()).updateBy(model.getUpdateBy()).updateTime(model.getUpdateTime())
                .build();
    }

    /** 校验流程编码未被其他模型占用。 */
    private void requireAvailableCode(String processCode) {
        if (processModelMapper.selectCount(new LambdaQueryWrapper<ProcessModelEntity>()
                .eq(ProcessModelEntity::getProcessCode, processCode)) > 0) {
            throw new BusinessException("流程编码已存在");
        }
    }
}
