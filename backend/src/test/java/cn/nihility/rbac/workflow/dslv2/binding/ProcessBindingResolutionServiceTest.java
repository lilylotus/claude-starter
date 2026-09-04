package cn.nihility.rbac.workflow.dslv2.binding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.org.entity.OrgEntity;
import cn.nihility.rbac.org.mapper.OrgMapper;
import cn.nihility.rbac.workflow.constant.ExecutionMode;
import cn.nihility.rbac.workflow.constant.ProcessModelStatus;
import cn.nihility.rbac.workflow.entity.ProcessBindingEntity;
import cn.nihility.rbac.workflow.entity.ProcessDefinitionEntity;
import cn.nihility.rbac.workflow.entity.ProcessModelEntity;
import cn.nihility.rbac.workflow.mapper.ProcessBindingMapper;
import cn.nihility.rbac.workflow.mapper.ProcessDefinitionMapper;
import cn.nihility.rbac.workflow.mapper.ProcessModelMapper;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * 业务绑定确定性解析真实数据库集成测试：精确组织 → 最近祖先组织 → 全局
 * （production-approval-lifecycle change design.md Decision 4，tasks.md 4.5）。
 */
@SpringBootTest
@Transactional
class ProcessBindingResolutionServiceTest {

    @Autowired
    private ProcessBindingResolutionService resolutionService;
    @Autowired
    private ProcessBindingMapper processBindingMapper;
    @Autowired
    private OrgMapper orgMapper;
    @Autowired
    private ProcessModelMapper processModelMapper;
    @Autowired
    private ProcessDefinitionMapper processDefinitionMapper;

    private static final AtomicInteger MODEL_SEQ = new AtomicInteger();

    @Test
    void resolve_shouldPreferExactOrgOverAncestorAndGlobal() {
        OrgEntity root = insertOrg("V2BindRoot", null, null);
        OrgEntity child = insertOrg("V2BindChild", root.getId(), root.getOrgPath());
        OrgEntity grandchild = insertOrg("V2BindGrandchild", child.getId(), child.getOrgPath());

        insertBinding("TEST_RESOLVE_BIZ", "TEST_RESOLVE_OP", "GLOBAL", 0L, 701L);
        insertBinding("TEST_RESOLVE_BIZ", "TEST_RESOLVE_OP", "ORG", root.getId(), 702L);
        insertBinding("TEST_RESOLVE_BIZ", "TEST_RESOLVE_OP", "ORG", child.getId(), 703L);

        ProcessBindingEntity resolved = resolutionService.resolve("TEST_RESOLVE_BIZ", "TEST_RESOLVE_OP", grandchild.getId());
        assertThat(resolved.getDefinitionId()).isEqualTo(703L);
    }

    @Test
    void resolve_shouldFallBackToNearestAncestor_whenExactOrgNotBound() {
        OrgEntity root = insertOrg("V2BindRoot2", null, null);
        OrgEntity child = insertOrg("V2BindChild2", root.getId(), root.getOrgPath());
        OrgEntity grandchild = insertOrg("V2BindGrandchild2", child.getId(), child.getOrgPath());

        insertBinding("TEST_RESOLVE_BIZ", "TEST_RESOLVE_OP", "GLOBAL", 0L, 711L);
        insertBinding("TEST_RESOLVE_BIZ", "TEST_RESOLVE_OP", "ORG", root.getId(), 712L);

        ProcessBindingEntity resolved = resolutionService.resolve("TEST_RESOLVE_BIZ", "TEST_RESOLVE_OP", grandchild.getId());
        assertThat(resolved.getDefinitionId()).isEqualTo(712L);
    }

    @Test
    void resolve_shouldFallBackToGlobal_whenNoOrgBindingMatches() {
        OrgEntity root = insertOrg("V2BindRoot3", null, null);
        insertBinding("TEST_RESOLVE_BIZ", "TEST_RESOLVE_OP", "GLOBAL", 0L, 721L);

        ProcessBindingEntity resolved = resolutionService.resolve("TEST_RESOLVE_BIZ", "TEST_RESOLVE_OP", root.getId());
        assertThat(resolved.getDefinitionId()).isEqualTo(721L);
    }

    @Test
    void resolve_shouldReject_whenNothingConfigured() {
        // 使用不属于 V13__seed_process_binding_global_fallback.sql 兜底种子覆盖范围
        // （ORG/USER/POSITION/APP × CREATE/UPDATE/ENABLE/DISABLE/DELETE）的虚构维度，
        // 避免命中种子数据导致本该拒绝的场景被误判为已配置。
        OrgEntity root = insertOrg("V2BindRoot4", null, null);
        assertThatThrownBy(() -> resolutionService.resolve("TEST_RESOLVE_BIZ2", "TEST_RESOLVE_OP2", root.getId()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void resolve_shouldIgnoreDisabledBinding() {
        OrgEntity root = insertOrg("V2BindRoot5", null, null);
        ProcessBindingEntity disabled = insertBinding("TEST_RESOLVE_BIZ3", "TEST_RESOLVE_OP3", "ORG", root.getId(), 731L);
        disabled.setEnabled(false);
        processBindingMapper.updateById(disabled);
        insertBinding("TEST_RESOLVE_BIZ3", "TEST_RESOLVE_OP3", "GLOBAL", 0L, 732L);

        ProcessBindingEntity resolved = resolutionService.resolve("TEST_RESOLVE_BIZ3", "TEST_RESOLVE_OP3", root.getId());
        assertThat(resolved.getDefinitionId()).isEqualTo(732L);
    }

    /**
     * {@code resolveForStart} 命中启用绑定、流程定义已发布、所属模型接受新发起、执行模式
     * {@code LEGACY_SYNC} 时应正常返回解析结果（production-approval-lifecycle change
     * tasks.md 4.5"本轮补齐"）。
     */
    @Test
    void resolveForStart_shouldSucceed_whenBindingAndDefinitionAndModelAllValid() {
        ProcessDefinitionEntity definition = insertModelAndDefinition(true, ProcessModelStatus.PUBLISHED);
        insertBinding("TEST_START_BIZ", "TEST_START_OP_OK", "GLOBAL", 0L, definition.getId());

        ResolvedProcessBinding resolved = resolutionService.resolveForStart("TEST_START_BIZ", "TEST_START_OP_OK", null);

        assertThat(resolved.definition().getId()).isEqualTo(definition.getId());
        assertThat(resolved.binding().getExecutionMode()).isEqualTo(ExecutionMode.LEGACY_SYNC);
    }

    /**
     * 绑定执行模式为尚未实现的 {@code RELIABLE_ASYNC} 时应直接拒绝发起，不静默按
     * {@code LEGACY_SYNC} 语义处理（design.md Decision 4"执行模式边界"）。
     */
    @Test
    void resolveForStart_shouldReject_whenExecutionModeIsReliableAsync() {
        ProcessDefinitionEntity definition = insertModelAndDefinition(true, ProcessModelStatus.PUBLISHED);
        ProcessBindingEntity binding = insertBinding(
                "TEST_START_BIZ", "T_START_ASYNC", "GLOBAL", 0L, definition.getId());
        binding.setExecutionMode(ExecutionMode.RELIABLE_ASYNC);
        processBindingMapper.updateById(binding);

        assertThatThrownBy(() -> resolutionService.resolveForStart("TEST_START_BIZ", "T_START_ASYNC", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("可靠异步执行尚未实现");
    }

    /**
     * 绑定所属流程模型 {@code enabled=false}（模型级停止接受新发起，tasks.md 4.6）时应拒绝，
     * 即便绑定自身与流程定义均合法有效。
     */
    @Test
    void resolveForStart_shouldReject_whenModelDisabled() {
        ProcessDefinitionEntity definition = insertModelAndDefinition(false, ProcessModelStatus.PUBLISHED);
        insertBinding("TEST_START_BIZ", "T_MODEL_OFF", "GLOBAL", 0L, definition.getId());

        assertThatThrownBy(() -> resolutionService.resolveForStart(
                "TEST_START_BIZ", "T_MODEL_OFF", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("停止接受新发起");
    }

    /**
     * 绑定指向的流程定义已下线（{@code status != PUBLISHED}）时应拒绝——绑定可能指向历史
     * 版本（显式回滚场景），必须按绑定携带的 {@code definitionId} 本身校验，不能想当然认为
     * 绑定存在即一定可用。
     */
    @Test
    void resolveForStart_shouldReject_whenBoundDefinitionNotPublished() {
        ProcessDefinitionEntity definition = insertModelAndDefinition(true, ProcessModelStatus.DISABLED);
        insertBinding("TEST_START_BIZ", "T_DEF_OFF", "GLOBAL", 0L, definition.getId());

        assertThatThrownBy(() -> resolutionService.resolveForStart(
                "TEST_START_BIZ", "T_DEF_OFF", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不存在或已下线");
    }

    /**
     * 落库一条流程模型 + 流程定义，供 {@code resolveForStart} 校验路径测试使用；不涉及
     * 真实 Flowable 部署，{@code flowableDefinitionId} 留空。
     */
    private ProcessDefinitionEntity insertModelAndDefinition(boolean modelEnabled, String definitionStatus) {
        String processCode = "TEST_RESOLVE_FOR_START_" + MODEL_SEQ.incrementAndGet();
        LocalDateTime now = LocalDateTime.now();
        ProcessModelEntity model = ProcessModelEntity.builder()
                .processCode(processCode)
                .processName("绑定解析校验测试")
                .modelJson("{}")
                .status(ProcessModelStatus.PUBLISHED)
                .enabled(modelEnabled)
                .draftRevision(1L)
                .draftStatus("EDITING")
                .createBy("test").createTime(now).updateBy("test").updateTime(now)
                .build();
        processModelMapper.insert(model);

        ProcessDefinitionEntity definition = ProcessDefinitionEntity.builder()
                .processModelId(model.getId())
                .processCode(processCode)
                .version(1)
                .schemaVersion(2)
                .flowableDefinitionKey(processCode)
                .status(definitionStatus)
                .publishedBy("test").publishedTime(now)
                .createBy("test").createTime(now).updateBy("test").updateTime(now)
                .build();
        processDefinitionMapper.insert(definition);

        model.setCurrentDefinitionId(definition.getId());
        model.setUpdateTime(LocalDateTime.now());
        processModelMapper.updateById(model);
        return definition;
    }

    private OrgEntity insertOrg(String name, Long parentId, String parentPath) {
        LocalDateTime now = LocalDateTime.now();
        OrgEntity org = OrgEntity.builder()
                .name(name)
                .code(name)
                .parentId(parentId == null ? 0L : parentId)
                .status(2000)
                .createBy("test").createTime(now).updateBy("test").updateTime(now)
                .build();
        orgMapper.insert(org);
        String path = (parentPath == null || parentPath.isBlank()) ? org.getId().toString() : parentPath + "/" + org.getId();
        org.setOrgPath(path);
        orgMapper.updateById(org);
        return org;
    }

    private ProcessBindingEntity insertBinding(String bizType, String operationType, String scopeType, Long scopeId, Long definitionId) {
        LocalDateTime now = LocalDateTime.now();
        ProcessBindingEntity entity = ProcessBindingEntity.builder()
                .bizType(bizType)
                .operationType(operationType)
                .scopeType(scopeType)
                .scopeId(scopeId)
                .definitionId(definitionId)
                .executionMode("LEGACY_SYNC")
                .revision(1L)
                .enabled(true)
                .createBy("test").createTime(now).updateBy("test").updateTime(now)
                .build();
        processBindingMapper.insert(entity);
        return entity;
    }
}
