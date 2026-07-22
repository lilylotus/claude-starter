package cn.nihility.rbac.formfield.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.dict.mapper.DictTypeMapper;
import cn.nihility.rbac.dict.service.DictItemService;
import cn.nihility.rbac.formfield.constant.FormFieldControlType;
import cn.nihility.rbac.formfield.constant.FormFieldStatus;
import cn.nihility.rbac.formfield.dto.FormFieldDefinitionCreateRequest;
import cn.nihility.rbac.formfield.dto.FormFieldDefinitionUpdateRequest;
import cn.nihility.rbac.formfield.dto.FormFieldDefinitionVO;
import cn.nihility.rbac.formfield.entity.FormFieldDefinitionEntity;
import cn.nihility.rbac.formfield.exception.DictTypeRequiredException;
import cn.nihility.rbac.formfield.exception.FieldCodeDuplicateException;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link FormFieldDefinitionServiceImpl} 的单元测试，重点覆盖绑定关系校验（可用性/
 * 互斥占用）、fieldCode 唯一性、字典下拉的 dictTypeId 必填性、承重字段（锁定字段）
 * 的停用/删除/放松保护，以及 {@code listActiveByBizType} 对锁定字段的过滤。
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
        lenient().when(formFieldDefinitionMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
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
     * 创建字段定义时，若 fieldCode 在同一业务对象类型下已被其他有效定义占用，应拒绝创建。
     */
    @Test
    void create_shouldThrowException_whenFieldCodeDuplicate() {
        when(metadataFieldMapper.selectById(1L))
                .thenReturn(buildMetadataEntity(1L, "USER", "ext1", MetadataFieldStatus.ENABLED));
        when(formFieldDefinitionMapper.existsActiveByMetadataFieldId(1L)).thenReturn(false);
        when(formFieldDefinitionMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> formFieldDefinitionService.create(buildCreateRequest(1L, FormFieldControlType.TEXT)))
                .isInstanceOf(FieldCodeDuplicateException.class);
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
        request.setFieldCode("idCardNo");
        request.setControlType(controlType);
        if (controlType == FormFieldControlType.DICT) {
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
        request.setFieldCode("code");
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
