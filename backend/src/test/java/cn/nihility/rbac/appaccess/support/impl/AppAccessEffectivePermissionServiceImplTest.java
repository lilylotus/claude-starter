package cn.nihility.rbac.appaccess.support.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.appaccess.constant.EffectiveSourceType;
import cn.nihility.rbac.appaccess.dto.AppAccessEffectiveItemVO;
import cn.nihility.rbac.appaccess.override.constant.OverrideType;
import cn.nihility.rbac.appaccess.override.entity.ManualOverrideEntity;
import cn.nihility.rbac.appaccess.override.mapper.ManualOverrideMapper;
import cn.nihility.rbac.appaccess.policy.entity.PolicyBrowserRuleEntity;
import cn.nihility.rbac.appaccess.policy.entity.PolicyIpRuleEntity;
import cn.nihility.rbac.appaccess.policy.mapper.PolicyBrowserRuleMapper;
import cn.nihility.rbac.appaccess.policy.mapper.PolicyGrantMapper;
import cn.nihility.rbac.appaccess.policy.mapper.PolicyIpRuleMapper;
import cn.nihility.rbac.appaccess.support.dto.EffectiveRawRow;
import cn.nihility.rbac.appaccess.support.mapper.AppAccessEffectiveMapper;
import cn.nihility.rbac.common.result.PageResult;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link AppAccessEffectivePermissionServiceImpl} 的单元测试（tasks.md 8.4），覆盖优先级
 * 规则四种场景（{@code DENY} 最高、{@code GRANT} 次之、策略再次之、都无则不可访问）、
 * 停用中的策略不计入（由 {@code existsActiveGrant} SQL 内 {@code p.status=2000} 条件保证，
 * 此处通过打桩其返回值间接验证调用方分支正确）、"包含已收回"审计查询；以及
 * app-access-request-control change 新增的"考虑请求上下文"判定入口（浏览器/IP 满足与否、
 * 两者 AND 语义、多策略命中取"任一满足"、GRANT 不受约束、DENY 优先级不变）与原两参数方法
 * 行为不变的回归验证。
 */
@ExtendWith(MockitoExtension.class)
class AppAccessEffectivePermissionServiceImplTest {

    /** 被测服务的人工例外数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private ManualOverrideMapper manualOverrideMapper;

    /** 被测服务的策略计算结果数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private PolicyGrantMapper policyGrantMapper;

    /** 被测服务的最终生效权限查询专用数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private AppAccessEffectiveMapper appAccessEffectiveMapper;

    /** 被测服务的策略请求控制条件-浏览器白名单数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private PolicyBrowserRuleMapper policyBrowserRuleMapper;

    /** 被测服务的策略请求控制条件-IP/网段白名单数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private PolicyIpRuleMapper policyIpRuleMapper;

    /** 被测服务实例。 */
    private AppAccessEffectivePermissionServiceImpl service;

    /**
     * 每个用例执行前重新构造被测服务。
     */
    @BeforeEach
    void setUp() {
        service = new AppAccessEffectivePermissionServiceImpl(manualOverrideMapper, policyGrantMapper,
                appAccessEffectiveMapper, policyBrowserRuleMapper, policyIpRuleMapper);
    }

    /**
     * 用户对应用同时存在启用中策略产生的授权记录与 {@code DENY} 人工例外时，最终生效权限为
     * 不可访问，DENY 优先级最高。
     */
    @Test
    void isAuthorized_shouldReturnFalse_whenDenyOverrideExists() {
        when(manualOverrideMapper.selectOne(any())).thenReturn(
                ManualOverrideEntity.builder().userId(1L).appId(2L).overrideType(OverrideType.DENY).build());

        assertThat(service.isAuthorized(1L, 2L)).isFalse();
    }

    /**
     * 不存在 {@code DENY} 例外、存在 {@code GRANT} 例外时，最终生效权限为可访问，不再查询
     * 策略授权记录。
     */
    @Test
    void isAuthorized_shouldReturnTrue_whenGrantOverrideExists() {
        when(manualOverrideMapper.selectOne(any())).thenReturn(
                ManualOverrideEntity.builder().userId(1L).appId(2L).overrideType(OverrideType.GRANT).build());

        assertThat(service.isAuthorized(1L, 2L)).isTrue();

        org.mockito.Mockito.verify(policyGrantMapper, org.mockito.Mockito.never()).existsActiveGrant(any(), any());
    }

    /**
     * 不存在任何人工例外、存在启用中策略产生的授权记录时，最终生效权限为可访问。
     */
    @Test
    void isAuthorized_shouldReturnTrue_whenNoOverrideButActivePolicyGrantExists() {
        when(manualOverrideMapper.selectOne(any())).thenReturn(null);
        when(policyGrantMapper.existsActiveGrant(1L, 2L)).thenReturn(1);

        assertThat(service.isAuthorized(1L, 2L)).isTrue();
    }

    /**
     * 既无人工例外也无启用中策略产生的授权记录时，最终生效权限为不可访问（策略停用后，
     * {@code existsActiveGrant} 的 SQL 内 {@code p.status=2000} 条件会让其立即不计入，
     * 此处以返回 0 模拟该效果，即"停用中的策略不计入"）。
     */
    @Test
    void isAuthorized_shouldReturnFalse_whenNoOverrideAndNoActivePolicyGrant() {
        when(manualOverrideMapper.selectOne(any())).thenReturn(null);
        when(policyGrantMapper.existsActiveGrant(1L, 2L)).thenReturn(0);

        assertThat(service.isAuthorized(1L, 2L)).isFalse();
    }

    /**
     * 按用户查询：命中启用中策略的应用应标记为 {@code POLICY} 来源并列出策略名称。
     */
    @Test
    void listEffectiveByUser_shouldReturnPolicySource_whenOnlyPolicyGrantExists() {
        when(appAccessEffectiveMapper.selectActiveGrantRowsByUser(1L)).thenReturn(List.of(
                EffectiveRawRow.builder().userId(1L).appId(2L).appName("应用A").policyName("策略1").build()));
        when(appAccessEffectiveMapper.selectManualOnlyRowsByUser(1L)).thenReturn(List.of());

        PageResult<AppAccessEffectiveItemVO> result = service.listEffectiveByUser(1L, false, 1, 10);

        assertThat(result.getTotal()).isEqualTo(1L);
        AppAccessEffectiveItemVO item = result.getRecords().get(0);
        assertThat(item.getAppId()).isEqualTo(2L);
        assertThat(item.getSourceType()).isEqualTo(EffectiveSourceType.POLICY);
        assertThat(item.getPolicyNames()).containsExactly("策略1");
    }

    /**
     * 按用户查询：命中多条策略的应用应在 {@code policyNames} 中列出全部策略名称。
     */
    @Test
    void listEffectiveByUser_shouldListAllPolicyNames_whenMultiplePoliciesGrantSameApp() {
        when(appAccessEffectiveMapper.selectActiveGrantRowsByUser(1L)).thenReturn(List.of(
                EffectiveRawRow.builder().userId(1L).appId(2L).appName("应用A").policyName("策略1").build(),
                EffectiveRawRow.builder().userId(1L).appId(2L).appName("应用A").policyName("策略2").build()));
        when(appAccessEffectiveMapper.selectManualOnlyRowsByUser(1L)).thenReturn(List.of());

        PageResult<AppAccessEffectiveItemVO> result = service.listEffectiveByUser(1L, false, 1, 10);

        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getPolicyNames()).containsExactlyInAnyOrder("策略1", "策略2");
    }

    /**
     * 按用户查询：纯人工追加授权（无策略命中）的应用应标记为 {@code MANUAL_GRANT} 来源。
     */
    @Test
    void listEffectiveByUser_shouldReturnManualGrantSource_whenPureManualGrant() {
        when(appAccessEffectiveMapper.selectActiveGrantRowsByUser(1L)).thenReturn(List.of());
        when(appAccessEffectiveMapper.selectManualOnlyRowsByUser(1L)).thenReturn(List.of(
                EffectiveRawRow.builder().userId(1L).appId(3L).appName("应用B").overrideType(OverrideType.GRANT).build()));

        PageResult<AppAccessEffectiveItemVO> result = service.listEffectiveByUser(1L, false, 1, 10);

        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getSourceType()).isEqualTo(EffectiveSourceType.MANUAL_GRANT);
        assertThat(result.getRecords().get(0).getPolicyNames()).isEmpty();
    }

    /**
     * 按用户查询：命中启用中策略但被 {@code DENY} 人工例外收回的应用，默认（不含审计参数）
     * 不出现在结果列表中。
     */
    @Test
    void listEffectiveByUser_shouldExcludeRevokedApp_byDefault() {
        when(appAccessEffectiveMapper.selectActiveGrantRowsByUser(1L)).thenReturn(List.of(
                EffectiveRawRow.builder().userId(1L).appId(2L).appName("应用A").policyName("策略1")
                        .overrideType(OverrideType.DENY).build()));
        when(appAccessEffectiveMapper.selectManualOnlyRowsByUser(1L)).thenReturn(List.of());

        PageResult<AppAccessEffectiveItemVO> result = service.listEffectiveByUser(1L, false, 1, 10);

        assertThat(result.getRecords()).isEmpty();
        assertThat(result.getTotal()).isZero();
    }

    /**
     * 按用户查询并开启 {@code includeRevoked} 时，命中启用中策略但被 {@code DENY} 人工例外
     * 收回的应用应出现在结果列表中，标记为 {@code REVOKED}。
     */
    @Test
    void listEffectiveByUser_shouldIncludeRevokedApp_whenIncludeRevokedTrue() {
        when(appAccessEffectiveMapper.selectActiveGrantRowsByUser(1L)).thenReturn(List.of(
                EffectiveRawRow.builder().userId(1L).appId(2L).appName("应用A").policyName("策略1")
                        .overrideType(OverrideType.DENY).build()));
        when(appAccessEffectiveMapper.selectManualOnlyRowsByUser(1L)).thenReturn(List.of());

        PageResult<AppAccessEffectiveItemVO> result = service.listEffectiveByUser(1L, true, 1, 10);

        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getSourceType()).isEqualTo(EffectiveSourceType.REVOKED);
        assertThat(result.getRecords().get(0).getPolicyNames()).containsExactly("策略1");
    }

    /**
     * 按应用查询：与按用户查询共用同一套合并判定规则，命中策略的用户标记为 {@code POLICY}
     * 来源。
     */
    @Test
    void listEffectiveByApp_shouldReturnPolicySource_whenOnlyPolicyGrantExists() {
        when(appAccessEffectiveMapper.selectActiveGrantRowsByApp(2L)).thenReturn(List.of(
                EffectiveRawRow.builder().userId(1L).userName("张三").appId(2L).policyName("策略1").build()));
        when(appAccessEffectiveMapper.selectManualOnlyRowsByApp(2L)).thenReturn(List.of());

        PageResult<AppAccessEffectiveItemVO> result = service.listEffectiveByApp(2L, 1, 10);

        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getUserId()).isEqualTo(1L);
        assertThat(result.getRecords().get(0).getSourceType()).isEqualTo(EffectiveSourceType.POLICY);
    }

    /**
     * 分页参数生效：完整结果集按页码/每页条数切片，{@code total} 反映完整结果集大小。
     */
    @Test
    void listEffectiveByUser_shouldPaginate() {
        when(appAccessEffectiveMapper.selectActiveGrantRowsByUser(1L)).thenReturn(List.of(
                EffectiveRawRow.builder().userId(1L).appId(1L).appName("应用1").policyName("策略").build(),
                EffectiveRawRow.builder().userId(1L).appId(2L).appName("应用2").policyName("策略").build(),
                EffectiveRawRow.builder().userId(1L).appId(3L).appName("应用3").policyName("策略").build()));
        when(appAccessEffectiveMapper.selectManualOnlyRowsByUser(1L)).thenReturn(List.of());

        PageResult<AppAccessEffectiveItemVO> result = service.listEffectiveByUser(1L, false, 1, 2);

        assertThat(result.getTotal()).isEqualTo(3L);
        assertThat(result.getRecords()).hasSize(2);
    }

    /**
     * 按用户查询：命中策略配置了请求控制条件时，结果条目 {@code hasRequestControl} 应为
     * {@code true}（app-access-request-control change design.md Decision 5）。
     */
    @Test
    void listEffectiveByUser_shouldMarkHasRequestControl_whenHitPolicyConfiguredRequestControl() {
        when(appAccessEffectiveMapper.selectActiveGrantRowsByUser(1L)).thenReturn(List.of(
                EffectiveRawRow.builder().userId(1L).appId(2L).appName("应用A").policyName("策略1")
                        .hasRequestControl(true).build()));
        when(appAccessEffectiveMapper.selectManualOnlyRowsByUser(1L)).thenReturn(List.of());

        PageResult<AppAccessEffectiveItemVO> result = service.listEffectiveByUser(1L, false, 1, 10);

        assertThat(result.getRecords().get(0).isHasRequestControl()).isTrue();
    }

    /**
     * 按用户查询：纯人工追加授权（{@code MANUAL_GRANT}）来源的结果条目 {@code
     * hasRequestControl} 恒为 {@code false}，即使某行原始数据携带了 {@code true}。
     */
    @Test
    void listEffectiveByUser_shouldNotMarkHasRequestControl_whenManualGrantSource() {
        when(appAccessEffectiveMapper.selectActiveGrantRowsByUser(1L)).thenReturn(List.of());
        when(appAccessEffectiveMapper.selectManualOnlyRowsByUser(1L)).thenReturn(List.of(
                EffectiveRawRow.builder().userId(1L).appId(3L).appName("应用B").overrideType(OverrideType.GRANT)
                        .build()));

        PageResult<AppAccessEffectiveItemVO> result = service.listEffectiveByUser(1L, false, 1, 10);

        assertThat(result.getRecords().get(0).isHasRequestControl()).isFalse();
    }

    /**
     * 考虑请求上下文的判定：用户对应用同时存在启用中策略产生的授权记录与 {@code DENY} 人工
     * 例外时，DENY 优先级最高，不再看候选策略/请求上下文（spec.md"DENY 例外优先级最高"
     * Scenario）。
     */
    @Test
    void isAuthorizedWithContext_shouldReturnFalse_whenDenyOverrideExists() {
        when(manualOverrideMapper.selectOne(any())).thenReturn(
                ManualOverrideEntity.builder().userId(1L).appId(2L).overrideType(OverrideType.DENY).build());

        assertThat(service.isAuthorized(1L, 2L, "192.168.1.1", "chrome-ua")).isFalse();
        org.mockito.Mockito.verify(policyGrantMapper, org.mockito.Mockito.never()).selectActivePolicyIds(any(), any());
    }

    /**
     * 考虑请求上下文的判定：存在 {@code GRANT} 人工例外时无条件放行，不看
     * {@code clientIp}/{@code userAgent}，也不查询候选策略的请求控制条件（spec.md
     * "GRANT 例外在没有 DENY 时生效且不受请求控制约束" Scenario）。
     */
    @Test
    void isAuthorizedWithContext_shouldReturnTrue_whenGrantOverrideExists_ignoringContext() {
        when(manualOverrideMapper.selectOne(any())).thenReturn(
                ManualOverrideEntity.builder().userId(1L).appId(2L).overrideType(OverrideType.GRANT).build());

        assertThat(service.isAuthorized(1L, 2L, null, null)).isTrue();
        org.mockito.Mockito.verify(policyGrantMapper, org.mockito.Mockito.never()).selectActivePolicyIds(any(), any());
    }

    /**
     * 考虑请求上下文的判定：不存在人工例外、也不存在任何身份命中的启用中策略时，不可访问
     * （spec.md"既无例外也无策略授权时不可访问" Scenario）。
     */
    @Test
    void isAuthorizedWithContext_shouldReturnFalse_whenNoCandidatePolicies() {
        when(manualOverrideMapper.selectOne(any())).thenReturn(null);
        when(policyGrantMapper.selectActivePolicyIds(1L, 2L)).thenReturn(List.of());

        assertThat(service.isAuthorized(1L, 2L, "192.168.1.1", "chrome-ua")).isFalse();
    }

    /**
     * 考虑请求上下文的判定：命中策略未配置任何请求控制条件时，身份命中即可通过。
     */
    @Test
    void isAuthorizedWithContext_shouldReturnTrue_whenCandidatePolicyHasNoRequestControl() {
        when(manualOverrideMapper.selectOne(any())).thenReturn(null);
        when(policyGrantMapper.selectActivePolicyIds(1L, 2L)).thenReturn(List.of(10L));
        when(policyBrowserRuleMapper.selectList(any())).thenReturn(List.of());
        when(policyIpRuleMapper.selectList(any())).thenReturn(List.of());

        assertThat(service.isAuthorized(1L, 2L, "192.168.1.1", null)).isTrue();
    }

    /**
     * 考虑请求上下文的判定：命中策略配置了浏览器白名单，当前 {@code User-Agent} 识别为
     * 白名单内的浏览器时通过（spec.md"无任何例外时按策略授权与请求控制共同判定" Scenario）。
     */
    @Test
    void isAuthorizedWithContext_shouldReturnTrue_whenBrowserWhitelistSatisfied() {
        String chromeUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
        when(manualOverrideMapper.selectOne(any())).thenReturn(null);
        when(policyGrantMapper.selectActivePolicyIds(1L, 2L)).thenReturn(List.of(10L));
        when(policyBrowserRuleMapper.selectList(any())).thenReturn(
                List.of(PolicyBrowserRuleEntity.builder().policyId(10L).browserCode("CHROME").build()));
        when(policyIpRuleMapper.selectList(any())).thenReturn(List.of());

        assertThat(service.isAuthorized(1L, 2L, "192.168.1.1", chromeUserAgent)).isTrue();
    }

    /**
     * 考虑请求上下文的判定：命中策略配置了浏览器白名单，当前 {@code User-Agent} 不属于
     * 白名单内的浏览器时拒绝。
     */
    @Test
    void isAuthorizedWithContext_shouldReturnFalse_whenBrowserWhitelistNotSatisfied() {
        String firefoxUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0";
        when(manualOverrideMapper.selectOne(any())).thenReturn(null);
        when(policyGrantMapper.selectActivePolicyIds(1L, 2L)).thenReturn(List.of(10L));
        when(policyBrowserRuleMapper.selectList(any())).thenReturn(
                List.of(PolicyBrowserRuleEntity.builder().policyId(10L).browserCode("CHROME").build()));
        when(policyIpRuleMapper.selectList(any())).thenReturn(List.of());

        assertThat(service.isAuthorized(1L, 2L, "192.168.1.1", firefoxUserAgent)).isFalse();
    }

    /**
     * 考虑请求上下文的判定：命中策略配置了 IP 白名单，当前客户端 IP 落在网段内时通过。
     */
    @Test
    void isAuthorizedWithContext_shouldReturnTrue_whenIpWhitelistSatisfied() {
        when(manualOverrideMapper.selectOne(any())).thenReturn(null);
        when(policyGrantMapper.selectActivePolicyIds(1L, 2L)).thenReturn(List.of(10L));
        when(policyBrowserRuleMapper.selectList(any())).thenReturn(List.of());
        when(policyIpRuleMapper.selectList(any())).thenReturn(
                List.of(PolicyIpRuleEntity.builder().policyId(10L).ipCidr("192.168.1.0/24").build()));

        assertThat(service.isAuthorized(1L, 2L, "192.168.1.100", null)).isTrue();
    }

    /**
     * 考虑请求上下文的判定：命中策略配置了 IP 白名单，当前客户端 IP 不在网段内时拒绝
     * （spec.md"身份命中但当前请求不满足命中策略的请求控制时拒绝签发票据" Scenario 的
     * 服务层依据）。
     */
    @Test
    void isAuthorizedWithContext_shouldReturnFalse_whenIpWhitelistNotSatisfied() {
        when(manualOverrideMapper.selectOne(any())).thenReturn(null);
        when(policyGrantMapper.selectActivePolicyIds(1L, 2L)).thenReturn(List.of(10L));
        when(policyBrowserRuleMapper.selectList(any())).thenReturn(List.of());
        when(policyIpRuleMapper.selectList(any())).thenReturn(
                List.of(PolicyIpRuleEntity.builder().policyId(10L).ipCidr("192.168.1.0/24").build()));

        assertThat(service.isAuthorized(1L, 2L, "10.0.0.1", null)).isFalse();
    }

    /**
     * 考虑请求上下文的判定：同一策略同时配置浏览器与 IP 白名单时需同时满足（AND 语义），
     * 仅满足其中一项时仍拒绝（spec.md"两个维度都配置时需同时满足" Scenario）。
     */
    @Test
    void isAuthorizedWithContext_shouldRequireBothDimensions_whenBothConfigured() {
        String chromeUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
        when(manualOverrideMapper.selectOne(any())).thenReturn(null);
        when(policyGrantMapper.selectActivePolicyIds(1L, 2L)).thenReturn(List.of(10L));
        when(policyBrowserRuleMapper.selectList(any())).thenReturn(
                List.of(PolicyBrowserRuleEntity.builder().policyId(10L).browserCode("CHROME").build()));
        when(policyIpRuleMapper.selectList(any())).thenReturn(
                List.of(PolicyIpRuleEntity.builder().policyId(10L).ipCidr("192.168.1.0/24").build()));

        // 浏览器满足但 IP 不满足：拒绝。
        assertThat(service.isAuthorized(1L, 2L, "10.0.0.1", chromeUserAgent)).isFalse();
        // 两者都满足：通过。
        assertThat(service.isAuthorized(1L, 2L, "192.168.1.1", chromeUserAgent)).isTrue();
    }

    /**
     * 考虑请求上下文的判定：多条策略命中同一用户+应用时，只要其中一条策略的请求控制条件
     * 被满足即判定为可访问（spec.md"多条策略命中时任一策略满足请求控制即可" Scenario）。
     */
    @Test
    void isAuthorizedWithContext_shouldReturnTrue_whenAnyCandidatePolicySatisfied() {
        String firefoxUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0";
        when(manualOverrideMapper.selectOne(any())).thenReturn(null);
        when(policyGrantMapper.selectActivePolicyIds(1L, 2L)).thenReturn(List.of(10L, 20L));
        when(policyBrowserRuleMapper.selectList(any())).thenReturn(
                List.of(PolicyBrowserRuleEntity.builder().policyId(10L).browserCode("CHROME").build()));
        when(policyIpRuleMapper.selectList(any())).thenReturn(List.of());

        // 策略 10 要求 Chrome，不满足；策略 20 未配置请求控制，身份命中即满足，整体判定通过。
        assertThat(service.isAuthorized(1L, 2L, "192.168.1.1", firefoxUserAgent)).isTrue();
    }

    /**
     * 回归验证：原两参数 {@code isAuthorized} 方法行为不受本次改动影响，既有测试
     * （见本类靠前的 {@code isAuthorized_should*} 系列用例）已覆盖，此处补充确认其不查询
     * 候选策略的请求控制条件（即"仅身份维度"语义未变）。
     */
    @Test
    void isAuthorized_withoutContext_shouldNotQueryRequestControlRules() {
        when(manualOverrideMapper.selectOne(any())).thenReturn(null);
        when(policyGrantMapper.existsActiveGrant(1L, 2L)).thenReturn(1);

        assertThat(service.isAuthorized(1L, 2L)).isTrue();

        org.mockito.Mockito.verify(policyGrantMapper, org.mockito.Mockito.never()).selectActivePolicyIds(any(), any());
        org.mockito.Mockito.verify(policyBrowserRuleMapper, org.mockito.Mockito.never()).selectList(any());
        org.mockito.Mockito.verify(policyIpRuleMapper, org.mockito.Mockito.never()).selectList(any());
    }
}
