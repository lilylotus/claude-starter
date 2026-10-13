package cn.nihility.rbac.abac.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.abac.constant.AbacAttrOperator;
import cn.nihility.rbac.abac.dto.AbacPolicyCreateRequest;
import cn.nihility.rbac.abac.dto.AbacPolicyTimeWindowRequestItem;
import cn.nihility.rbac.abac.dto.AbacPolicyUpdateRequest;
import cn.nihility.rbac.abac.dto.AbacPolicyUserAttrRequestItem;
import cn.nihility.rbac.abac.dto.AbacPolicyVO;
import cn.nihility.rbac.abac.entity.AbacPolicyEntity;
import cn.nihility.rbac.abac.entity.AbacPolicyResourceEntity;
import cn.nihility.rbac.abac.entity.AbacPolicyTimeWindowEntity;
import cn.nihility.rbac.abac.entity.AbacPolicyUserAttrEntity;
import cn.nihility.rbac.abac.mapper.AbacPolicyMapper;
import cn.nihility.rbac.abac.mapper.AbacPolicyResourceMapper;
import cn.nihility.rbac.abac.mapper.AbacPolicyTimeWindowMapper;
import cn.nihility.rbac.abac.mapper.AbacPolicyUserAttrMapper;
import cn.nihility.rbac.auth.service.CurrentOperatorService;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.formfield.constant.FormFieldBizType;
import cn.nihility.rbac.metadata.constant.MetadataFieldStatus;
import cn.nihility.rbac.metadata.entity.MetadataFieldEntity;
import cn.nihility.rbac.metadata.mapper.MetadataFieldMapper;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link AbacPolicyServiceImpl} 的单元测试（abac-policy-engine change tasks.md 4.2），
 * 覆盖 spec.md「ABAC 策略规则的定义与维护」「ABAC 策略规则的用户属性条件」「ABAC 策略
 * 规则的时间窗口条件」三个 Requirement 下的全部 Scenario；其中"多条属性条件要求同时
 * 满足"（{@code AbacUserAttrEvaluatorTest}）、"多条时间窗口满足任意一条即可"
 * （{@code AbacTimeWindowEvaluatorTest}）、"停用中的策略不参与运行时判定"
 * （{@code AbacAuthorizationServiceImplTest}）在对应的运行时判定组件测试中覆盖，不在
 * 本测试类重复。
 */
@ExtendWith(MockitoExtension.class)
class AbacPolicyServiceImplTest {

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

    /** 被测服务的元数据字段数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private MetadataFieldMapper metadataFieldMapper;

    /** 被测服务的当前登录操作人用户 id 解析依赖，使用 Mockito 打桩。 */
    @Mock
    private CurrentOperatorService currentOperatorService;

    /** 被测服务实例。 */
    private AbacPolicyServiceImpl abacPolicyService;

    /**
     * 每个用例执行前重新构造被测服务，并让 {@code abacPolicyMapper.insert} 模拟自增主键
     * 回填。
     */
    @BeforeEach
    void setUp() {
        abacPolicyService = new AbacPolicyServiceImpl(abacPolicyMapper, abacPolicyResourceMapper,
                abacPolicyUserAttrMapper, abacPolicyTimeWindowMapper, metadataFieldMapper, currentOperatorService);
        lenient().when(currentOperatorService.resolveUserId()).thenReturn(1L);
        lenient().doAnswer(invocation -> {
            AbacPolicyEntity entity = invocation.getArgument(0);
            entity.setId(100L);
            return 1;
        }).when(abacPolicyMapper).insert(any(AbacPolicyEntity.class));
        lenient().when(abacPolicyResourceMapper.selectList(any())).thenReturn(List.of());
        lenient().when(abacPolicyUserAttrMapper.selectByPolicyId(anyLong())).thenReturn(List.of());
        lenient().when(abacPolicyTimeWindowMapper.selectList(any())).thenReturn(List.of());
    }

    /**
     * 仅配置用户属性条件时应能成功保存，且之后查询该策略能看到这条记录（spec.md"成功
     * 创建仅含用户属性条件的策略规则" Scenario）。
     */
    @Test
    void create_shouldSucceed_whenOnlyUserAttrConfigured() {
        when(metadataFieldMapper.selectById(1L)).thenReturn(buildUserField(1L));
        AbacPolicyCreateRequest request = userAttrOnlyRequest();

        AbacPolicyVO vo = abacPolicyService.create(request);

        assertThat(vo.getId()).isEqualTo(100L);
        ArgumentCaptor<AbacPolicyUserAttrEntity> captor = ArgumentCaptor.forClass(AbacPolicyUserAttrEntity.class);
        verify(abacPolicyUserAttrMapper).insert(captor.capture());
        assertThat(captor.getValue().getAttrValue()).isEqualTo("财务部");
        verify(abacPolicyTimeWindowMapper, never()).insert(any(AbacPolicyTimeWindowEntity.class));
    }

    /**
     * 仅配置时间窗口条件时应能成功保存，不限定用户属性（spec.md"成功创建仅含时间窗口
     * 条件的策略规则" Scenario）。
     */
    @Test
    void create_shouldSucceed_whenOnlyTimeWindowConfigured() {
        AbacPolicyCreateRequest request = timeWindowOnlyRequest();

        AbacPolicyVO vo = abacPolicyService.create(request);

        assertThat(vo.getId()).isEqualTo(100L);
        ArgumentCaptor<AbacPolicyTimeWindowEntity> captor = ArgumentCaptor.forClass(AbacPolicyTimeWindowEntity.class);
        verify(abacPolicyTimeWindowMapper).insert(captor.capture());
        assertThat(captor.getValue().getStartTime()).isEqualTo(LocalTime.of(18, 0));
        verify(abacPolicyUserAttrMapper, never()).insert(any(AbacPolicyUserAttrEntity.class));
    }

    /**
     * 用户属性条件、时间窗口条件同时为空时应拒绝保存（spec.md"用户属性条件与时间窗口
     * 条件同时为空时拒绝保存" Scenario）。
     */
    @Test
    void create_shouldReject_whenUserAttrAndTimeWindowBothEmpty() {
        AbacPolicyCreateRequest request = userAttrOnlyRequest();
        request.setUserAttrs(List.of());

        assertThatThrownBy(() -> abacPolicyService.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能同时为空");
        verify(abacPolicyMapper, never()).insert(any(AbacPolicyEntity.class));
    }

    /**
     * 目标资源编码为空时应拒绝保存（spec.md"目标资源编码为空时拒绝保存" Scenario）。
     */
    @Test
    void create_shouldReject_whenResourceCodesEmpty() {
        AbacPolicyCreateRequest request = userAttrOnlyRequest();
        request.setResourceCodes(List.of());

        assertThatThrownBy(() -> abacPolicyService.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("目标资源编码不能为空");
        verify(abacPolicyMapper, never()).insert(any(AbacPolicyEntity.class));
    }

    /**
     * 编辑策略规则的目标资源编码应采用整体替换语义：已绑定两个资源编码，编辑时只提交其中
     * 一个并新增一个不同的，保存后应先清空原有绑定再按本次提交重新写入（spec.md"编辑策略
     * 规则的目标资源编码采用整体替换" Scenario）。
     */
    @Test
    void update_shouldReplaceResourceCodesEntirely() {
        AbacPolicyEntity existing = AbacPolicyEntity.builder().id(100L).name("旧名称").status(2000).showOrder(0).build();
        when(abacPolicyMapper.selectById(100L)).thenReturn(existing);

        AbacPolicyUpdateRequest request = new AbacPolicyUpdateRequest();
        request.setName("新名称");
        request.setResourceCodes(List.of("OrgManagement:org:edit", "OrgManagement:org:delete"));
        AbacPolicyUserAttrRequestItem userAttr = new AbacPolicyUserAttrRequestItem();
        userAttr.setMetadataFieldId(1L);
        userAttr.setOperator(AbacAttrOperator.EQ);
        userAttr.setValues(List.of("财务部"));
        request.setUserAttrs(List.of(userAttr));
        when(metadataFieldMapper.selectById(1L)).thenReturn(buildUserField(1L));

        abacPolicyService.update(100L, request);

        verify(abacPolicyResourceMapper).delete(any());
        ArgumentCaptor<AbacPolicyResourceEntity> captor = ArgumentCaptor.forClass(AbacPolicyResourceEntity.class);
        verify(abacPolicyResourceMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        List<String> insertedCodes = captor.getAllValues().stream().map(AbacPolicyResourceEntity::getPermissionCode)
                .toList();
        assertThat(insertedCodes).containsExactly("OrgManagement:org:edit", "OrgManagement:org:delete");
    }

    /**
     * 引用非用户业务类型的元数据字段时应拒绝保存（spec.md"引用非用户业务类型的元数据
     * 字段被拒绝" Scenario）。
     */
    @Test
    void create_shouldReject_whenAttrFieldNotUserBizType() {
        MetadataFieldEntity orgField = buildUserField(1L);
        orgField.setBizType(FormFieldBizType.ORG);
        when(metadataFieldMapper.selectById(1L)).thenReturn(orgField);

        AbacPolicyCreateRequest request = userAttrOnlyRequest();

        assertThatThrownBy(() -> abacPolicyService.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不属于用户业务域");
        verify(abacPolicyMapper, never()).insert(any(AbacPolicyEntity.class));
    }

    /**
     * 引用已停用的元数据字段时应拒绝保存（spec.md"引用已停用的元数据字段被拒绝"
     * Scenario）。
     */
    @Test
    void create_shouldReject_whenAttrFieldDisabled() {
        MetadataFieldEntity disabledField = buildUserField(1L);
        disabledField.setStatus(MetadataFieldStatus.DISABLED);
        when(metadataFieldMapper.selectById(1L)).thenReturn(disabledField);

        AbacPolicyCreateRequest request = userAttrOnlyRequest();

        assertThatThrownBy(() -> abacPolicyService.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不存在或未启用");
        verify(abacPolicyMapper, never()).insert(any(AbacPolicyEntity.class));
    }

    /**
     * 同一策略内属性条件关联同一个元数据字段时应拒绝保存（spec.md"同一策略内属性字段
     * 不允许重复" Scenario）。
     */
    @Test
    void create_shouldReject_whenDuplicateAttrField() {
        when(metadataFieldMapper.selectById(1L)).thenReturn(buildUserField(1L));

        AbacPolicyCreateRequest request = userAttrOnlyRequest();
        AbacPolicyUserAttrRequestItem duplicate = new AbacPolicyUserAttrRequestItem();
        duplicate.setMetadataFieldId(1L);
        duplicate.setOperator(AbacAttrOperator.EQ);
        duplicate.setValues(List.of("研发部"));
        request.setUserAttrs(List.of(request.getUserAttrs().get(0), duplicate));

        assertThatThrownBy(() -> abacPolicyService.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能重复配置");
        verify(abacPolicyMapper, never()).insert(any(AbacPolicyEntity.class));
    }

    /**
     * 成功配置指定星期的时间窗口（spec.md"成功配置指定星期的时间窗口" Scenario）。
     */
    @Test
    void create_shouldPersistDayOfWeek_whenTimeWindowSpecifiesDay() {
        AbacPolicyCreateRequest request = timeWindowOnlyRequest();
        request.getTimeWindows().get(0).setDayOfWeek(6);
        request.getTimeWindows().get(0).setStartTime(LocalTime.MIDNIGHT);
        request.getTimeWindows().get(0).setEndTime(LocalTime.of(23, 59, 59));

        abacPolicyService.create(request);

        ArgumentCaptor<AbacPolicyTimeWindowEntity> captor = ArgumentCaptor.forClass(AbacPolicyTimeWindowEntity.class);
        verify(abacPolicyTimeWindowMapper).insert(captor.capture());
        assertThat(captor.getValue().getDayOfWeek()).isEqualTo(6);
    }

    /**
     * 成功配置不指定星期（每天均适用）的时间窗口（spec.md"成功配置每天适用的时间窗口"
     * Scenario）。
     */
    @Test
    void create_shouldPersistNullDayOfWeek_whenTimeWindowAppliesEveryDay() {
        AbacPolicyCreateRequest request = timeWindowOnlyRequest();

        abacPolicyService.create(request);

        ArgumentCaptor<AbacPolicyTimeWindowEntity> captor = ArgumentCaptor.forClass(AbacPolicyTimeWindowEntity.class);
        verify(abacPolicyTimeWindowMapper).insert(captor.capture());
        assertThat(captor.getValue().getDayOfWeek()).isNull();
    }

    /**
     * 时间窗口结束时间早于或等于起始时间时应拒绝保存（spec.md"结束时间早于或等于起始
     * 时间时拒绝保存" Scenario）。
     */
    @Test
    void create_shouldReject_whenTimeWindowEndNotAfterStart() {
        AbacPolicyCreateRequest request = timeWindowOnlyRequest();
        request.getTimeWindows().get(0).setStartTime(LocalTime.of(18, 0));
        request.getTimeWindows().get(0).setEndTime(LocalTime.of(9, 0));

        assertThatThrownBy(() -> abacPolicyService.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("结束时间必须晚于起始时间");
        verify(abacPolicyMapper, never()).insert(any(AbacPolicyEntity.class));
    }

    /**
     * 构造一条仅含用户属性条件的新增请求。
     *
     * @return 新增请求
     */
    private AbacPolicyCreateRequest userAttrOnlyRequest() {
        AbacPolicyCreateRequest request = new AbacPolicyCreateRequest();
        request.setName("非本部门禁止编辑");
        request.setResourceCodes(List.of("OrgManagement:org:edit"));
        AbacPolicyUserAttrRequestItem userAttr = new AbacPolicyUserAttrRequestItem();
        userAttr.setMetadataFieldId(1L);
        userAttr.setOperator(AbacAttrOperator.NE);
        userAttr.setValues(List.of("财务部"));
        request.setUserAttrs(List.of(userAttr));
        return request;
    }

    /**
     * 构造一条仅含时间窗口条件的新增请求。
     *
     * @return 新增请求
     */
    private AbacPolicyCreateRequest timeWindowOnlyRequest() {
        AbacPolicyCreateRequest request = new AbacPolicyCreateRequest();
        request.setName("非工作时间禁止导出");
        request.setResourceCodes(List.of("ExcelExport:export:execute"));
        AbacPolicyTimeWindowRequestItem timeWindow = new AbacPolicyTimeWindowRequestItem();
        timeWindow.setStartTime(LocalTime.of(18, 0));
        timeWindow.setEndTime(LocalTime.of(23, 59, 59));
        request.setTimeWindows(List.of(timeWindow));
        return request;
    }

    /**
     * 构造一个测试用的 {@code biz_type=USER} 元数据字段实体。
     *
     * @param id 元数据字段 id
     * @return 元数据字段实体
     */
    private MetadataFieldEntity buildUserField(Long id) {
        return MetadataFieldEntity.builder()
                .id(id)
                .bizType(FormFieldBizType.USER)
                .tableName("tab_user")
                .columnName("dept")
                .columnType("VARCHAR(64)")
                .fieldCode("dept")
                .fieldName("部门")
                .status(MetadataFieldStatus.ENABLED)
                .build();
    }
}
