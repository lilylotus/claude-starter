package cn.nihility.rbac.sso.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.app.authconfig.dto.AppUserinfoFieldMappingRow;
import cn.nihility.rbac.app.authconfig.mapper.AppUserinfoFieldMappingMapper;
import cn.nihility.rbac.app.sync.constant.TransformType;
import cn.nihility.rbac.metadata.entity.MetadataFieldEntity;
import cn.nihility.rbac.metadata.mapper.MetadataFieldMapper;
import cn.nihility.rbac.user.entity.UserEntity;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link SsoUserinfoAttributesResolver} 的单元测试（add-sso-userinfo-field-mapping change
 * tasks.md 9.1），覆盖无记录时的默认两行兜底、已配置映射（含固定值/转换脚本）两种场景。
 */
@ExtendWith(MockitoExtension.class)
class SsoUserinfoAttributesResolverTest {

    @Mock
    private AppUserinfoFieldMappingMapper appUserinfoFieldMappingMapper;

    @Mock
    private MetadataFieldMapper metadataFieldMapper;

    private SsoUserinfoAttributesResolver resolver;

    /**
     * 测试用用户实体：id=1，姓名"张三"，编号"u001"。
     */
    private final UserEntity user = UserEntity.builder().id(1L).name("张三").code("u001").build();

    /**
     * 无任何映射记录时，应用默认的"用户ID + 姓名"两行现算兜底逻辑生成属性 Map。
     */
    @Test
    void resolve_shouldUseDefaultRows_whenNoRecords() {
        resolver = new SsoUserinfoAttributesResolver(appUserinfoFieldMappingMapper, metadataFieldMapper);
        when(appUserinfoFieldMappingMapper.selectByAppRefId(10L)).thenReturn(List.of());
        MetadataFieldEntity nameField = MetadataFieldEntity.builder()
                .id(2L).bizType("USER").tableName("tab_user").columnName("name").columnType("VARCHAR(64)")
                .fieldCode("name").fieldName("用户姓名").build();
        when(metadataFieldMapper.selectOne(any())).thenReturn(nameField);

        Map<String, Object> attributes = resolver.resolve(10L, user);

        assertThat(attributes).containsEntry("id", 1L);
        assertThat(attributes).containsEntry("name", "张三");
    }

    /**
     * 已配置映射时，按 {@code fieldCode} 从用户实体取值，并按 {@code transformType} 应用
     * 转换（{@code NO_TRANSFORM}/{@code FIXED_VALUE} 各一行）。
     */
    @Test
    void resolve_shouldApplyTransform_whenRecordsConfigured() {
        resolver = new SsoUserinfoAttributesResolver(appUserinfoFieldMappingMapper, metadataFieldMapper);
        when(appUserinfoFieldMappingMapper.selectByAppRefId(10L)).thenReturn(List.of(
                AppUserinfoFieldMappingRow.builder()
                        .metadataFieldId(3L).fieldName("用户编号").fieldCode("code")
                        .appFieldName("userCode").appFieldCode("userCode")
                        .transformType(TransformType.NO_TRANSFORM).build(),
                AppUserinfoFieldMappingRow.builder()
                        .metadataFieldId(null).fieldName("固定租户").fieldCode("id")
                        .appFieldName("tenant").appFieldCode("tenant")
                        .transformType(TransformType.FIXED_VALUE).transformValue("demo-tenant").build()));

        Map<String, Object> attributes = resolver.resolve(10L, user);

        assertThat(attributes).containsEntry("userCode", "u001");
        assertThat(attributes).containsEntry("tenant", "demo-tenant");
    }
}
