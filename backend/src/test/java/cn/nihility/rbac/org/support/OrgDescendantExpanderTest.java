package cn.nihility.rbac.org.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.org.constant.OrgStatus;
import cn.nihility.rbac.org.entity.OrgEntity;
import cn.nihility.rbac.org.mapper.OrgMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link OrgDescendantExpander} 的单元测试，覆盖 BFS 展开根组织自身连同全部子孙组织 id、
 * 排除无关兄弟组织、空输入短路等分支逻辑（org-scope-data-permission change design.md
 * Decision 3）。
 */
@ExtendWith(MockitoExtension.class)
class OrgDescendantExpanderTest {

    /** 被测组件的数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private OrgMapper orgMapper;

    /** 被测组件实例。 */
    private OrgDescendantExpander orgDescendantExpander;

    /**
     * 每个用例执行前重新构造被测组件。
     */
    @BeforeEach
    void setUp() {
        orgDescendantExpander = new OrgDescendantExpander(orgMapper);
    }

    /**
     * 展开子孙组织 id 时，应收集根组织自身连同其全部子孙组织 id，不包含无关的兄弟组织。
     */
    @Test
    void expandWithDescendants_shouldCollectSelfAndAllDescendants() {
        OrgEntity root = buildEntity(1L, "总公司", "ROOT", 0L, OrgStatus.ENABLED, 10);
        OrgEntity child = buildEntity(2L, "研发部", "DEV", 1L, OrgStatus.ENABLED, 5);
        OrgEntity grandChild = buildEntity(3L, "研发一组", "DEV1", 2L, OrgStatus.ENABLED, 1);
        OrgEntity other = buildEntity(4L, "财务部", "FIN", 1L, OrgStatus.ENABLED, 4);
        root.setOrgPath("1");
        child.setOrgPath("1/2");
        grandChild.setOrgPath("1/2/3");
        other.setOrgPath("1/4");
        when(orgMapper.selectList(any()))
                .thenReturn(List.of(child), List.of(child, grandChild));

        Set<Long> result = orgDescendantExpander.expandWithDescendants(Set.of(2L));

        assertThat(result).containsExactlyInAnyOrder(2L, 3L);
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<QueryWrapper> queryCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(orgMapper).selectList(queryCaptor.capture());
        QueryWrapper<?> pathQuery = queryCaptor.getValue();
        assertThat(pathQuery.getSqlSegment()).contains("org_path =").contains("OR org_path LIKE");
        assertThat(pathQuery.getParamNameValuePairs().values()).contains("1/2", "1/2/%");
    }

    /**
     * 展开一个空的根组织 id 集合时，应直接返回空集合，不发起任何数据库查询。
     */
    @Test
    void expandWithDescendants_shouldReturnEmptySet_whenRootOrgIdsEmpty() {
        Set<Long> result = orgDescendantExpander.expandWithDescendants(Set.of());

        assertThat(result).isEmpty();
        verify(orgMapper, never()).selectList(any(LambdaQueryWrapper.class));
    }

    /**
     * {@link OrgDescendantExpander#expandWithDescendantsIncludingDeleted}：根组织自身已被
     * 逻辑删除时，仍应能查到其 {@code orgPath} 并据此展开出删除前的全部子孙组织 id
     * （fix-app-sync-pull-deleted-org-scope change design.md Decision 3）。
     */
    @Test
    void expandWithDescendantsIncludingDeleted_shouldExpand_whenRootItselfDeleted() {
        OrgEntity root = buildEntity(1L, "总公司", "ROOT", 0L, OrgStatus.DELETED, 10);
        OrgEntity child = buildEntity(2L, "研发部", "DEV", 1L, OrgStatus.ENABLED, 5);
        root.setOrgPath("1");
        child.setOrgPath("1/2");
        when(orgMapper.selectList(any()))
                .thenReturn(List.of(root), List.of(root, child));

        Set<Long> result = orgDescendantExpander.expandWithDescendantsIncludingDeleted(Set.of(1L));

        assertThat(result).containsExactlyInAnyOrder(1L, 2L);
    }

    /**
     * {@link OrgDescendantExpander#expandWithDescendantsIncludingDeleted}：根组织未被删除，
     * 但其某个非根子孙组织已被逻辑删除时，展开结果中仍应包含该已删除的子孙组织 id。
     */
    @Test
    void expandWithDescendantsIncludingDeleted_shouldIncludeDeletedNonRootDescendant() {
        OrgEntity root = buildEntity(1L, "总公司", "ROOT", 0L, OrgStatus.ENABLED, 10);
        OrgEntity child = buildEntity(2L, "研发部", "DEV", 1L, OrgStatus.ENABLED, 5);
        OrgEntity deletedGrandChild = buildEntity(3L, "研发一组", "DEV1", 2L, OrgStatus.DELETED, 1);
        root.setOrgPath("1");
        child.setOrgPath("1/2");
        deletedGrandChild.setOrgPath("1/2/3");
        when(orgMapper.selectList(any()))
                .thenReturn(List.of(root), List.of(root, child, deletedGrandChild));

        Set<Long> result = orgDescendantExpander.expandWithDescendantsIncludingDeleted(Set.of(1L));

        assertThat(result).containsExactlyInAnyOrder(1L, 2L, 3L);
    }

    /**
     * {@link OrgDescendantExpander#expandWithDescendantsIncludingDeleted}：传入空集合时应直接
     * 返回空集合，不发起任何数据库查询。
     */
    @Test
    void expandWithDescendantsIncludingDeleted_shouldReturnEmptySet_whenRootOrgIdsEmpty() {
        Set<Long> result = orgDescendantExpander.expandWithDescendantsIncludingDeleted(Set.of());

        assertThat(result).isEmpty();
        verify(orgMapper, never()).selectList(any(LambdaQueryWrapper.class));
    }

    /**
     * 同一批数据（含已删除组织）分别调用两个方法时，{@code expandWithDescendantsIncludingDeleted}
     * 应比 {@code expandWithDescendants} 多出恰好被逻辑删除的那些组织 id，其余展开结果一致，
     * 确认差异只体现在已删除组织上（tasks.md 1.3）。
     */
    @Test
    void bothExpandMethods_shouldOnlyDifferOnDeletedOrgs_givenSameData() {
        OrgEntity root = buildEntity(1L, "总公司", "ROOT", 0L, OrgStatus.ENABLED, 10);
        OrgEntity child = buildEntity(2L, "研发部", "DEV", 1L, OrgStatus.ENABLED, 5);
        OrgEntity deletedGrandChild = buildEntity(3L, "研发一组", "DEV1", 2L, OrgStatus.DELETED, 1);
        root.setOrgPath("1");
        child.setOrgPath("1/2");
        deletedGrandChild.setOrgPath("1/2/3");

        // expandWithDescendants：根查询排除已删除（命中 root），子孙查询排除已删除（命中 root、child）。
        when(orgMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(root));
        when(orgMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(root, child));
        Set<Long> excludingDeleted = orgDescendantExpander.expandWithDescendants(Set.of(1L));

        // expandWithDescendantsIncludingDeleted：不排除已删除，命中全部三条。
        Mockito.reset(orgMapper);
        when(orgMapper.selectList(any())).thenReturn(List.of(root), List.of(root, child, deletedGrandChild));
        Set<Long> includingDeleted = orgDescendantExpander.expandWithDescendantsIncludingDeleted(Set.of(1L));

        assertThat(excludingDeleted).containsExactlyInAnyOrder(1L, 2L);
        assertThat(includingDeleted).containsExactlyInAnyOrder(1L, 2L, 3L);
        assertThat(includingDeleted).containsAll(excludingDeleted);
        Set<Long> onlyInIncludingDeleted = new HashSet<>(includingDeleted);
        onlyInIncludingDeleted.removeAll(excludingDeleted);
        assertThat(onlyInIncludingDeleted).containsExactly(3L);
    }

    /**
     * 构造一个用于测试的组织实体。
     *
     * @param id       组织 id
     * @param name     组织名称
     * @param code     组织编码
     * @param parentId 上级组织 id
     * @param status   状态码值
     * @param showOrder 显示序号
     * @return 组织实体
     */
    private OrgEntity buildEntity(long id, String name, String code, long parentId, int status, int showOrder) {
        return OrgEntity.builder()
                .id(id)
                .name(name)
                .code(code)
                .parentId(parentId)
                .status(status)
                .showOrder(showOrder)
                .build();
    }
}
