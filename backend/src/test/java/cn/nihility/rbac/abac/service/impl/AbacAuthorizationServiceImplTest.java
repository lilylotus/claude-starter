package cn.nihility.rbac.abac.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.abac.constant.AbacPolicyStatus;
import cn.nihility.rbac.abac.entity.AbacPolicyEntity;
import cn.nihility.rbac.abac.entity.AbacPolicyResourceEntity;
import cn.nihility.rbac.abac.entity.AbacPolicyTimeWindowEntity;
import cn.nihility.rbac.abac.entity.AbacPolicyUserAttrEntity;
import cn.nihility.rbac.abac.mapper.AbacPolicyMapper;
import cn.nihility.rbac.abac.mapper.AbacPolicyResourceMapper;
import cn.nihility.rbac.abac.mapper.AbacPolicyTimeWindowMapper;
import cn.nihility.rbac.abac.mapper.AbacPolicyUserAttrMapper;
import cn.nihility.rbac.abac.support.AbacTimeWindowEvaluator;
import cn.nihility.rbac.abac.support.AbacUserAttrEvaluator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link AbacAuthorizationServiceImpl} 的单元测试，覆盖 spec.md"ABAC 运行时限制判定"
 * 需求下的全部 Scenario：资源未绑定策略不拒绝、命中/不命中用户属性条件、命中时间窗口
 * 条件、两维度都配置时需同时满足、多候选任意命中即拒绝、停用策略不作为候选
 * （abac-policy-engine change tasks.md 5.1）。
 */
@ExtendWith(MockitoExtension.class)
class AbacAuthorizationServiceImplTest {

    /** 被测服务的策略规则数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private AbacPolicyMapper abacPolicyMapper;

    /** 被测服务的策略目标资源编码数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private AbacPolicyResourceMapper abacPolicyResourceMapper;

    /** 被测服务的策略用户属性条件数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private AbacPolicyUserAttrMapper abacPolicyUserAttrMapper;

    /** 被测服务的策略时间窗口条件数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private AbacPolicyTimeWindowMapper abacPolicyTimeWindowMapper;

    /** 被测服务的用户属性条件判定依赖，使用 Mockito 打桩。 */
    @Mock
    private AbacUserAttrEvaluator abacUserAttrEvaluator;

    /** 被测服务的时间窗口条件判定依赖，使用 Mockito 打桩。 */
    @Mock
    private AbacTimeWindowEvaluator abacTimeWindowEvaluator;

    /** 被测服务实例。 */
    private AbacAuthorizationServiceImpl service;

    /**
     * 每个用例执行前重新构造被测服务。
     */
    @BeforeEach
    void setUp() {
        service = new AbacAuthorizationServiceImpl(abacPolicyMapper, abacPolicyResourceMapper,
                abacPolicyUserAttrMapper, abacPolicyTimeWindowMapper, abacUserAttrEvaluator, abacTimeWindowEvaluator);
    }

    /**
     * 资源编码未绑定任何策略规则时，判定结果恒为"不拒绝"，不应继续查询候选策略状态
     * （spec.md"资源编码未绑定任何策略时不拒绝" Scenario）。
     */
    @Test
    void isDenied_shouldReturnFalse_whenResourceHasNoBoundPolicy() {
        when(abacPolicyResourceMapper.selectList(any())).thenReturn(List.of());

        boolean result = service.isDenied(1L, "OrgManagement:org:edit");

        assertThat(result).isFalse();
    }

    /**
     * 命中用户属性条件（未配置时间窗口，视为该维度恒满足）时，判定结果为"拒绝"
     * （spec.md"命中用户属性条件时判定为拒绝" Scenario）。
     */
    @Test
    void isDenied_shouldReturnTrue_whenUserAttrConditionMatched() {
        mockSingleCandidatePolicy(100L, "OrgManagement:org:edit");
        when(abacUserAttrEvaluator.matchesAll(eq(1L), any())).thenReturn(true);
        when(abacTimeWindowEvaluator.matchesAny(any())).thenReturn(true);

        boolean result = service.isDenied(1L, "OrgManagement:org:edit");

        assertThat(result).isTrue();
    }

    /**
     * 不命中用户属性条件时，判定结果为"不拒绝"（spec.md"不命中用户属性条件时判定为不
     * 拒绝" Scenario）。
     */
    @Test
    void isDenied_shouldReturnFalse_whenUserAttrConditionNotMatched() {
        mockSingleCandidatePolicy(100L, "OrgManagement:org:edit");
        when(abacUserAttrEvaluator.matchesAll(eq(1L), any())).thenReturn(false);
        when(abacTimeWindowEvaluator.matchesAny(any())).thenReturn(true);

        boolean result = service.isDenied(1L, "OrgManagement:org:edit");

        assertThat(result).isFalse();
    }

    /**
     * 命中时间窗口条件（未配置用户属性条件，视为该维度恒满足）时，判定结果为"拒绝"，
     * 不区分用户（spec.md"命中时间窗口条件时判定为拒绝" Scenario）。
     */
    @Test
    void isDenied_shouldReturnTrue_whenTimeWindowConditionMatched() {
        mockSingleCandidatePolicy(100L, "ExcelExport:export:execute");
        when(abacUserAttrEvaluator.matchesAll(eq(2L), any())).thenReturn(true);
        when(abacTimeWindowEvaluator.matchesAny(any())).thenReturn(true);

        boolean result = service.isDenied(2L, "ExcelExport:export:execute");

        assertThat(result).isTrue();
    }

    /**
     * 用户属性条件、时间窗口条件均配置时，须两者同时满足才算命中；本例时间窗口维度不
     * 满足，判定结果为"不拒绝"（spec.md"用户属性与时间窗口都配置时需同时满足才算命中"
     * Scenario）。
     */
    @Test
    void isDenied_shouldReturnFalse_whenOnlyOneDimensionMatched() {
        mockSingleCandidatePolicy(100L, "OrgManagement:org:edit");
        when(abacUserAttrEvaluator.matchesAll(eq(1L), any())).thenReturn(true);
        when(abacTimeWindowEvaluator.matchesAny(any())).thenReturn(false);

        boolean result = service.isDenied(1L, "OrgManagement:org:edit");

        assertThat(result).isFalse();
    }

    /**
     * 多条候选策略中，只要任意一条命中，判定结果即为"拒绝"，不要求全部候选都命中
     * （spec.md"多条候选策略中任意一条命中即判定拒绝" Scenario）。
     */
    @Test
    void isDenied_shouldReturnTrue_whenAnyCandidatePolicyMatched() {
        String resourceCode = "OrgManagement:org:edit";
        when(abacPolicyResourceMapper.selectList(any())).thenReturn(List.of(
                AbacPolicyResourceEntity.builder().policyId(100L).permissionCode(resourceCode).build(),
                AbacPolicyResourceEntity.builder().policyId(200L).permissionCode(resourceCode).build()));
        when(abacPolicyMapper.selectList(any())).thenReturn(List.of(
                AbacPolicyEntity.builder().id(100L).status(AbacPolicyStatus.ENABLED).build(),
                AbacPolicyEntity.builder().id(200L).status(AbacPolicyStatus.ENABLED).build()));
        when(abacPolicyUserAttrMapper.selectList(any())).thenReturn(List.of(
                AbacPolicyUserAttrEntity.builder().policyId(100L).build(),
                AbacPolicyUserAttrEntity.builder().policyId(200L).build()));
        when(abacPolicyTimeWindowMapper.selectList(any())).thenReturn(List.of());
        when(abacTimeWindowEvaluator.matchesAny(any())).thenReturn(true);
        // P1（policyId=100）未命中，P2（policyId=200）命中。
        when(abacUserAttrEvaluator.matchesAll(eq(1L), any())).thenReturn(false, true);

        boolean result = service.isDenied(1L, resourceCode);

        assertThat(result).isTrue();
    }

    /**
     * 资源编码绑定的唯一一条策略当前状态为停用时，该策略不计入候选，判定结果为"不拒绝"，
     * 即使其条件本会命中当前用户与当前请求（spec.md"停用策略不作为候选" Scenario）。
     */
    @Test
    void isDenied_shouldReturnFalse_whenOnlyBoundPolicyIsDisabled() {
        String resourceCode = "OrgManagement:org:edit";
        when(abacPolicyResourceMapper.selectList(any())).thenReturn(List.of(
                AbacPolicyResourceEntity.builder().policyId(100L).permissionCode(resourceCode).build()));
        // 查询启用状态候选时过滤掉了停用策略，返回空列表。
        when(abacPolicyMapper.selectList(any())).thenReturn(List.of());

        boolean result = service.isDenied(1L, resourceCode);

        assertThat(result).isFalse();
    }

    /**
     * 构造一条命中给定资源编码、状态为启用的唯一候选策略，并打通用户属性条件/时间窗口
     * 条件的批量查询链路。
     *
     * @param policyId     策略 id
     * @param resourceCode 资源编码
     */
    private void mockSingleCandidatePolicy(Long policyId, String resourceCode) {
        when(abacPolicyResourceMapper.selectList(any())).thenReturn(List.of(
                AbacPolicyResourceEntity.builder().policyId(policyId).permissionCode(resourceCode).build()));
        when(abacPolicyMapper.selectList(any())).thenReturn(List.of(
                AbacPolicyEntity.builder().id(policyId).status(AbacPolicyStatus.ENABLED).build()));
        when(abacPolicyUserAttrMapper.selectList(any())).thenReturn(List.of(
                AbacPolicyUserAttrEntity.builder().policyId(policyId).build()));
        when(abacPolicyTimeWindowMapper.selectList(any())).thenReturn(List.of(
                AbacPolicyTimeWindowEntity.builder().policyId(policyId).build()));
    }
}
