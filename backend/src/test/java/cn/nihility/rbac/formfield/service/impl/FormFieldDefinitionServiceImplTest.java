package cn.nihility.rbac.formfield.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.dict.dto.DictItemOptionVO;
import cn.nihility.rbac.dict.entity.DictTypeEntity;
import cn.nihility.rbac.dict.mapper.DictTypeMapper;
import cn.nihility.rbac.dict.service.DictItemService;
import cn.nihility.rbac.formfield.constant.FormFieldControlType;
import cn.nihility.rbac.formfield.constant.FormFieldStatus;
import cn.nihility.rbac.formfield.dto.FormFieldDefinitionCreateRequest;
import cn.nihility.rbac.formfield.dto.FormFieldDefinitionUpdateRequest;
import cn.nihility.rbac.formfield.dto.FormFieldDefinitionVO;
import cn.nihility.rbac.formfield.dto.FormFieldRenderItemVO;
import cn.nihility.rbac.formfield.entity.FormFieldDefinitionEntity;
import cn.nihility.rbac.formfield.exception.DictTypeRequiredException;
import cn.nihility.rbac.formfield.exception.LockedFormFieldException;
import cn.nihility.rbac.formfield.exception.MetadataFieldAlreadyBoundException;
import cn.nihility.rbac.formfield.exception.MetadataFieldUnavailableException;
import cn.nihility.rbac.formfield.mapper.FormFieldDefinitionMapper;
import cn.nihility.rbac.metadata.constant.MetadataFieldStatus;
import cn.nihility.rbac.metadata.entity.MetadataFieldEntity;
import cn.nihility.rbac.metadata.mapper.MetadataFieldMapper;
import cn.nihility.rbac.operationlog.service.OperationLogRecorder;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link FormFieldDefinitionServiceImpl} 的单元测试，重点覆盖绑定关系校验（可用性/
 * 互斥占用）、fieldCode 派生自绑定的元数据字段（创建取值、改绑刷新、读取实时回填）、
 * 字典下拉的 dictTypeId 必填性、承重字段（锁定字段）的停用/删除/放松保护，以及
 * {@code listActiveByBizType} 对锁定字段的过滤。
 */
@ExtendWith(MockitoExtension.class)
class FormFieldDefinitionServiceImplTest {

    /** 被测服务的表单字段定义数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private FormFieldDefinitionMapper formFieldDefinitionMapper;

    /** 被测服务的元数据字段数据访问依赖，直接跨模块注入，使用 Mockito 打桩。 */
    @Mock
    private MetadataFieldMapper metadataFieldMapper;

    /** 被测服务的字典类型数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private DictTypeMapper dictTypeMapper;

    /** 被测服务的字典项业务逻辑依赖，使用 Mockito 打桩。 */
    @Mock
    private DictItemService dictItemService;

    /** 被测服务的操作日志记录组件依赖，使用 Mockito 打桩。 */
    @Mock
    private OperationLogRecorder operationLogRecorder;

    /** 被测服务实例。 */
    private FormFieldDefinitionServiceImpl formFieldDefinitionService;

    /**
     * 每个用例执行前重新构造被测服务；实体/DTO 转换通过
     * {@code FormFieldDefinitionConvert.INSTANCE} 静态调用完成，无需在此注入或 mock。
     */
    @BeforeEach
    void setUp() {
        formFieldDefinitionService = new FormFieldDefinitionServiceImpl(formFieldDefinitionMapper,
                metadataFieldMapper, dictTypeMapper, dictItemService, operationLogRecorder);
    }

    /**
     * 创建字段定义时，若绑定的元数据字段不存在，应拒绝创建。
     */
    @Test
    void create_shouldThrowException_whenMetadataFieldNotFound() {
        when(metadataFieldMapper.selectById(1L)).thenReturn(null);

        assertThatThrownBy(() -> formFieldDefinitionService.create(buildCreateRequest(1L, FormFieldControlType.TEXT)))
                .isInstanceOf(MetadataFieldUnavailableException.class);
    }

    /**
     * 创建字段定义时，若绑定的元数据字段状态非启用，应拒绝创建。
     */
    @Test
    void create_shouldThrowException_whenMetadataFieldDisabled() {
        when(metadataFieldMapper.selectById(1L))
                .thenReturn(buildMetadataEntity(1L, "USER", "ext1", MetadataFieldStatus.DISABLED));

        assertThatThrownBy(() -> formFieldDefinitionService.create(buildCreateRequest(1L, FormFieldControlType.TEXT)))
                .isInstanceOf(MetadataFieldUnavailableException.class);
    }

    /**
     * 创建字段定义时，若绑定的元数据字段已被其他有效定义占用，应拒绝创建。
     */
    @Test
    void create_shouldThrowException_whenMetadataFieldAlreadyBound() {
        when(metadataFieldMapper.selectById(1L))
                .thenReturn(buildMetadataEntity(1L, "USER", "ext1", MetadataFieldStatus.ENABLED));
        when(formFieldDefinitionMapper.existsActiveByMetadataFieldId(1L)).thenReturn(true);

        assertThatThrownBy(() -> formFieldDefinitionService.create(buildCreateRequest(1L, FormFieldControlType.TEXT)))
                .isInstanceOf(MetadataFieldAlreadyBoundException.class);
    }

    /**
     * 创建字段定义时，控件类型为下拉单选字典但未提供 dictTypeId，应拒绝创建。
     */
    @Test
    void create_shouldThrowException_whenDictControlTypeWithoutDictTypeId() {
        when(metadataFieldMapper.selectById(1L))
                .thenReturn(buildMetadataEntity(1L, "USER", "ext1", MetadataFieldStatus.ENABLED));
        when(formFieldDefinitionMapper.existsActiveByMetadataFieldId(1L)).thenReturn(false);

        FormFieldDefinitionCreateRequest request = buildCreateRequest(1L, FormFieldControlType.DICT);
        request.setDictTypeId(null);

        assertThatThrownBy(() -> formFieldDefinitionService.create(request))
                .isInstanceOf(DictTypeRequiredException.class);
    }

    /**
     * 创建字段定义时，控件类型为多选字典下拉但未提供 dictTypeId，应拒绝创建，
     * 与下拉单选字典的校验规则一致。
     */
    @Test
    void create_shouldThrowException_whenMultiDictControlTypeWithoutDictTypeId() {
        when(metadataFieldMapper.selectById(1L))
                .thenReturn(buildMetadataEntity(1L, "USER", "ext1", MetadataFieldStatus.ENABLED));
        when(formFieldDefinitionMapper.existsActiveByMetadataFieldId(1L)).thenReturn(false);

        FormFieldDefinitionCreateRequest request = buildCreateRequest(1L, FormFieldControlType.MULTI_DICT);
        request.setDictTypeId(null);

        assertThatThrownBy(() -> formFieldDefinitionService.create(request))
                .isInstanceOf(DictTypeRequiredException.class);
    }

    /**
     * 创建字段定义时，控件类型为日期，即使未提供 dictTypeId 也应正常创建
     * （日期类型不依赖字典类型）。
     */
    @Test
    void create_shouldSucceed_whenDateControlTypeWithoutDictTypeId() {
        MetadataFieldEntity metadata = buildMetadataEntity(1L, "USER", "ext1", MetadataFieldStatus.ENABLED);
        when(metadataFieldMapper.selectById(1L)).thenReturn(metadata);
        when(formFieldDefinitionMapper.existsActiveByMetadataFieldId(1L)).thenReturn(false);
        lenient().when(metadataFieldMapper.selectByIds(any())).thenReturn(List.of(metadata));
        lenient().when(formFieldDefinitionMapper.selectById(any()))
                .thenReturn(buildDefinitionEntity(99L, "USER", 1L, "birthday", FormFieldStatus.ENABLED));

        FormFieldDefinitionCreateRequest request = buildCreateRequest(1L, FormFieldControlType.DATE);
        request.setDictTypeId(null);

        formFieldDefinitionService.create(request);

        ArgumentCaptor<FormFieldDefinitionEntity> captor =
                ArgumentCaptor.forClass(FormFieldDefinitionEntity.class);
        Mockito.verify(formFieldDefinitionMapper).insert(captor.capture());
        assertThat(captor.getValue().getControlType()).isEqualTo(FormFieldControlType.DATE);
        assertThat(captor.getValue().getDictTypeId()).isNull();
    }

    /**
     * 创建一条控件类型为日期的字段定义时，写入操作日志的字段快照中"控件类型"
     * 应展示为中文文案"日期"。
     */
    @Test
    void create_shouldRecordDateControlTypeLabel_inOperationLog() {
        MetadataFieldEntity metadata = buildMetadataEntity(1L, "USER", "ext1", MetadataFieldStatus.ENABLED);
        when(metadataFieldMapper.selectById(1L)).thenReturn(metadata);
        when(formFieldDefinitionMapper.existsActiveByMetadataFieldId(1L)).thenReturn(false);
        lenient().when(metadataFieldMapper.selectByIds(any())).thenReturn(List.of(metadata));
        lenient().when(formFieldDefinitionMapper.selectById(any()))
                .thenReturn(buildDefinitionEntity(99L, "USER", 1L, "birthday", FormFieldStatus.ENABLED));

        formFieldDefinitionService.create(buildCreateRequest(1L, FormFieldControlType.DATE));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        Mockito.verify(operationLogRecorder).recordCreate(any(), any(), any(), captor.capture());
        assertThat(captor.getValue().get("控件类型")).isEqualTo("日期");
    }

    /**
     * 创建一条控件类型为多选字典下拉的字段定义时，写入操作日志的字段快照中
     * "控件类型"应展示为中文文案"多选字典下拉"。
     */
    @Test
    void create_shouldRecordMultiDictControlTypeLabel_inOperationLog() {
        MetadataFieldEntity metadata = buildMetadataEntity(1L, "USER", "ext1", MetadataFieldStatus.ENABLED);
        when(metadataFieldMapper.selectById(1L)).thenReturn(metadata);
        when(formFieldDefinitionMapper.existsActiveByMetadataFieldId(1L)).thenReturn(false);
        when(dictTypeMapper.selectById(1L))
                .thenReturn(DictTypeEntity.builder().id(1L).code("tag_type").name("标签类型").build());
        lenient().when(metadataFieldMapper.selectByIds(any())).thenReturn(List.of(metadata));
        lenient().when(formFieldDefinitionMapper.selectById(any()))
                .thenReturn(buildDefinitionEntity(99L, "USER", 1L, "tags", FormFieldStatus.ENABLED));

        formFieldDefinitionService.create(buildCreateRequest(1L, FormFieldControlType.MULTI_DICT));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        Mockito.verify(operationLogRecorder).recordCreate(any(), any(), any(), captor.capture());
        assertThat(captor.getValue().get("控件类型")).isEqualTo("多选字典下拉");
    }

    /**
     * 动态字段渲染元数据接口对控件类型为多选字典下拉的定义，应内嵌其关联字典
     * 类型下的启用字典项，与下拉单选字典的行为一致。
     */
    @Test
    void buildRenderSchema_shouldIncludeDictOptions_whenMultiDictControlType() {
        FormFieldDefinitionEntity entity = buildDefinitionEntity(20L, "USER", 1L, "tags", FormFieldStatus.ENABLED);
        entity.setControlType(FormFieldControlType.MULTI_DICT);
        entity.setDictTypeId(5L);
        when(formFieldDefinitionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(entity));
        when(metadataFieldMapper.selectByIds(any()))
                .thenReturn(List.of(buildMetadataEntity(1L, "USER", "ext1", MetadataFieldStatus.ENABLED)));
        DictTypeEntity dictType = DictTypeEntity.builder().id(5L).code("tag_type").name("标签类型").build();
        when(dictTypeMapper.selectByIds(any())).thenReturn(List.of(dictType));
        when(dictTypeMapper.selectById(5L)).thenReturn(dictType);
        when(dictItemService.getEnabledOptions("tag_type")).thenReturn(
                List.of(DictItemOptionVO.builder().code("A").label("标签A").showOrder(0).build()));

        List<FormFieldRenderItemVO> result = formFieldDefinitionService.buildRenderSchema("USER");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDictOptions()).hasSize(1);
        assertThat(result.get(0).getDictOptions().get(0).getLabel()).isEqualTo("标签A");
        assertThat(result.get(0).getDictOptions().get(0).getValue()).isEqualTo("A");
    }

    /**
     * 动态字段渲染元数据接口对控件类型为日期的定义，不应内嵌 {@code dictOptions}。
     */
    @Test
    void buildRenderSchema_shouldExcludeDictOptions_whenDateControlType() {
        FormFieldDefinitionEntity entity = buildDefinitionEntity(21L, "USER", 1L, "birthday",
                FormFieldStatus.ENABLED);
        entity.setControlType(FormFieldControlType.DATE);
        when(formFieldDefinitionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(entity));
        when(metadataFieldMapper.selectByIds(any()))
                .thenReturn(List.of(buildMetadataEntity(1L, "USER", "ext1", MetadataFieldStatus.ENABLED)));

        List<FormFieldRenderItemVO> result = formFieldDefinitionService.buildRenderSchema("USER");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDictOptions()).isEmpty();
    }

    /**
     * 创建字段定义成功时，{@code bizType} 应取自所绑定元数据字段，而不是请求参数。
     */
    @Test
    void create_shouldSetBizTypeFromMetadataField() {
        MetadataFieldEntity metadata = buildMetadataEntity(1L, "USER", "ext1", MetadataFieldStatus.ENABLED);
        when(metadataFieldMapper.selectById(1L)).thenReturn(metadata);
        when(formFieldDefinitionMapper.existsActiveByMetadataFieldId(1L)).thenReturn(false);
        lenient().when(metadataFieldMapper.selectByIds(any())).thenReturn(List.of(metadata));
        // create() 最后调用 getById 回查详情，桩住 selectById 让这次回查能正常返回。
        lenient().when(formFieldDefinitionMapper.selectById(any()))
                .thenReturn(buildDefinitionEntity(99L, "USER", 1L, "idCardNo", FormFieldStatus.ENABLED));

        formFieldDefinitionService.create(buildCreateRequest(1L, FormFieldControlType.TEXT));

        org.mockito.ArgumentCaptor<FormFieldDefinitionEntity> captor =
                org.mockito.ArgumentCaptor.forClass(FormFieldDefinitionEntity.class);
        org.mockito.Mockito.verify(formFieldDefinitionMapper).insert(captor.capture());
        assertThat(captor.getValue().getBizType()).isEqualTo("USER");
        assertThat(captor.getValue().getStatus()).isEqualTo(FormFieldStatus.ENABLED);
    }

    /**
     * 创建字段定义成功时，{@code fieldCode} 应完全派生自所绑定的元数据字段，即使请求
     * 本身不再提交 {@code fieldCode}（创建请求 DTO 已不包含该字段）。
     */
    @Test
    void create_shouldDeriveFieldCodeFromMetadataField() {
        MetadataFieldEntity metadata = MetadataFieldEntity.builder()
                .id(1L)
                .bizType("USER")
                .tableName("tab_user")
                .columnName("ext1")
                .columnType("VARCHAR(255)")
                .fieldCode("idCardNo")
                .fieldName("身份证号")
                .status(MetadataFieldStatus.ENABLED)
                .build();
        when(metadataFieldMapper.selectById(1L)).thenReturn(metadata);
        when(formFieldDefinitionMapper.existsActiveByMetadataFieldId(1L)).thenReturn(false);
        lenient().when(metadataFieldMapper.selectByIds(any())).thenReturn(List.of(metadata));
        lenient().when(formFieldDefinitionMapper.selectById(any()))
                .thenReturn(buildDefinitionEntity(99L, "USER", 1L, "idCardNo", FormFieldStatus.ENABLED));

        formFieldDefinitionService.create(buildCreateRequest(1L, FormFieldControlType.TEXT));

        org.mockito.ArgumentCaptor<FormFieldDefinitionEntity> captor =
                org.mockito.ArgumentCaptor.forClass(FormFieldDefinitionEntity.class);
        org.mockito.Mockito.verify(formFieldDefinitionMapper).insert(captor.capture());
        assertThat(captor.getValue().getFieldCode()).isEqualTo("idCardNo");
    }

    /**
     * 更新一条绑定承重字段（{@code name}/{@code code}）的定义时，若尝试把
     * {@code isRequired} 改为 {@code false}，应拒绝该次更新。
     */
    @Test
    void update_shouldThrowException_whenLockedDefinitionSetIsRequiredFalse() {
        FormFieldDefinitionEntity entity = buildDefinitionEntity(10L, "ORG", 1L, "code", FormFieldStatus.ENABLED);
        when(formFieldDefinitionMapper.selectById(10L)).thenReturn(entity);
        when(metadataFieldMapper.selectById(1L))
                .thenReturn(buildMetadataEntity(1L, "ORG", "code", MetadataFieldStatus.ENABLED));

        FormFieldDefinitionUpdateRequest request = buildUpdateRequest();
        request.setIsRequired(false);

        assertThatThrownBy(() -> formFieldDefinitionService.update(10L, request))
                .isInstanceOf(LockedFormFieldException.class);
    }

    /**
     * 更新一条绑定承重字段的定义时，仅调整展示名称等非受限属性应正常保存。
     */
    @Test
    void update_shouldSucceed_whenLockedDefinitionOnlyChangesDisplayName() {
        FormFieldDefinitionEntity entity = buildDefinitionEntity(10L, "ORG", 1L, "code", FormFieldStatus.ENABLED);
        when(formFieldDefinitionMapper.selectById(10L)).thenReturn(entity);
        when(metadataFieldMapper.selectById(1L))
                .thenReturn(buildMetadataEntity(1L, "ORG", "code", MetadataFieldStatus.ENABLED));

        FormFieldDefinitionUpdateRequest request = buildUpdateRequest();
        request.setFieldName("组织编码（新展示名）");

        formFieldDefinitionService.update(10L, request);

        assertThat(entity.getFieldName()).isEqualTo("组织编码（新展示名）");
    }

    /**
     * 更新一条非锁定定义时，若请求体 {@code metadataFieldId} 与当前值相同，
     * 应视为不改绑，不触发改绑校验，正常保存其余属性。
     */
    @Test
    void update_shouldNotTriggerRebindCheck_whenMetadataFieldIdUnchanged() {
        FormFieldDefinitionEntity entity = buildDefinitionEntity(10L, "ORG", 1L, "remark", FormFieldStatus.ENABLED);
        when(formFieldDefinitionMapper.selectById(10L)).thenReturn(entity);
        when(metadataFieldMapper.selectById(1L))
                .thenReturn(buildMetadataEntity(1L, "ORG", "remark", MetadataFieldStatus.ENABLED));

        FormFieldDefinitionUpdateRequest request = buildUpdateRequest();
        request.setMetadataFieldId(1L);
        request.setFieldName("备注（新）");

        formFieldDefinitionService.update(10L, request);

        assertThat(entity.getMetadataFieldId()).isEqualTo(1L);
        assertThat(entity.getFieldName()).isEqualTo("备注（新）");
    }

    /**
     * 更新一条非锁定定义时，若改绑到同一 bizType 下另一个启用且未被占用的元数据
     * 字段，应改绑成功，且 {@code fieldCode} 同步刷新为新绑定元数据字段的当前值。
     */
    @Test
    void update_shouldRebind_whenNonLockedDefinitionTargetsAvailableMetadataField() {
        FormFieldDefinitionEntity entity = buildDefinitionEntity(10L, "ORG", 1L, "remark", FormFieldStatus.ENABLED);
        when(formFieldDefinitionMapper.selectById(10L)).thenReturn(entity);
        when(metadataFieldMapper.selectById(1L))
                .thenReturn(buildMetadataEntity(1L, "ORG", "remark", MetadataFieldStatus.ENABLED));
        MetadataFieldEntity rebindTarget = MetadataFieldEntity.builder()
                .id(2L)
                .bizType("ORG")
                .tableName("tab_org")
                .columnName("ext1")
                .columnType("VARCHAR(255)")
                .fieldCode("ext1Code")
                .fieldName("ext1")
                .status(MetadataFieldStatus.ENABLED)
                .build();
        when(metadataFieldMapper.selectById(2L)).thenReturn(rebindTarget);
        when(formFieldDefinitionMapper.existsActiveByMetadataFieldIdExcluding(2L, 10L)).thenReturn(false);

        FormFieldDefinitionUpdateRequest request = buildUpdateRequest();
        request.setMetadataFieldId(2L);

        formFieldDefinitionService.update(10L, request);

        assertThat(entity.getMetadataFieldId()).isEqualTo(2L);
        assertThat(entity.getFieldCode()).isEqualTo("ext1Code");
    }

    /**
     * 查询字段定义详情时，{@code fieldCode} 应实时取自当前绑定的元数据字段，而不是
     * 实体上落库时的旧值——即元数据字段的 {@code fieldCode} 被单独编辑后，下一次
     * 查询表单字段定义应立刻看到最新值。
     */
    @Test
    void getById_shouldReflectLatestFieldCodeFromMetadataField() {
        FormFieldDefinitionEntity entity = buildDefinitionEntity(10L, "ORG", 1L, "oldCode", FormFieldStatus.ENABLED);
        MetadataFieldEntity metadata = MetadataFieldEntity.builder()
                .id(1L)
                .bizType("ORG")
                .tableName("tab_org")
                .columnName("remark")
                .columnType("VARCHAR(255)")
                .fieldCode("newCode")
                .fieldName("备注")
                .status(MetadataFieldStatus.ENABLED)
                .build();
        when(formFieldDefinitionMapper.selectById(10L)).thenReturn(entity);
        when(metadataFieldMapper.selectByIds(any())).thenReturn(List.of(metadata));

        FormFieldDefinitionVO vo = formFieldDefinitionService.getById(10L);

        assertThat(vo.getFieldCode()).isEqualTo("newCode");
    }

    /**
     * 改绑目标已被其他有效定义占用时，应拒绝该次更新。
     */
    @Test
    void update_shouldThrowException_whenRebindTargetAlreadyBound() {
        FormFieldDefinitionEntity entity = buildDefinitionEntity(10L, "ORG", 1L, "remark", FormFieldStatus.ENABLED);
        when(formFieldDefinitionMapper.selectById(10L)).thenReturn(entity);
        when(metadataFieldMapper.selectById(1L))
                .thenReturn(buildMetadataEntity(1L, "ORG", "remark", MetadataFieldStatus.ENABLED));
        when(metadataFieldMapper.selectById(2L))
                .thenReturn(buildMetadataEntity(2L, "ORG", "ext1", MetadataFieldStatus.ENABLED));
        when(formFieldDefinitionMapper.existsActiveByMetadataFieldIdExcluding(2L, 10L)).thenReturn(true);

        FormFieldDefinitionUpdateRequest request = buildUpdateRequest();
        request.setMetadataFieldId(2L);

        assertThatThrownBy(() -> formFieldDefinitionService.update(10L, request))
                .isInstanceOf(MetadataFieldAlreadyBoundException.class);
        assertThat(entity.getMetadataFieldId()).isEqualTo(1L);
    }

    /**
     * 改绑目标不存在或状态非启用时，应拒绝该次更新。
     */
    @Test
    void update_shouldThrowException_whenRebindTargetUnavailable() {
        FormFieldDefinitionEntity entity = buildDefinitionEntity(10L, "ORG", 1L, "remark", FormFieldStatus.ENABLED);
        when(formFieldDefinitionMapper.selectById(10L)).thenReturn(entity);
        when(metadataFieldMapper.selectById(1L))
                .thenReturn(buildMetadataEntity(1L, "ORG", "remark", MetadataFieldStatus.ENABLED));
        when(metadataFieldMapper.selectById(2L)).thenReturn(null);

        FormFieldDefinitionUpdateRequest request = buildUpdateRequest();
        request.setMetadataFieldId(2L);

        assertThatThrownBy(() -> formFieldDefinitionService.update(10L, request))
                .isInstanceOf(MetadataFieldUnavailableException.class);
        assertThat(entity.getMetadataFieldId()).isEqualTo(1L);
    }

    /**
     * 改绑目标的 bizType 与当前定义不同时，应拒绝该次更新。
     */
    @Test
    void update_shouldThrowException_whenRebindTargetBizTypeMismatch() {
        FormFieldDefinitionEntity entity = buildDefinitionEntity(10L, "ORG", 1L, "remark", FormFieldStatus.ENABLED);
        when(formFieldDefinitionMapper.selectById(10L)).thenReturn(entity);
        when(metadataFieldMapper.selectById(1L))
                .thenReturn(buildMetadataEntity(1L, "ORG", "remark", MetadataFieldStatus.ENABLED));
        when(metadataFieldMapper.selectById(2L))
                .thenReturn(buildMetadataEntity(2L, "USER", "ext1", MetadataFieldStatus.ENABLED));

        FormFieldDefinitionUpdateRequest request = buildUpdateRequest();
        request.setMetadataFieldId(2L);

        assertThatThrownBy(() -> formFieldDefinitionService.update(10L, request))
                .isInstanceOf(MetadataFieldUnavailableException.class);
        assertThat(entity.getMetadataFieldId()).isEqualTo(1L);
    }

    /**
     * 更新一条绑定承重字段的定义时，若请求体 {@code metadataFieldId} 与当前值不同，
     * 应拒绝改绑。
     */
    @Test
    void update_shouldThrowException_whenLockedDefinitionRebind() {
        FormFieldDefinitionEntity entity = buildDefinitionEntity(10L, "ORG", 1L, "code", FormFieldStatus.ENABLED);
        when(formFieldDefinitionMapper.selectById(10L)).thenReturn(entity);
        when(metadataFieldMapper.selectById(1L))
                .thenReturn(buildMetadataEntity(1L, "ORG", "code", MetadataFieldStatus.ENABLED));

        FormFieldDefinitionUpdateRequest request = buildUpdateRequest();
        request.setMetadataFieldId(2L);

        assertThatThrownBy(() -> formFieldDefinitionService.update(10L, request))
                .isInstanceOf(LockedFormFieldException.class);
        assertThat(entity.getMetadataFieldId()).isEqualTo(1L);
    }

    /**
     * 停用一条绑定承重字段的定义时，应拒绝停用。
     */
    @Test
    void disable_shouldThrowException_whenDefinitionLocked() {
        FormFieldDefinitionEntity entity = buildDefinitionEntity(10L, "ORG", 1L, "name", FormFieldStatus.ENABLED);
        when(formFieldDefinitionMapper.selectById(10L)).thenReturn(entity);
        when(metadataFieldMapper.selectById(1L))
                .thenReturn(buildMetadataEntity(1L, "ORG", "name", MetadataFieldStatus.ENABLED));

        assertThatThrownBy(() -> formFieldDefinitionService.disable(10L))
                .isInstanceOf(LockedFormFieldException.class);
        assertThat(entity.getStatus()).isEqualTo(FormFieldStatus.ENABLED);
    }

    /**
     * 删除一条绑定承重字段的定义时，应拒绝删除。
     */
    @Test
    void delete_shouldThrowException_whenDefinitionLocked() {
        FormFieldDefinitionEntity entity = buildDefinitionEntity(10L, "USER", 1L, "code", FormFieldStatus.ENABLED);
        when(formFieldDefinitionMapper.selectById(10L)).thenReturn(entity);
        when(metadataFieldMapper.selectById(1L))
                .thenReturn(buildMetadataEntity(1L, "USER", "code", MetadataFieldStatus.ENABLED));

        assertThatThrownBy(() -> formFieldDefinitionService.delete(10L))
                .isInstanceOf(LockedFormFieldException.class);
        assertThat(entity.getStatus()).isEqualTo(FormFieldStatus.ENABLED);
    }

    /**
     * 删除一条未绑定承重字段的定义时，应正常执行逻辑删除。
     */
    @Test
    void delete_shouldSucceed_whenDefinitionNotLocked() {
        FormFieldDefinitionEntity entity = buildDefinitionEntity(10L, "USER", 1L, "ext1", FormFieldStatus.ENABLED);
        when(formFieldDefinitionMapper.selectById(10L)).thenReturn(entity);
        when(metadataFieldMapper.selectById(1L))
                .thenReturn(buildMetadataEntity(1L, "USER", "ext1", MetadataFieldStatus.ENABLED));

        formFieldDefinitionService.delete(10L);

        assertThat(entity.getStatus()).isEqualTo(FormFieldStatus.DELETED);
    }

    /**
     * {@code listActiveByBizType} 应过滤掉承重字段（锁定字段）的定义，只返回可参与
     * 数据驱动校验管线的非锁定定义。
     */
    @Test
    void listActiveByBizType_shouldExcludeLockedDefinitions() {
        FormFieldDefinitionEntity lockedEntity = buildDefinitionEntity(10L, "ORG", 1L, "code",
                FormFieldStatus.ENABLED);
        FormFieldDefinitionEntity unlockedEntity = buildDefinitionEntity(11L, "ORG", 2L, "remark",
                FormFieldStatus.ENABLED);
        when(formFieldDefinitionMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(lockedEntity, unlockedEntity));
        when(metadataFieldMapper.selectByIds(any())).thenReturn(List.of(
                buildMetadataEntity(1L, "ORG", "code", MetadataFieldStatus.ENABLED),
                buildMetadataEntity(2L, "ORG", "remark", MetadataFieldStatus.ENABLED)));

        List<FormFieldDefinitionVO> result = formFieldDefinitionService.listActiveByBizType("ORG");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getColumnName()).isEqualTo("remark");
        assertThat(result.get(0).getLocked()).isFalse();
    }

    /**
     * 构造一条创建请求。
     *
     * @param metadataFieldId 绑定的元数据字段 id
     * @param controlType     控件类型
     * @return 创建请求
     */
    private FormFieldDefinitionCreateRequest buildCreateRequest(long metadataFieldId, int controlType) {
        FormFieldDefinitionCreateRequest request = new FormFieldDefinitionCreateRequest();
        request.setMetadataFieldId(metadataFieldId);
        request.setFieldName("身份证号");
        request.setControlType(controlType);
        if (FormFieldControlType.DICT_TYPES.contains(controlType)) {
            request.setDictTypeId(1L);
        }
        request.setIsUnique(false);
        request.setIsRequired(false);
        request.setShowInList(true);
        request.setShowInCreate(true);
        request.setShowInEdit(true);
        request.setEditable(true);
        request.setShowOrder(0);
        return request;
    }

    /**
     * 构造一条更新请求，默认值均满足承重字段的保护要求（必填/新增/编辑展示均为
     * {@code true}），测试用例按需覆盖单个字段。
     *
     * @return 更新请求
     */
    private FormFieldDefinitionUpdateRequest buildUpdateRequest() {
        FormFieldDefinitionUpdateRequest request = new FormFieldDefinitionUpdateRequest();
        request.setFieldName("组织编码");
        request.setControlType(FormFieldControlType.TEXT);
        request.setIsUnique(true);
        request.setIsRequired(true);
        request.setShowInList(true);
        request.setShowInCreate(true);
        request.setShowInEdit(true);
        request.setEditable(true);
        request.setShowOrder(0);
        return request;
    }

    /**
     * 构造一个测试用的元数据字段实体。
     *
     * @param id         主键 id
     * @param bizType    业务对象类型
     * @param columnName 字段列名
     * @param status     状态
     * @return 元数据字段实体
     */
    private MetadataFieldEntity buildMetadataEntity(long id, String bizType, String columnName, int status) {
        return MetadataFieldEntity.builder()
                .id(id)
                .bizType(bizType)
                .tableName("tab_" + bizType.toLowerCase())
                .columnName(columnName)
                .columnType("VARCHAR(255)")
                .fieldCode(columnName)
                .fieldName(columnName)
                .status(status)
                .build();
    }

    /**
     * 构造一个测试用的表单字段定义实体。
     *
     * @param id              主键 id
     * @param bizType         业务对象类型
     * @param metadataFieldId 绑定的元数据字段 id
     * @param fieldCode       字段标识
     * @param status          状态
     * @return 表单字段定义实体
     */
    private FormFieldDefinitionEntity buildDefinitionEntity(long id, String bizType, long metadataFieldId,
            String fieldCode, int status) {
        return FormFieldDefinitionEntity.builder()
                .id(id)
                .bizType(bizType)
                .metadataFieldId(metadataFieldId)
                .fieldName(fieldCode)
                .fieldCode(fieldCode)
                .controlType(FormFieldControlType.TEXT)
                .isUnique(false)
                .isRequired(true)
                .showInList(true)
                .showInCreate(true)
                .showInEdit(true)
                .editable(true)
                .showOrder(0)
                .status(status)
                .build();
    }
}
