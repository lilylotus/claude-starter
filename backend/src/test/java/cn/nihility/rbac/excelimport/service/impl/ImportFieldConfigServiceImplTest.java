package cn.nihility.rbac.excelimport.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.excelimport.constant.ImportFieldConfigStatus;
import cn.nihility.rbac.excelimport.dto.ImportFieldConfigCreateRequest;
import cn.nihility.rbac.excelimport.dto.ImportFieldConfigUpdateRequest;
import cn.nihility.rbac.excelimport.dto.ImportFieldConfigVO;
import cn.nihility.rbac.excelimport.entity.ImportFieldConfigEntity;
import cn.nihility.rbac.excelimport.exception.LockedImportFieldConfigException;
import cn.nihility.rbac.excelimport.mapper.ImportFieldConfigMapper;
import cn.nihility.rbac.formfield.constant.FormFieldStatus;
import cn.nihility.rbac.formfield.entity.FormFieldDefinitionEntity;
import cn.nihility.rbac.formfield.mapper.FormFieldDefinitionMapper;
import cn.nihility.rbac.operationlog.service.OperationLogRecorder;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link ImportFieldConfigServiceImpl} 的单元测试，重点覆盖：关联表单字段定义时的
 * 可用性/bizType 一致性校验、未关联时字段标识必填、同 bizType 下字段标识唯一性、
 * POSITION 固定标识列（锁定配置）的删除/取消主键/取消必填/改绑保护，以及非锁定配置
 * 的正常改绑。
 */
@ExtendWith(MockitoExtension.class)
class ImportFieldConfigServiceImplTest {

    /** 被测服务的导入字段配置数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private ImportFieldConfigMapper importFieldConfigMapper;

    /** 被测服务的表单字段定义数据访问依赖，直接跨模块注入，使用 Mockito 打桩。 */
    @Mock
    private FormFieldDefinitionMapper formFieldDefinitionMapper;

    /** 被测服务的操作日志记录组件依赖，使用 Mockito 打桩。 */
    @Mock
    private OperationLogRecorder operationLogRecorder;

    /** 被测服务实例。 */
    private ImportFieldConfigServiceImpl importFieldConfigService;

    /**
     * 每个用例执行前重新构造被测服务；实体/DTO 转换通过
     * {@code ImportFieldConfigConvert.INSTANCE} 静态调用完成，无需在此注入或 mock。
     */
    @BeforeEach
    void setUp() {
        importFieldConfigService = new ImportFieldConfigServiceImpl(importFieldConfigMapper,
                formFieldDefinitionMapper, operationLogRecorder);
    }

    /**
     * 创建导入字段配置时，若业务对象类型非法，应拒绝创建。
     */
    @Test
    void create_shouldThrowException_whenBizTypeInvalid() {
        ImportFieldConfigCreateRequest request = buildCreateRequest("INVALID", null, "code");

        assertThatThrownBy(() -> importFieldConfigService.create(request))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * 创建导入字段配置时，若未关联表单字段定义且未提交字段标识，应拒绝创建。
     */
    @Test
    void create_shouldThrowException_whenNoDefinitionAndFieldCodeBlank() {
        ImportFieldConfigCreateRequest request = buildCreateRequest("ORG", null, null);

        assertThatThrownBy(() -> importFieldConfigService.create(request))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * 创建导入字段配置时，未关联表单字段定义但提交了字段标识，应正常创建，字段标识
     * 直接取自请求提交的值。
     */
    @Test
    void create_shouldSucceed_whenNoDefinitionAndFieldCodeProvided() {
        ImportFieldConfigCreateRequest request = buildCreateRequest("ORG", null, "remark");
        lenient().when(importFieldConfigMapper.selectById(any()))
                .thenReturn(buildEntity(1L, "ORG", null, "remark"));

        importFieldConfigService.create(request);

        org.mockito.ArgumentCaptor<ImportFieldConfigEntity> captor =
                org.mockito.ArgumentCaptor.forClass(ImportFieldConfigEntity.class);
        org.mockito.Mockito.verify(importFieldConfigMapper).insert(captor.capture());
        assertThat(captor.getValue().getFieldCode()).isEqualTo("remark");
        assertThat(captor.getValue().getStatus()).isEqualTo(ImportFieldConfigStatus.ENABLED);
    }

    /**
     * 创建导入字段配置时，关联的表单字段定义不存在，应拒绝创建。
     */
    @Test
    void create_shouldThrowException_whenDefinitionNotFound() {
        when(formFieldDefinitionMapper.selectById(1L)).thenReturn(null);

        ImportFieldConfigCreateRequest request = buildCreateRequest("ORG", 1L, null);

        assertThatThrownBy(() -> importFieldConfigService.create(request))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * 创建导入字段配置时，关联的表单字段定义状态非启用，应拒绝创建。
     */
    @Test
    void create_shouldThrowException_whenDefinitionDisabled() {
        when(formFieldDefinitionMapper.selectById(1L))
                .thenReturn(buildDefinitionEntity(1L, "ORG", "remark", FormFieldStatus.DISABLED));

        ImportFieldConfigCreateRequest request = buildCreateRequest("ORG", 1L, null);

        assertThatThrownBy(() -> importFieldConfigService.create(request))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * 创建导入字段配置时，关联的表单字段定义 bizType 与请求不一致，应拒绝创建。
     */
    @Test
    void create_shouldThrowException_whenDefinitionBizTypeMismatch() {
        when(formFieldDefinitionMapper.selectById(1L))
                .thenReturn(buildDefinitionEntity(1L, "USER", "remark", FormFieldStatus.ENABLED));

        ImportFieldConfigCreateRequest request = buildCreateRequest("ORG", 1L, null);

        assertThatThrownBy(() -> importFieldConfigService.create(request))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * 创建导入字段配置时，字段标识在同 bizType 下已存在有效配置，应拒绝创建。
     */
    @Test
    void create_shouldThrowException_whenFieldCodeDuplicate() {
        when(formFieldDefinitionMapper.selectById(1L))
                .thenReturn(buildDefinitionEntity(1L, "ORG", "remark", FormFieldStatus.ENABLED));
        when(importFieldConfigMapper.existsActiveByFieldCode("ORG", "remark")).thenReturn(true);

        ImportFieldConfigCreateRequest request = buildCreateRequest("ORG", 1L, null);

        assertThatThrownBy(() -> importFieldConfigService.create(request))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * 创建导入字段配置成功时，字段标识应取自所绑定的表单字段定义，而不是请求提交的
     * （空）字段标识。
     */
    @Test
    void create_shouldDeriveFieldCodeFromDefinition() {
        when(formFieldDefinitionMapper.selectById(1L))
                .thenReturn(buildDefinitionEntity(1L, "ORG", "remark", FormFieldStatus.ENABLED));
        when(importFieldConfigMapper.existsActiveByFieldCode("ORG", "remark")).thenReturn(false);
        lenient().when(importFieldConfigMapper.selectById(any()))
                .thenReturn(buildEntity(99L, "ORG", 1L, "remark"));

        ImportFieldConfigCreateRequest request = buildCreateRequest("ORG", 1L, null);
        importFieldConfigService.create(request);

        org.mockito.ArgumentCaptor<ImportFieldConfigEntity> captor =
                org.mockito.ArgumentCaptor.forClass(ImportFieldConfigEntity.class);
        org.mockito.Mockito.verify(importFieldConfigMapper).insert(captor.capture());
        assertThat(captor.getValue().getFieldCode()).isEqualTo("remark");
    }

    /**
     * 更新一条锁定（系统保护）配置时，若尝试把 isRequired 改为 false，应拒绝该次更新。
     */
    @Test
    void update_shouldThrowException_whenLockedConfigSetIsRequiredFalse() {
        ImportFieldConfigEntity entity = buildEntity(10L, "POSITION", null, "__userCode");
        when(importFieldConfigMapper.selectById(10L)).thenReturn(entity);

        ImportFieldConfigUpdateRequest request = buildUpdateRequest();
        request.setIsRequired(false);

        assertThatThrownBy(() -> importFieldConfigService.update(10L, request))
                .isInstanceOf(LockedImportFieldConfigException.class);
    }

    /**
     * 更新一条锁定配置时，若尝试把 isPrimaryKey 改为 false，应拒绝该次更新。
     */
    @Test
    void update_shouldThrowException_whenLockedConfigSetIsPrimaryKeyFalse() {
        ImportFieldConfigEntity entity = buildEntity(10L, "POSITION", null, "__orgCode");
        when(importFieldConfigMapper.selectById(10L)).thenReturn(entity);

        ImportFieldConfigUpdateRequest request = buildUpdateRequest();
        request.setIsPrimaryKey(false);

        assertThatThrownBy(() -> importFieldConfigService.update(10L, request))
                .isInstanceOf(LockedImportFieldConfigException.class);
    }

    /**
     * 更新一条锁定配置时，若尝试改绑表单字段定义，应拒绝该次更新。
     */
    @Test
    void update_shouldThrowException_whenLockedConfigRebind() {
        ImportFieldConfigEntity entity = buildEntity(10L, "POSITION", null, "__userCode");
        when(importFieldConfigMapper.selectById(10L)).thenReturn(entity);

        ImportFieldConfigUpdateRequest request = buildUpdateRequest();
        request.setFormFieldDefinitionId(2L);

        assertThatThrownBy(() -> importFieldConfigService.update(10L, request))
                .isInstanceOf(LockedImportFieldConfigException.class);
    }

    /**
     * 更新一条锁定配置时，仅调整表头文字与显示序号应正常保存（锁定配置仍允许调整这
     * 两项）。
     */
    @Test
    void update_shouldSucceed_whenLockedConfigOnlyChangesHeaderNameAndShowOrder() {
        ImportFieldConfigEntity entity = buildEntity(10L, "POSITION", null, "__userCode");
        when(importFieldConfigMapper.selectById(10L)).thenReturn(entity);

        ImportFieldConfigUpdateRequest request = buildUpdateRequest();
        request.setExcelHeaderName("人员工号");
        request.setShowOrder(300);

        importFieldConfigService.update(10L, request);

        assertThat(entity.getExcelHeaderName()).isEqualTo("人员工号");
        assertThat(entity.getShowOrder()).isEqualTo(300);
    }

    /**
     * 更新一条非锁定配置时，若改绑到同一 bizType 下另一个启用的表单字段定义，应改绑
     * 成功，且字段标识同步刷新为新绑定定义的当前值。
     */
    @Test
    void update_shouldRebind_whenNonLockedTargetsAvailableDefinition() {
        ImportFieldConfigEntity entity = buildEntity(10L, "ORG", 1L, "remark");
        when(importFieldConfigMapper.selectById(10L)).thenReturn(entity);
        when(formFieldDefinitionMapper.selectById(2L))
                .thenReturn(buildDefinitionEntity(2L, "ORG", "ext1Code", FormFieldStatus.ENABLED));
        when(importFieldConfigMapper.existsActiveByFieldCodeExcluding("ORG", "ext1Code", 10L)).thenReturn(false);

        ImportFieldConfigUpdateRequest request = buildUpdateRequest();
        request.setFormFieldDefinitionId(2L);

        importFieldConfigService.update(10L, request);

        assertThat(entity.getFormFieldDefinitionId()).isEqualTo(2L);
        assertThat(entity.getFieldCode()).isEqualTo("ext1Code");
    }

    /**
     * 删除一条锁定配置时，应拒绝删除。
     */
    @Test
    void delete_shouldThrowException_whenLockedConfig() {
        ImportFieldConfigEntity entity = buildEntity(10L, "POSITION", null, "__orgCode");
        when(importFieldConfigMapper.selectById(10L)).thenReturn(entity);

        assertThatThrownBy(() -> importFieldConfigService.delete(10L))
                .isInstanceOf(LockedImportFieldConfigException.class);
        assertThat(entity.getStatus()).isNotEqualTo(ImportFieldConfigStatus.DELETED);
    }

    /**
     * 删除一条非锁定配置时，应正常执行逻辑删除。
     */
    @Test
    void delete_shouldSucceed_whenNotLocked() {
        ImportFieldConfigEntity entity = buildEntity(10L, "ORG", 1L, "remark");
        when(importFieldConfigMapper.selectById(10L)).thenReturn(entity);

        importFieldConfigService.delete(10L);

        assertThat(entity.getStatus()).isEqualTo(ImportFieldConfigStatus.DELETED);
    }

    /**
     * 查询详情时，返回的视图对象 locked 标记应准确反映锁定白名单的判定结果。
     */
    @Test
    void getById_shouldComputeLockedFlag() {
        ImportFieldConfigEntity entity = buildEntity(10L, "POSITION", null, "__userCode");
        when(importFieldConfigMapper.selectById(10L)).thenReturn(entity);

        ImportFieldConfigVO vo = importFieldConfigService.getById(10L);

        assertThat(vo.getLocked()).isTrue();
    }

    /**
     * 查询启用状态列表时，应只返回启用状态的记录，非启用状态记录不通过 Mapper 的
     * 查询条件返回，验证调用参数正确传递业务对象类型。
     */
    @Test
    void listActiveByBizType_shouldReturnEnrichedList() {
        ImportFieldConfigEntity entity = buildEntity(11L, "APP", 1L, "code");
        when(importFieldConfigMapper.selectList(any())).thenReturn(List.of(entity));

        List<ImportFieldConfigVO> result = importFieldConfigService.listActiveByBizType("APP");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFieldCode()).isEqualTo("code");
        assertThat(result.get(0).getLocked()).isFalse();
    }

    /**
     * 构造一条创建请求。
     *
     * @param bizType                业务对象类型
     * @param formFieldDefinitionId  绑定的表单字段定义 id，可为 null
     * @param fieldCode              字段标识，仅在未绑定定义时生效
     * @return 创建请求
     */
    private ImportFieldConfigCreateRequest buildCreateRequest(String bizType, Long formFieldDefinitionId,
            String fieldCode) {
        ImportFieldConfigCreateRequest request = new ImportFieldConfigCreateRequest();
        request.setBizType(bizType);
        request.setFormFieldDefinitionId(formFieldDefinitionId);
        request.setFieldCode(fieldCode);
        request.setExcelHeaderName("测试表头");
        request.setIsPrimaryKey(false);
        request.setIsRequired(false);
        request.setShowOrder(0);
        return request;
    }

    /**
     * 构造一条更新请求，默认值满足锁定配置的保护要求（主键/必填均为 {@code true}，
     * 不改绑），测试用例按需覆盖单个字段。
     *
     * @return 更新请求
     */
    private ImportFieldConfigUpdateRequest buildUpdateRequest() {
        ImportFieldConfigUpdateRequest request = new ImportFieldConfigUpdateRequest();
        request.setFormFieldDefinitionId(null);
        request.setExcelHeaderName("测试表头");
        request.setIsPrimaryKey(true);
        request.setIsRequired(true);
        request.setShowOrder(0);
        return request;
    }

    /**
     * 构造一个测试用的导入字段配置实体。
     *
     * @param id                     主键 id
     * @param bizType                业务对象类型
     * @param formFieldDefinitionId  绑定的表单字段定义 id，可为 null
     * @param fieldCode              字段标识
     * @return 导入字段配置实体
     */
    private ImportFieldConfigEntity buildEntity(long id, String bizType, Long formFieldDefinitionId,
            String fieldCode) {
        return ImportFieldConfigEntity.builder()
                .id(id)
                .bizType(bizType)
                .formFieldDefinitionId(formFieldDefinitionId)
                .fieldCode(fieldCode)
                .excelHeaderName("测试表头")
                .isPrimaryKey(true)
                .isRequired(true)
                .showOrder(0)
                .status(ImportFieldConfigStatus.ENABLED)
                .build();
    }

    /**
     * 构造一个测试用的表单字段定义实体。
     *
     * @param id        主键 id
     * @param bizType   业务对象类型
     * @param fieldCode 字段标识
     * @param status    状态
     * @return 表单字段定义实体
     */
    private FormFieldDefinitionEntity buildDefinitionEntity(long id, String bizType, String fieldCode, int status) {
        return FormFieldDefinitionEntity.builder()
                .id(id)
                .bizType(bizType)
                .metadataFieldId(1L)
                .fieldName(fieldCode)
                .fieldCode(fieldCode)
                .controlType(1)
                .isUnique(false)
                .isRequired(false)
                .showInList(true)
                .showInCreate(true)
                .showInEdit(true)
                .editable(true)
                .showOrder(0)
                .status(status)
                .build();
    }
}
