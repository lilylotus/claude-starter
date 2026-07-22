package cn.nihility.rbac.metadata.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.formfield.mapper.FormFieldDefinitionMapper;
import cn.nihility.rbac.metadata.constant.MetadataFieldStatus;
import cn.nihility.rbac.metadata.dto.MetadataFieldUpdateRequest;
import cn.nihility.rbac.metadata.dto.MetadataFieldVO;
import cn.nihility.rbac.metadata.entity.MetadataFieldEntity;
import cn.nihility.rbac.metadata.exception.MetadataFieldInUseException;
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
 * {@link MetadataFieldServiceImpl} 的单元测试，重点覆盖停用被占用字段的拒绝逻辑、
 * "可用元数据字段"过滤逻辑、以及仅字段名称可编辑等分支。
 */
@ExtendWith(MockitoExtension.class)
class MetadataFieldServiceImplTest {

    /** 被测服务的元数据字段数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private MetadataFieldMapper metadataFieldMapper;

    /** 被测服务的表单字段定义数据访问依赖，仅用于判断占用情况，使用 Mockito 打桩。 */
    @Mock
    private FormFieldDefinitionMapper formFieldDefinitionMapper;

    /** 被测服务的操作日志记录组件依赖，使用 Mockito 打桩。 */
    @Mock
    private OperationLogRecorder operationLogRecorder;

    /** 被测服务实例。 */
    private MetadataFieldServiceImpl metadataFieldService;

    /**
     * 每个用例执行前重新构造被测服务；实体/DTO 转换通过
     * {@code MetadataFieldConvert.INSTANCE} 静态调用完成，无需在此注入或 mock。
     */
    @BeforeEach
    void setUp() {
        metadataFieldService = new MetadataFieldServiceImpl(metadataFieldMapper, formFieldDefinitionMapper,
                operationLogRecorder);
    }

    /**
     * 停用一个当前被有效表单字段定义绑定的元数据字段时，应拒绝停用并保持状态不变。
     */
    @Test
    void disable_shouldThrowException_whenFieldInUse() {
        MetadataFieldEntity entity = buildEntity(1L, "code", MetadataFieldStatus.ENABLED);
        when(metadataFieldMapper.selectById(1L)).thenReturn(entity);
        when(formFieldDefinitionMapper.existsActiveByMetadataFieldId(1L)).thenReturn(true);

        assertThatThrownBy(() -> metadataFieldService.disable(1L))
                .isInstanceOf(MetadataFieldInUseException.class);
        assertThat(entity.getStatus()).isEqualTo(MetadataFieldStatus.ENABLED);
    }

    /**
     * 停用一个当前未被任何有效表单字段定义绑定的元数据字段时，应正常停用。
     */
    @Test
    void disable_shouldSucceed_whenFieldNotInUse() {
        MetadataFieldEntity entity = buildEntity(1L, "remark", MetadataFieldStatus.ENABLED);
        when(metadataFieldMapper.selectById(1L)).thenReturn(entity);
        when(formFieldDefinitionMapper.existsActiveByMetadataFieldId(1L)).thenReturn(false);

        metadataFieldService.disable(1L);

        assertThat(entity.getStatus()).isEqualTo(MetadataFieldStatus.DISABLED);
    }

    /**
     * 更新元数据字段时，仅 {@code fieldName} 会被更新，物理属性（表名称/列名/列类型/
     * 业务对象类型）保持不变——请求 DTO 本身只暴露 {@code fieldName}，从结构上保证
     * 了这一点。
     */
    @Test
    void update_shouldOnlyChangeFieldName() {
        MetadataFieldEntity entity = buildEntity(1L, "code", MetadataFieldStatus.ENABLED);
        when(metadataFieldMapper.selectById(1L)).thenReturn(entity);

        MetadataFieldUpdateRequest request = new MetadataFieldUpdateRequest();
        request.setFieldName("组织编码（新）");

        metadataFieldService.update(1L, request);

        assertThat(entity.getFieldName()).isEqualTo("组织编码（新）");
        assertThat(entity.getTableName()).isEqualTo("tab_org");
        assertThat(entity.getColumnName()).isEqualTo("code");
        assertThat(entity.getBizType()).isEqualTo("ORG");
    }

    /**
     * 查询可用元数据字段时，应过滤掉已被有效表单字段定义绑定的记录，只返回未被占用的。
     */
    @Test
    void listAvailable_shouldExcludeFieldsAlreadyBound() {
        MetadataFieldEntity boundEntity = buildEntity(1L, "code", MetadataFieldStatus.ENABLED);
        MetadataFieldEntity availableEntity = buildEntity(2L, "ext1", MetadataFieldStatus.ENABLED);
        when(metadataFieldMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(boundEntity, availableEntity));
        when(formFieldDefinitionMapper.existsActiveByMetadataFieldId(1L)).thenReturn(true);
        when(formFieldDefinitionMapper.existsActiveByMetadataFieldId(2L)).thenReturn(false);

        List<MetadataFieldVO> available = metadataFieldService.listAvailable("ORG");

        assertThat(available).hasSize(1);
        assertThat(available.get(0).getColumnName()).isEqualTo("ext1");
    }

    /**
     * 查询一个不存在的元数据字段时，应抛出业务异常。
     */
    @Test
    void getById_shouldThrowBusinessException_whenNotFound() {
        when(metadataFieldMapper.selectById(anyLong())).thenReturn(null);

        assertThatThrownBy(() -> metadataFieldService.getById(99L))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * 构造一个测试用的元数据字段实体。
     *
     * @param id         主键 id
     * @param columnName 字段列名
     * @param status     状态
     * @return 元数据字段实体
     */
    private MetadataFieldEntity buildEntity(long id, String columnName, int status) {
        return MetadataFieldEntity.builder()
                .id(id)
                .bizType("ORG")
                .tableName("tab_org")
                .columnName(columnName)
                .columnType("VARCHAR(64)")
                .fieldName("组织编码")
                .status(status)
                .build();
    }
}
