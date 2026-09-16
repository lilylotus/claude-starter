package cn.nihility.rbac.workflow.dslv2.binding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.workflow.constant.ProcessModelStatus;
import cn.nihility.rbac.workflow.dslv2.dto.ProcessBindingRequest;
import cn.nihility.rbac.workflow.dslv2.dto.ProcessBindingVO;
import cn.nihility.rbac.workflow.entity.ProcessDefinitionEntity;
import cn.nihility.rbac.workflow.entity.ProcessModelEntity;
import cn.nihility.rbac.workflow.mapper.ProcessDefinitionMapper;
import cn.nihility.rbac.workflow.mapper.ProcessModelMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * 业务绑定生命周期管理服务真实数据库集成测试：切换版本（含跨流程模型，
 * allow-cross-model-binding-switch change tasks.md 2.1）、删除（软删除，
 * add-process-binding-delete change tasks.md 7.1），参照同目录
 * {@code ProcessBindingResolutionServiceTest} 的风格。
 */
@SpringBootTest
@Transactional
class WorkflowProcessBindingServiceTest {

    @Autowired
    private WorkflowProcessBindingService bindingService;
    @Autowired
    private ProcessBindingResolutionService resolutionService;
    @Autowired
    private ProcessModelMapper processModelMapper;
    @Autowired
    private ProcessDefinitionMapper processDefinitionMapper;

    private static final AtomicInteger SEQ = new AtomicInteger();

    /**
     * 同一流程模型内切换到更新版本应成功，{@code definitionId} 正确更新
     * （tasks.md 2.1.1）。
     */
    @Test
    void switchDefinition_shouldSucceed_whenUpgradingWithinSameModel() {
        ProcessModelEntity model = insertModel();
        ProcessDefinitionEntity v1 = insertDefinition(model.getId(), 1, ProcessModelStatus.PUBLISHED);
        ProcessDefinitionEntity v2 = insertDefinition(model.getId(), 2, ProcessModelStatus.PUBLISHED);
        ProcessBindingVO binding = createGlobalBinding("TEST_SWITCH_BIZ1", "TEST_SWITCH_OP1", v1.getId());

        ProcessBindingVO updated = bindingService.switchDefinition(
                binding.getId(), switchRequest(v2.getId(), binding.getRevision()), 1L);

        assertThat(updated.getDefinitionId()).isEqualTo(v2.getId());
    }

    /**
     * 切换到另一个流程模型下的已发布版本应成功，不再要求"必须与当前绑定属于同一流程模型"
     * （tasks.md 2.1.2，design.md Decision 1）。
     */
    @Test
    void switchDefinition_shouldSucceed_whenTargetBelongsToAnotherModel() {
        ProcessModelEntity modelA = insertModel();
        ProcessModelEntity modelB = insertModel();
        ProcessDefinitionEntity defA = insertDefinition(modelA.getId(), 3, ProcessModelStatus.PUBLISHED);
        ProcessDefinitionEntity defB = insertDefinition(modelB.getId(), 1, ProcessModelStatus.PUBLISHED);
        ProcessBindingVO binding = createGlobalBinding("TEST_SWITCH_BIZ2", "TEST_SWITCH_OP2", defA.getId());

        ProcessBindingVO updated = bindingService.switchDefinition(
                binding.getId(), switchRequest(defB.getId(), binding.getRevision()), 1L);

        assertThat(updated.getDefinitionId()).isEqualTo(defB.getId());
        assertThat(defA.getProcessModelId()).isNotEqualTo(defB.getProcessModelId());
    }

    /** 切换到非 PUBLISHED 状态的定义仍应被拒绝（tasks.md 2.1.3）。 */
    @Test
    void switchDefinition_shouldReject_whenTargetNotPublished() {
        ProcessModelEntity model = insertModel();
        ProcessDefinitionEntity v1 = insertDefinition(model.getId(), 1, ProcessModelStatus.PUBLISHED);
        ProcessDefinitionEntity disabled = insertDefinition(model.getId(), 2, ProcessModelStatus.DISABLED);
        ProcessBindingVO binding = createGlobalBinding("TEST_SWITCH_BIZ3", "TEST_SWITCH_OP3", v1.getId());

        assertThatThrownBy(() -> bindingService.switchDefinition(
                binding.getId(), switchRequest(disabled.getId(), binding.getRevision()), 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已发布");
    }

    /** {@code expectedRevision} 冲突时仍应拒绝（tasks.md 2.1.4）。 */
    @Test
    void switchDefinition_shouldReject_whenExpectedRevisionConflicts() {
        ProcessModelEntity model = insertModel();
        ProcessDefinitionEntity v1 = insertDefinition(model.getId(), 1, ProcessModelStatus.PUBLISHED);
        ProcessDefinitionEntity v2 = insertDefinition(model.getId(), 2, ProcessModelStatus.PUBLISHED);
        ProcessBindingVO binding = createGlobalBinding("TEST_SWITCH_BIZ4", "TEST_SWITCH_OP4", v1.getId());

        assertThatThrownBy(() -> bindingService.switchDefinition(
                binding.getId(), switchRequest(v2.getId(), binding.getRevision() + 99), 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("revision");
    }

    /** 删除一条绑定后，{@code listBindings()} 不再返回它（tasks.md 7.1.1）。 */
    @Test
    void deleteBinding_shouldExcludeFromListBindings() {
        ProcessModelEntity model = insertModel();
        ProcessDefinitionEntity definition = insertDefinition(model.getId(), 1, ProcessModelStatus.PUBLISHED);
        ProcessBindingVO binding = createGlobalBinding("TEST_DELETE_BIZ1", "TEST_DELETE_OP1", definition.getId());

        bindingService.deleteBinding(binding.getId(), 1L);

        List<ProcessBindingVO> remaining = bindingService.listBindings();
        assertThat(remaining).noneMatch(vo -> vo.getId().equals(binding.getId()));
    }

    /** 删除一条绑定后，同一维度可重新新建绑定成功（tasks.md 7.1.2）。 */
    @Test
    void deleteBinding_shouldAllowRecreatingSameDimension() {
        ProcessModelEntity model = insertModel();
        ProcessDefinitionEntity definition = insertDefinition(model.getId(), 1, ProcessModelStatus.PUBLISHED);
        String bizType = "TEST_DELETE_BIZ2";
        String operationType = "TEST_DELETE_OP2";
        ProcessBindingVO binding = createGlobalBinding(bizType, operationType, definition.getId());

        bindingService.deleteBinding(binding.getId(), 1L);
        ProcessBindingVO recreated = createGlobalBinding(bizType, operationType, definition.getId());

        assertThat(recreated.getId()).isNotEqualTo(binding.getId());
        assertThat(recreated.getEnabled()).isTrue();
    }

    /** 删除一条状态为启用的绑定应直接成功，不要求先停用（tasks.md 7.1.3）。 */
    @Test
    void deleteBinding_shouldSucceed_whenBindingCurrentlyEnabled() {
        ProcessModelEntity model = insertModel();
        ProcessDefinitionEntity definition = insertDefinition(model.getId(), 1, ProcessModelStatus.PUBLISHED);
        ProcessBindingVO binding = createGlobalBinding("TEST_DELETE_BIZ3", "TEST_DELETE_OP3", definition.getId());
        assertThat(binding.getEnabled()).isTrue();

        bindingService.deleteBinding(binding.getId(), 1L);

        assertThatThrownBy(() -> bindingService.getBinding(binding.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不存在");
    }

    /** 删除后 {@code resolve} 不再命中该绑定（tasks.md 7.1.4）。 */
    @Test
    void deleteBinding_shouldMakeResolutionMiss() {
        ProcessModelEntity model = insertModel();
        ProcessDefinitionEntity definition = insertDefinition(model.getId(), 1, ProcessModelStatus.PUBLISHED);
        String bizType = "TEST_DELETE_BIZ4";
        String operationType = "TEST_DELETE_OP4";
        ProcessBindingVO binding = createGlobalBinding(bizType, operationType, definition.getId());

        assertThat(resolutionService.resolve(bizType, operationType, null).getId()).isEqualTo(binding.getId());

        bindingService.deleteBinding(binding.getId(), 1L);

        assertThatThrownBy(() -> resolutionService.resolve(bizType, operationType, null))
                .isInstanceOf(BusinessException.class);
    }

    /** 删除一个不存在/已被删除的 {@code bindingId} 时应抛出"业务绑定不存在"（tasks.md 7.1.5）。 */
    @Test
    void deleteBinding_shouldReject_whenBindingNotFoundOrAlreadyDeleted() {
        ProcessModelEntity model = insertModel();
        ProcessDefinitionEntity definition = insertDefinition(model.getId(), 1, ProcessModelStatus.PUBLISHED);
        ProcessBindingVO binding = createGlobalBinding("TEST_DELETE_BIZ5", "TEST_DELETE_OP5", definition.getId());
        bindingService.deleteBinding(binding.getId(), 1L);

        assertThatThrownBy(() -> bindingService.deleteBinding(binding.getId(), 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不存在");
        assertThatThrownBy(() -> bindingService.deleteBinding(-1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不存在");
    }

    /** 落库一个已发布状态的流程模型，供切换/删除场景测试使用。 */
    private ProcessModelEntity insertModel() {
        String processCode = "TEST_BINDING_MODEL_" + SEQ.incrementAndGet();
        LocalDateTime now = LocalDateTime.now();
        ProcessModelEntity model = ProcessModelEntity.builder()
                .processCode(processCode)
                .processName("业务绑定服务测试模型")
                .modelJson("{}")
                .status(ProcessModelStatus.PUBLISHED)
                .enabled(true)
                .draftRevision(1L)
                .draftStatus("EDITING")
                .createBy("test").createTime(now).updateBy("test").updateTime(now)
                .build();
        processModelMapper.insert(model);
        return model;
    }

    /** 落库一条流程定义版本，不涉及真实 Flowable 部署，{@code flowableDefinitionId} 留空。 */
    private ProcessDefinitionEntity insertDefinition(Long processModelId, int version, String status) {
        String processCode = "TEST_BINDING_DEF_" + SEQ.incrementAndGet();
        LocalDateTime now = LocalDateTime.now();
        ProcessDefinitionEntity definition = ProcessDefinitionEntity.builder()
                .processModelId(processModelId)
                .processCode(processCode)
                .version(version)
                .schemaVersion(2)
                .flowableDefinitionKey(processCode)
                .status(status)
                .publishedBy("test").publishedTime(now)
                .createBy("test").createTime(now).updateBy("test").updateTime(now)
                .build();
        processDefinitionMapper.insert(definition);
        return definition;
    }

    /** 通过 {@link WorkflowProcessBindingService#createBinding} 新建一条 GLOBAL 范围绑定。 */
    private ProcessBindingVO createGlobalBinding(String bizType, String operationType, Long definitionId) {
        ProcessBindingRequest request = new ProcessBindingRequest();
        request.setBizType(bizType);
        request.setOperationType(operationType);
        request.setScopeType("GLOBAL");
        request.setDefinitionId(definitionId);
        return bindingService.createBinding(request, 1L);
    }

    /** 构造切换绑定版本请求，仅携带 {@code switchDefinition} 实际使用到的字段。 */
    private ProcessBindingRequest switchRequest(Long definitionId, Long expectedRevision) {
        ProcessBindingRequest request = new ProcessBindingRequest();
        request.setDefinitionId(definitionId);
        request.setExpectedRevision(expectedRevision);
        return request;
    }
}
