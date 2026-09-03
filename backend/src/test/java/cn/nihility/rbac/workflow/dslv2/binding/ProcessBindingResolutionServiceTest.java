package cn.nihility.rbac.workflow.dslv2.binding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.org.entity.OrgEntity;
import cn.nihility.rbac.org.mapper.OrgMapper;
import cn.nihility.rbac.workflow.entity.ProcessBindingEntity;
import cn.nihility.rbac.workflow.mapper.ProcessBindingMapper;
import java.time.LocalDateTime;
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

    @Test
    void resolve_shouldPreferExactOrgOverAncestorAndGlobal() {
        OrgEntity root = insertOrg("V2BindRoot", null, null);
        OrgEntity child = insertOrg("V2BindChild", root.getId(), root.getOrgPath());
        OrgEntity grandchild = insertOrg("V2BindGrandchild", child.getId(), child.getOrgPath());

        insertBinding("USER", "UPDATE", "GLOBAL", 0L, 701L);
        insertBinding("USER", "UPDATE", "ORG", root.getId(), 702L);
        insertBinding("USER", "UPDATE", "ORG", child.getId(), 703L);

        ProcessBindingEntity resolved = resolutionService.resolve("USER", "UPDATE", grandchild.getId());
        assertThat(resolved.getDefinitionId()).isEqualTo(703L);
    }

    @Test
    void resolve_shouldFallBackToNearestAncestor_whenExactOrgNotBound() {
        OrgEntity root = insertOrg("V2BindRoot2", null, null);
        OrgEntity child = insertOrg("V2BindChild2", root.getId(), root.getOrgPath());
        OrgEntity grandchild = insertOrg("V2BindGrandchild2", child.getId(), child.getOrgPath());

        insertBinding("USER", "UPDATE", "GLOBAL", 0L, 711L);
        insertBinding("USER", "UPDATE", "ORG", root.getId(), 712L);

        ProcessBindingEntity resolved = resolutionService.resolve("USER", "UPDATE", grandchild.getId());
        assertThat(resolved.getDefinitionId()).isEqualTo(712L);
    }

    @Test
    void resolve_shouldFallBackToGlobal_whenNoOrgBindingMatches() {
        OrgEntity root = insertOrg("V2BindRoot3", null, null);
        insertBinding("USER", "UPDATE", "GLOBAL", 0L, 721L);

        ProcessBindingEntity resolved = resolutionService.resolve("USER", "UPDATE", root.getId());
        assertThat(resolved.getDefinitionId()).isEqualTo(721L);
    }

    @Test
    void resolve_shouldReject_whenNothingConfigured() {
        OrgEntity root = insertOrg("V2BindRoot4", null, null);
        assertThatThrownBy(() -> resolutionService.resolve("APP", "DELETE", root.getId()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void resolve_shouldIgnoreDisabledBinding() {
        OrgEntity root = insertOrg("V2BindRoot5", null, null);
        ProcessBindingEntity disabled = insertBinding("POSITION", "CREATE", "ORG", root.getId(), 731L);
        disabled.setEnabled(false);
        processBindingMapper.updateById(disabled);
        insertBinding("POSITION", "CREATE", "GLOBAL", 0L, 732L);

        ProcessBindingEntity resolved = resolutionService.resolve("POSITION", "CREATE", root.getId());
        assertThat(resolved.getDefinitionId()).isEqualTo(732L);
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
