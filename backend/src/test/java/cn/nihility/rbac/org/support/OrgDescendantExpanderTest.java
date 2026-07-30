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
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
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
        when(orgMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(root, child, grandChild, other));

        Set<Long> result = orgDescendantExpander.expandWithDescendants(Set.of(2L));

        assertThat(result).containsExactlyInAnyOrder(2L, 3L);
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
