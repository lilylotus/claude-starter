package cn.nihility.rbac.app.authconfig.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.app.authconfig.dto.AppUserinfoFieldMappingRow;
import cn.nihility.rbac.app.sync.constant.TransformType;
import cn.nihility.rbac.metadata.entity.MetadataFieldEntity;
import cn.nihility.rbac.metadata.mapper.MetadataFieldMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link AppUserinfoFieldMappingDefaults} 的单元测试（add-sso-userinfo-field-mapping change
 * tasks.md 9.1），覆盖"姓名"元数据字段存在/不存在两种场景（design.md Decision 4）。
 */
@ExtendWith(MockitoExtension.class)
class AppUserinfoFieldMappingDefaultsTest {

    @Mock
    private MetadataFieldMapper metadataFieldMapper;

    /**
     * "姓名"元数据字段存在时，默认列表应包含"用户ID"、"姓名"两行。
     */
    @Test
    void compute_shouldReturnTwoRows_whenNameFieldExists() {
        MetadataFieldEntity nameField = MetadataFieldEntity.builder()
                .id(2L)
                .bizType("USER")
                .tableName("tab_user")
                .columnName("name")
                .columnType("VARCHAR(64)")
                .fieldCode("name")
                .fieldName("用户姓名")
                .build();
        when(metadataFieldMapper.selectOne(any())).thenReturn(nameField);

        List<AppUserinfoFieldMappingRow> rows = AppUserinfoFieldMappingDefaults.compute(metadataFieldMapper);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).getMetadataFieldId()).isNull();
        assertThat(rows.get(0).getFieldCode()).isEqualTo("id");
        assertThat(rows.get(0).getAppFieldCode()).isEqualTo("id");
        assertThat(rows.get(0).getTransformType()).isEqualTo(TransformType.NO_TRANSFORM);
        assertThat(rows.get(1).getMetadataFieldId()).isEqualTo(2L);
        assertThat(rows.get(1).getFieldCode()).isEqualTo("name");
        assertThat(rows.get(1).getAppFieldCode()).isEqualTo("name");
    }

    /**
     * "姓名"元数据字段查不到时（当前无删除入口，防御性场景），默认列表应退化为只有
     * "用户ID"一行。
     */
    @Test
    void compute_shouldReturnOnlyUserIdRow_whenNameFieldMissing() {
        when(metadataFieldMapper.selectOne(any())).thenReturn(null);

        List<AppUserinfoFieldMappingRow> rows = AppUserinfoFieldMappingDefaults.compute(metadataFieldMapper);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getAppFieldCode()).isEqualTo("id");
    }
}
