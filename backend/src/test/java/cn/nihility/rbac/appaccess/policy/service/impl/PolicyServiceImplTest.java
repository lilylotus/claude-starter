package cn.nihility.rbac.appaccess.policy.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.appaccess.policy.constant.PolicyAttrOperator;
import cn.nihility.rbac.appaccess.policy.dto.PolicyCreateRequest;
import cn.nihility.rbac.appaccess.policy.dto.PolicyOrgScopeRequestItem;
import cn.nihility.rbac.appaccess.policy.dto.PolicyUpdateRequest;
import cn.nihility.rbac.appaccess.policy.dto.PolicyUserAttrRequestItem;
import cn.nihility.rbac.appaccess.policy.dto.PolicyVO;
import cn.nihility.rbac.appaccess.policy.entity.PolicyBrowserRuleEntity;
import cn.nihility.rbac.appaccess.policy.entity.PolicyEntity;
import cn.nihility.rbac.appaccess.policy.entity.PolicyIpRuleEntity;
import cn.nihility.rbac.appaccess.policy.entity.PolicyOrgScopeEntity;
import cn.nihility.rbac.appaccess.policy.entity.PolicyTargetAppEntity;
import cn.nihility.rbac.appaccess.policy.entity.PolicyUserAttrEntity;
import cn.nihility.rbac.appaccess.policy.mapper.PolicyBrowserRuleMapper;
import cn.nihility.rbac.appaccess.policy.mapper.PolicyGrantMapper;
import cn.nihility.rbac.appaccess.policy.mapper.PolicyIpRuleMapper;
import cn.nihility.rbac.appaccess.policy.mapper.PolicyMapper;
import cn.nihility.rbac.appaccess.policy.mapper.PolicyOrgScopeMapper;
import cn.nihility.rbac.appaccess.policy.mapper.PolicyTargetAppMapper;
import cn.nihility.rbac.appaccess.policy.mapper.PolicyUserAttrMapper;
import cn.nihility.rbac.auth.service.CurrentOperatorService;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.formfield.constant.FormFieldBizType;
import cn.nihility.rbac.metadata.constant.MetadataFieldStatus;
import cn.nihility.rbac.metadata.entity.MetadataFieldEntity;
import cn.nihility.rbac.metadata.mapper.MetadataFieldMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link PolicyServiceImpl} 的单元测试（tasks.md 8.1），覆盖新增/编辑整体替换语义、组织
 * 范围与用户属性条件同时为空校验拒绝、目标应用为空校验拒绝、属性条件关联非 USER 域/
 * 已停用元数据字段被拒绝、同一策略内属性字段重复被拒绝、删除级联清理授权记录。
 */
@ExtendWith(MockitoExtension.class)
class PolicyServiceImplTest {

    /** 被测服务的策略规则数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private PolicyMapper policyMapper;

    /** 被测服务的策略组织范围条件数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private PolicyOrgScopeMapper policyOrgScopeMapper;

    /** 被测服务的策略用户属性条件数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private PolicyUserAttrMapper policyUserAttrMapper;

    /** 被测服务的策略目标应用数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private PolicyTargetAppMapper policyTargetAppMapper;

    /** 被测服务的策略请求控制条件-浏览器白名单数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private PolicyBrowserRuleMapper policyBrowserRuleMapper;

    /** 被测服务的策略请求控制条件-IP/网段白名单数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private PolicyIpRuleMapper policyIpRuleMapper;

    /** 被测服务的策略计算结果数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private PolicyGrantMapper policyGrantMapper;

    /** 被测服务的元数据字段数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private MetadataFieldMapper metadataFieldMapper;

    /** 被测服务的当前登录操作人用户 id 解析依赖，使用 Mockito 打桩。 */
    @Mock
    private CurrentOperatorService currentOperatorService;

    /** 被测服务实例。 */
    private PolicyServiceImpl policyService;

    /**
     * 每个用例执行前重新构造被测服务，并让 {@code policyMapper.insert} 模拟自增主键回填。
     */
    @BeforeEach
    void setUp() {
        policyService = new PolicyServiceImpl(policyMapper, policyOrgScopeMapper, policyUserAttrMapper,
                policyTargetAppMapper, policyBrowserRuleMapper, policyIpRuleMapper, policyGrantMapper,
                metadataFieldMapper, currentOperatorService);
        lenient().when(currentOperatorService.resolveUserId()).thenReturn(1L);
        lenient().doAnswer(invocation -> {
            PolicyEntity entity = invocation.getArgument(0);
            entity.setId(100L);
            return 1;
        }).when(policyMapper).insert(any(PolicyEntity.class));
        lenient().when(policyOrgScopeMapper.selectByPolicyId(anyLong())).thenReturn(List.of());
        lenient().when(policyUserAttrMapper.selectByPolicyId(anyLong())).thenReturn(List.of());
        lenient().when(policyTargetAppMapper.selectByPolicyId(anyLong())).thenReturn(List.of());
        lenient().when(policyBrowserRuleMapper.selectList(any())).thenReturn(List.of());
        lenient().when(policyIpRuleMapper.selectList(any())).thenReturn(List.of());
    }

    /**
     * 新增策略规则成功时，应插入策略主表、组织范围、用户属性条件、目标应用四类记录。
     */
    @Test
    void create_shouldPersistPolicyAndSubTables_whenValid() {
        when(metadataFieldMapper.selectById(1L)).thenReturn(buildUserField(1L));

        PolicyCreateRequest request = validRequest();
        PolicyVO vo = policyService.create(request);

        assertThat(vo.getId()).isEqualTo(100L);
        verify(policyMapper).insert(any(PolicyEntity.class));

        ArgumentCaptor<PolicyOrgScopeEntity> orgScopeCaptor = ArgumentCaptor.forClass(PolicyOrgScopeEntity.class);
        verify(policyOrgScopeMapper).insert(orgScopeCaptor.capture());
        assertThat(orgScopeCaptor.getValue().getPolicyId()).isEqualTo(100L);
        assertThat(orgScopeCaptor.getValue().getOrgId()).isEqualTo(10L);

        ArgumentCaptor<PolicyUserAttrEntity> userAttrCaptor = ArgumentCaptor.forClass(PolicyUserAttrEntity.class);
        verify(policyUserAttrMapper).insert(userAttrCaptor.capture());
        assertThat(userAttrCaptor.getValue().getAttrValue()).isEqualTo("male");

        ArgumentCaptor<PolicyTargetAppEntity> targetAppCaptor = ArgumentCaptor.forClass(PolicyTargetAppEntity.class);
        verify(policyTargetAppMapper).insert(targetAppCaptor.capture());
        assertThat(targetAppCaptor.getValue().getAppId()).isEqualTo(200L);
    }

    /**
     * 组织范围与用户属性条件同时为空时应拒绝保存。
     */
    @Test
    void create_shouldReject_whenOrgScopeAndUserAttrBothEmpty() {
        PolicyCreateRequest request = validRequest();
        request.setOrgScopes(List.of());
        request.setUserAttrs(List.of());

        assertThatThrownBy(() -> policyService.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能同时为空");
        verify(policyMapper, never()).insert(any(PolicyEntity.class));
    }

    /**
     * 目标应用为空时应拒绝保存。
     */
    @Test
    void create_shouldReject_whenTargetAppEmpty() {
        PolicyCreateRequest request = validRequest();
        request.setTargetAppIds(List.of());

        assertThatThrownBy(() -> policyService.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("目标应用不能为空");
        verify(policyMapper, never()).insert(any(PolicyEntity.class));
    }

    /**
     * 属性条件关联的元数据字段 {@code bizType} 非 {@code USER} 时应拒绝保存。
     */
    @Test
    void create_shouldReject_whenAttrFieldNotUserBizType() {
        MetadataFieldEntity orgField = buildUserField(1L);
        orgField.setBizType(FormFieldBizType.ORG);
        when(metadataFieldMapper.selectById(1L)).thenReturn(orgField);

        PolicyCreateRequest request = validRequest();

        assertThatThrownBy(() -> policyService.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不属于用户业务域");
        verify(policyMapper, never()).insert(any(PolicyEntity.class));
    }

    /**
     * 属性条件关联的元数据字段已停用时应拒绝保存。
     */
    @Test
    void create_shouldReject_whenAttrFieldDisabled() {
        MetadataFieldEntity disabledField = buildUserField(1L);
        disabledField.setStatus(MetadataFieldStatus.DISABLED);
        when(metadataFieldMapper.selectById(1L)).thenReturn(disabledField);

        PolicyCreateRequest request = validRequest();

        assertThatThrownBy(() -> policyService.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不存在或未启用");
        verify(policyMapper, never()).insert(any(PolicyEntity.class));
    }

    /**
     * 同一策略内属性字段重复配置时应拒绝保存。
     */
    @Test
    void create_shouldReject_whenDuplicateAttrField() {
        when(metadataFieldMapper.selectById(1L)).thenReturn(buildUserField(1L));

        PolicyCreateRequest request = validRequest();
        PolicyUserAttrRequestItem duplicate = new PolicyUserAttrRequestItem();
        duplicate.setMetadataFieldId(1L);
        duplicate.setOperator(PolicyAttrOperator.EQ);
        duplicate.setValues(List.of("female"));
        request.setUserAttrs(List.of(request.getUserAttrs().get(0), duplicate));

        assertThatThrownBy(() -> policyService.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能重复配置");
        verify(policyMapper, never()).insert(any(PolicyEntity.class));
    }

    /**
     * 编辑策略规则时，组织范围/用户属性条件/目标应用均为整体替换语义：先按 policyId 删除
     * 既有记录，再按提交内容批量插入。
     */
    @Test
    void update_shouldReplaceSubTables() {
        PolicyEntity existing = PolicyEntity.builder().id(100L).name("旧名称").build();
        when(policyMapper.selectById(100L)).thenReturn(existing);
        when(metadataFieldMapper.selectById(1L)).thenReturn(buildUserField(1L));

        PolicyUpdateRequest request = new PolicyUpdateRequest();
        request.setName("新名称");
        request.setOrgScopes(validRequest().getOrgScopes());
        request.setUserAttrs(validRequest().getUserAttrs());
        request.setTargetAppIds(List.of(300L));

        policyService.update(100L, request);

        verify(policyOrgScopeMapper).delete(any());
        verify(policyUserAttrMapper).delete(any());
        verify(policyTargetAppMapper).delete(any());
        verify(policyOrgScopeMapper, times(1)).insert(any(PolicyOrgScopeEntity.class));
        verify(policyUserAttrMapper, times(1)).insert(any(PolicyUserAttrEntity.class));
        ArgumentCaptor<PolicyTargetAppEntity> targetAppCaptor = ArgumentCaptor.forClass(PolicyTargetAppEntity.class);
        verify(policyTargetAppMapper).insert(targetAppCaptor.capture());
        assertThat(targetAppCaptor.getValue().getAppId()).isEqualTo(300L);
        assertThat(existing.getName()).isEqualTo("新名称");
    }

    /**
     * 删除策略规则时，应级联删除该策略产生的全部策略授权记录，不留下孤儿记录。
     */
    @Test
    void delete_shouldCascadeDeleteGrantRecords() {
        when(policyMapper.selectById(100L)).thenReturn(PolicyEntity.builder().id(100L).build());

        policyService.delete(100L);

        verify(policyOrgScopeMapper).delete(any());
        verify(policyUserAttrMapper).delete(any());
        verify(policyTargetAppMapper).delete(any());
        verify(policyBrowserRuleMapper).delete(any());
        verify(policyIpRuleMapper).delete(any());
        verify(policyGrantMapper).delete(any());
        verify(policyMapper).deleteById(100L);
    }

    /**
     * 删除不存在的策略规则时应拒绝。
     */
    @Test
    void delete_shouldThrowException_whenPolicyNotFound() {
        when(policyMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> policyService.delete(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("策略规则不存在");
        verify(policyGrantMapper, never()).delete(any());
    }

    /**
     * 新增策略规则时提交浏览器/IP 白名单，应分别插入对应子表记录（app-access-request-control
     * change tasks.md 8.3）。
     */
    @Test
    void create_shouldPersistRequestControlRules_whenProvided() {
        when(metadataFieldMapper.selectById(1L)).thenReturn(buildUserField(1L));

        PolicyCreateRequest request = validRequest();
        request.setBrowserRules(List.of("CHROME", "EDGE"));
        request.setIpRules(List.of("192.168.1.0/24"));

        policyService.create(request);

        ArgumentCaptor<PolicyBrowserRuleEntity> browserCaptor = ArgumentCaptor.forClass(PolicyBrowserRuleEntity.class);
        verify(policyBrowserRuleMapper, times(2)).insert(browserCaptor.capture());
        assertThat(browserCaptor.getAllValues()).extracting(PolicyBrowserRuleEntity::getBrowserCode)
                .containsExactlyInAnyOrder("CHROME", "EDGE");

        ArgumentCaptor<PolicyIpRuleEntity> ipCaptor = ArgumentCaptor.forClass(PolicyIpRuleEntity.class);
        verify(policyIpRuleMapper).insert(ipCaptor.capture());
        assertThat(ipCaptor.getValue().getIpCidr()).isEqualTo("192.168.1.0/24");
    }

    /**
     * 请求控制条件完全留空时应正常保存，不触发组织范围/用户属性条件"至少一项非空"约束之外
     * 的额外报错（app-access-request-control change tasks.md 8.3）。
     */
    @Test
    void create_shouldSucceed_whenRequestControlEmpty() {
        when(metadataFieldMapper.selectById(1L)).thenReturn(buildUserField(1L));

        PolicyCreateRequest request = validRequest();
        request.setBrowserRules(List.of());
        request.setIpRules(List.of());

        PolicyVO vo = policyService.create(request);

        assertThat(vo.getId()).isEqualTo(100L);
        verify(policyBrowserRuleMapper, never()).insert(any(PolicyBrowserRuleEntity.class));
        verify(policyIpRuleMapper, never()).insert(any(PolicyIpRuleEntity.class));
    }

    /**
     * IP 白名单条目格式不合法时应拒绝保存（spec.md"非法 IP/网段格式被拒绝" Scenario）。
     */
    @Test
    void create_shouldReject_whenIpRuleInvalid() {
        when(metadataFieldMapper.selectById(1L)).thenReturn(buildUserField(1L));
        PolicyCreateRequest request = validRequest();
        request.setIpRules(List.of("not-an-ip"));

        assertThatThrownBy(() -> policyService.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("非法的 IP/网段格式");
        verify(policyMapper, never()).insert(any(PolicyEntity.class));
    }

    /**
     * 浏览器白名单编码不在预置枚举内时应拒绝保存。
     */
    @Test
    void create_shouldReject_whenBrowserCodeInvalid() {
        when(metadataFieldMapper.selectById(1L)).thenReturn(buildUserField(1L));
        PolicyCreateRequest request = validRequest();
        request.setBrowserRules(List.of("UNKNOWN_BROWSER"));

        assertThatThrownBy(() -> policyService.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("非法的浏览器编码");
        verify(policyMapper, never()).insert(any(PolicyEntity.class));
    }

    /**
     * 编辑策略规则时，浏览器/IP 白名单均为整体替换语义：先按 policyId 删除既有记录，再按
     * 提交内容批量插入（app-access-request-control change tasks.md 8.3）。
     */
    @Test
    void update_shouldReplaceRequestControlRules() {
        PolicyEntity existing = PolicyEntity.builder().id(100L).name("旧名称").build();
        when(policyMapper.selectById(100L)).thenReturn(existing);
        when(metadataFieldMapper.selectById(1L)).thenReturn(buildUserField(1L));

        PolicyUpdateRequest request = new PolicyUpdateRequest();
        request.setName("新名称");
        request.setOrgScopes(validRequest().getOrgScopes());
        request.setUserAttrs(validRequest().getUserAttrs());
        request.setTargetAppIds(List.of(300L));
        request.setBrowserRules(List.of("FIREFOX"));
        request.setIpRules(List.of("10.0.0.1"));

        policyService.update(100L, request);

        verify(policyBrowserRuleMapper).delete(any());
        verify(policyIpRuleMapper).delete(any());
        ArgumentCaptor<PolicyBrowserRuleEntity> browserCaptor = ArgumentCaptor.forClass(PolicyBrowserRuleEntity.class);
        verify(policyBrowserRuleMapper).insert(browserCaptor.capture());
        assertThat(browserCaptor.getValue().getBrowserCode()).isEqualTo("FIREFOX");
    }

    /**
     * 构造一条通过基本校验的策略规则新增请求：组织范围一条 + 用户属性条件一条 + 目标应用
     * 一个。
     *
     * @return 新增请求
     */
    private PolicyCreateRequest validRequest() {
        PolicyOrgScopeRequestItem orgScope = new PolicyOrgScopeRequestItem();
        orgScope.setOrgId(10L);
        orgScope.setIncludeChildren(false);

        PolicyUserAttrRequestItem userAttr = new PolicyUserAttrRequestItem();
        userAttr.setMetadataFieldId(1L);
        userAttr.setOperator(PolicyAttrOperator.EQ);
        userAttr.setValues(List.of("male"));

        PolicyCreateRequest request = new PolicyCreateRequest();
        request.setName("测试策略");
        request.setRemark("备注");
        request.setOrgScopes(List.of(orgScope));
        request.setUserAttrs(List.of(userAttr));
        request.setTargetAppIds(List.of(200L));
        return request;
    }

    /**
     * 构造一个测试用的、状态启用且业务域为 USER 的元数据字段实体。
     *
     * @param id 主键 id
     * @return 元数据字段实体
     */
    private MetadataFieldEntity buildUserField(long id) {
        return MetadataFieldEntity.builder()
                .id(id)
                .bizType(FormFieldBizType.USER)
                .tableName("tab_user")
                .columnName("gender")
                .columnType("VARCHAR(64)")
                .fieldCode("gender")
                .fieldName("性别")
                .status(MetadataFieldStatus.ENABLED)
                .build();
    }
}
