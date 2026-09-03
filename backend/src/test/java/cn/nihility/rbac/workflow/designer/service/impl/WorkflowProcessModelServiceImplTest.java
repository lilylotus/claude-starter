package cn.nihility.rbac.workflow.designer.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.workflow.constant.ProcessModelStatus;
import cn.nihility.rbac.workflow.designer.compiler.CompiledProcess;
import cn.nihility.rbac.workflow.designer.compiler.NodeAssigneeRuleDraft;
import cn.nihility.rbac.workflow.designer.compiler.WorkflowModelCompiler;
import cn.nihility.rbac.workflow.designer.dto.ProcessDefinitionVersionVO;
import cn.nihility.rbac.workflow.designer.dto.PublishResultVO;
import cn.nihility.rbac.workflow.entity.NodeAssigneeRuleEntity;
import cn.nihility.rbac.workflow.entity.ProcessDefinitionEntity;
import cn.nihility.rbac.workflow.entity.ProcessModelEntity;
import cn.nihility.rbac.workflow.exception.WorkflowModelValidationException;
import cn.nihility.rbac.workflow.mapper.NodeAssigneeRuleMapper;
import cn.nihility.rbac.workflow.mapper.ProcessDefinitionMapper;
import cn.nihility.rbac.workflow.mapper.ProcessModelMapper;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.DeploymentBuilder;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link WorkflowProcessModelServiceImpl} 单元测试（workflow-approval-engine change
 * tasks.md 10.5）：覆盖草稿保存不触发部署、发布生成新版本、编译失败不部署、下线/启用状态流转
 * 等关键场景。
 */
@ExtendWith(MockitoExtension.class)
class WorkflowProcessModelServiceImplTest {

    @Mock
    private ProcessModelMapper processModelMapper;

    @Mock
    private ProcessDefinitionMapper processDefinitionMapper;

    @Mock
    private NodeAssigneeRuleMapper nodeAssigneeRuleMapper;

    @Mock
    private WorkflowModelCompiler workflowModelCompiler;

    @Mock
    private cn.nihility.rbac.workflow.dslv2.compiler.WorkflowModelCompilerV2 workflowModelCompilerV2;

    @Mock
    private RepositoryService repositoryService;

    @Mock
    private DeploymentBuilder deploymentBuilder;

    @Mock
    private Deployment deployment;

    @Mock
    private ProcessDefinitionQuery processDefinitionQuery;

    @Mock
    private ProcessDefinition flowableProcessDefinition;

    private WorkflowProcessModelServiceImpl service;

    /** 模拟自增主键分配。 */
    private final AtomicLong idSequence = new AtomicLong(100L);

    @BeforeEach
    void setUp() {
        service = new WorkflowProcessModelServiceImpl(
                processModelMapper, processDefinitionMapper, nodeAssigneeRuleMapper, workflowModelCompiler,
                workflowModelCompilerV2, repositoryService);
    }

    /** 保存草稿只更新 {@code model_json}，不触碰 Flowable。 */
    @Test
    void saveDraft_shouldOnlyUpdateModelJsonAndNotTouchFlowable() {
        ProcessModelEntity model = draftModel();
        when(processModelMapper.selectById(1L)).thenReturn(model);

        service.saveDraft(1L, "{\"processCode\":\"DEMO\"}", null);

        assertThat(model.getModelJson()).isEqualTo("{\"processCode\":\"DEMO\"}");
        assertThat(model.getStatus()).isEqualTo(ProcessModelStatus.PUBLISHED);
        verify(processModelMapper).updateById(model);
        verify(repositoryService, never()).createDeployment();
    }

    /** 创建模型使用草稿状态，不触发 Flowable 部署。 */
    @Test
    void createModel_shouldPersistDraftWithoutDeploying() {
        when(processModelMapper.selectCount(any())).thenReturn(0L);
        doAnswer(invocation -> {
            ProcessModelEntity entity = invocation.getArgument(0);
            entity.setId(201L);
            return 1;
        }).when(processModelMapper).insert(any(ProcessModelEntity.class));

        var result = service.createModel("USER_CHANGE", "人员变更审批", 9L);

        assertThat(result.getId()).isEqualTo(201L);
        assertThat(result.getStatus()).isEqualTo(ProcessModelStatus.DRAFT);
        assertThat(result.getProcessCode()).isEqualTo("USER_CHANGE");
        verify(repositoryService, never()).createDeployment();
    }

    /** 流程模型列表按更新时间和主键倒序返回，不暴露持久化实体。 */
    @Test
    void listModels_shouldReturnPresentationObjects() {
        when(processModelMapper.selectList(any())).thenReturn(List.of(draftModel()));

        var models = service.listModels();

        assertThat(models).singleElement().satisfies(model -> {
            assertThat(model.getId()).isEqualTo(1L);
            assertThat(model.getModelJson()).isEqualTo("{\"processCode\":\"MASTER_DATA_APPROVAL\"}");
        });
    }

    /** 复制模型只复制草稿内容，不能复用原模型的已发布定义。 */
    @Test
    void copyModel_shouldCreateIndependentDraftWithoutDefinition() {
        ProcessModelEntity source = draftModel();
        source.setCurrentDefinitionId(10L);
        when(processModelMapper.selectById(1L)).thenReturn(source);
        when(processModelMapper.selectCount(any())).thenReturn(0L);
        doAnswer(invocation -> {
            ProcessModelEntity entity = invocation.getArgument(0);
            entity.setId(202L);
            return 1;
        }).when(processModelMapper).insert(any(ProcessModelEntity.class));

        var copy = service.copyModel(1L, "USER_CHANGE_COPY", "人员变更审批副本", 9L);

        assertThat(copy.getId()).isEqualTo(202L);
        assertThat(copy.getStatus()).isEqualTo(ProcessModelStatus.DRAFT);
        assertThat(copy.getCurrentDefinitionId()).isNull();
        assertThat(copy.getModelJson()).isEqualTo(source.getModelJson());
        verify(repositoryService, never()).createDeployment();
    }

    /** 发布成功：编译 -> 部署 -> 落库新版本行 + 规则行 -> 更新流程模型当前版本。 */
    @Test
    void publish_shouldCreateNewVersionAndUpdateCurrentDefinition() {
        ProcessModelEntity model = draftModel();
        when(processModelMapper.selectById(1L)).thenReturn(model);
        when(processDefinitionMapper.selectList(any())).thenReturn(List.of(existingDefinition(1)));

        NodeAssigneeRuleDraft ruleDraft = new NodeAssigneeRuleDraft(
                "deptLeaderApprove", "部门负责人审批", 1, null, null, null, null, null, null,
                false, true, true, false, false);
        when(workflowModelCompiler.compile(any())).thenReturn(new CompiledProcess(new BpmnModel(), List.of(ruleDraft)));

        when(repositoryService.createDeployment()).thenReturn(deploymentBuilder);
        when(deploymentBuilder.name(anyString())).thenReturn(deploymentBuilder);
        when(deploymentBuilder.addBpmnModel(anyString(), any())).thenReturn(deploymentBuilder);
        when(deploymentBuilder.deploy()).thenReturn(deployment);
        when(deployment.getId()).thenReturn("deployment-1");
        when(repositoryService.createProcessDefinitionQuery()).thenReturn(processDefinitionQuery);
        when(processDefinitionQuery.deploymentId("deployment-1")).thenReturn(processDefinitionQuery);
        when(processDefinitionQuery.singleResult()).thenReturn(flowableProcessDefinition);
        when(flowableProcessDefinition.getKey()).thenReturn("MASTER_DATA_APPROVAL");
        when(flowableProcessDefinition.getId()).thenReturn("MASTER_DATA_APPROVAL:2:abc");

        doAnswer(invocation -> {
            ProcessDefinitionEntity entity = invocation.getArgument(0);
            entity.setId(idSequence.incrementAndGet());
            return 1;
        }).when(processDefinitionMapper).insert(any(ProcessDefinitionEntity.class));

        PublishResultVO result = service.publish(1L, 9L);

        assertThat(result.getVersion()).isEqualTo(2);
        assertThat(result.getFlowableDefinitionKey()).isEqualTo("MASTER_DATA_APPROVAL");
        assertThat(model.getStatus()).isEqualTo(ProcessModelStatus.PUBLISHED);
        assertThat(model.getCurrentDefinitionId()).isEqualTo(result.getProcessDefinitionId());
        verify(nodeAssigneeRuleMapper).insert(any(NodeAssigneeRuleEntity.class));
        verify(repositoryService).createDeployment();
    }

    /** DSL 编译失败时不应发生任何部署动作。 */
    @Test
    void publish_shouldNotDeployWhenCompileFails() {
        ProcessModelEntity model = draftModel();
        when(processModelMapper.selectById(1L)).thenReturn(model);
        when(workflowModelCompiler.compile(any()))
                .thenThrow(new WorkflowModelValidationException("条件节点缺少默认分支"));

        assertThatThrownBy(() -> service.publish(1L, 9L))
                .isInstanceOf(WorkflowModelValidationException.class);

        verify(repositoryService, never()).createDeployment();
        verify(processDefinitionMapper, never()).insert(any(ProcessDefinitionEntity.class));
    }

    /** 下线当前生效版本：挂起 Flowable 流程定义，两处状态改为 DISABLED。 */
    @Test
    void disable_shouldSuspendDefinitionAndUpdateStatus() {
        ProcessModelEntity model = draftModel();
        ProcessDefinitionEntity definition = existingDefinition(1);
        when(processModelMapper.selectById(1L)).thenReturn(model);
        when(processDefinitionMapper.selectById(model.getCurrentDefinitionId())).thenReturn(definition);

        service.disable(1L);

        verify(repositoryService).suspendProcessDefinitionById(definition.getFlowableDefinitionId());
        assertThat(definition.getStatus()).isEqualTo(ProcessModelStatus.DISABLED);
        assertThat(model.getStatus()).isEqualTo(ProcessModelStatus.DISABLED);
    }

    /** 已下线的版本重复下线应被拒绝。 */
    @Test
    void disable_shouldRejectWhenAlreadyDisabled() {
        ProcessModelEntity model = draftModel();
        ProcessDefinitionEntity definition = existingDefinition(1);
        definition.setStatus(ProcessModelStatus.DISABLED);
        when(processModelMapper.selectById(1L)).thenReturn(model);
        when(processDefinitionMapper.selectById(model.getCurrentDefinitionId())).thenReturn(definition);

        assertThatThrownBy(() -> service.disable(1L)).isInstanceOf(BusinessException.class);
        verify(repositoryService, never()).suspendProcessDefinitionById(anyString());
    }

    /** 重新启用已下线版本：激活 Flowable 流程定义，两处状态改回 PUBLISHED。 */
    @Test
    void enable_shouldActivateDefinitionAndUpdateStatus() {
        ProcessModelEntity model = draftModel();
        ProcessDefinitionEntity definition = existingDefinition(1);
        definition.setStatus(ProcessModelStatus.DISABLED);
        when(processModelMapper.selectById(1L)).thenReturn(model);
        when(processDefinitionMapper.selectById(model.getCurrentDefinitionId())).thenReturn(definition);

        service.enable(1L);

        verify(repositoryService).activateProcessDefinitionById(definition.getFlowableDefinitionId());
        assertThat(definition.getStatus()).isEqualTo(ProcessModelStatus.PUBLISHED);
        assertThat(model.getStatus()).isEqualTo(ProcessModelStatus.PUBLISHED);
    }

    /** 当前版本未下线时不允许启用。 */
    @Test
    void enable_shouldRejectWhenCurrentVersionNotDisabled() {
        ProcessModelEntity model = draftModel();
        ProcessDefinitionEntity definition = existingDefinition(1);
        when(processModelMapper.selectById(1L)).thenReturn(model);
        when(processDefinitionMapper.selectById(model.getCurrentDefinitionId())).thenReturn(definition);

        assertThatThrownBy(() -> service.enable(1L)).isInstanceOf(BusinessException.class);
        verify(repositoryService, never()).activateProcessDefinitionById(anyString());
    }

    /** 版本历史按版本号倒序返回，携带只读 DSL 快照。 */
    @Test
    void listVersions_shouldReturnOrderedVersionHistory() {
        ProcessModelEntity model = draftModel();
        when(processModelMapper.selectById(1L)).thenReturn(model);
        when(processDefinitionMapper.selectList(any())).thenReturn(List.of(existingDefinition(1)));

        List<ProcessDefinitionVersionVO> versions = service.listVersions(1L);

        assertThat(versions).hasSize(1);
        assertThat(versions.get(0).getVersion()).isEqualTo(1);
        assertThat(versions.get(0).getModelJsonSnapshot()).isEqualTo(model.getModelJson());
    }

    private ProcessModelEntity draftModel() {
        return ProcessModelEntity.builder()
                .id(1L)
                .processCode("MASTER_DATA_APPROVAL")
                .processName("主数据变更审批流程")
                .modelJson("{\"processCode\":\"MASTER_DATA_APPROVAL\"}")
                .status(ProcessModelStatus.PUBLISHED)
                .currentDefinitionId(10L)
                .build();
    }

    private ProcessDefinitionEntity existingDefinition(int version) {
        return ProcessDefinitionEntity.builder()
                .id(10L)
                .processModelId(1L)
                .processCode("MASTER_DATA_APPROVAL")
                .version(version)
                .flowableDefinitionKey("MASTER_DATA_APPROVAL")
                .flowableDefinitionId("MASTER_DATA_APPROVAL:1:xyz")
                .modelJsonSnapshot("{\"processCode\":\"MASTER_DATA_APPROVAL\"}")
                .status(ProcessModelStatus.PUBLISHED)
                .publishedBy("system")
                .build();
    }
}
