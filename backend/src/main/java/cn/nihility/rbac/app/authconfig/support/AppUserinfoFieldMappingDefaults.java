package cn.nihility.rbac.app.authconfig.support;

import cn.nihility.rbac.app.authconfig.dto.AppUserinfoFieldMappingRow;
import cn.nihility.rbac.app.sync.constant.TransformType;
import cn.nihility.rbac.formfield.constant.FormFieldBizType;
import cn.nihility.rbac.metadata.entity.MetadataFieldEntity;
import cn.nihility.rbac.metadata.mapper.MetadataFieldMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.ArrayList;
import java.util.List;

/**
 * 应用用户信息响应字段映射的默认（未落库）两行现算逻辑（add-sso-userinfo-field-mapping
 * change design.md Decision 4）：某应用在 {@code tab_app_userinfo_field_mapping} 无任何
 * 记录时，管理端查询接口（{@code AppAuthConfigServiceImpl}）与运行时解析组件
 * （{@code SsoUserinfoAttributesResolver}）均调用本类现算同一份默认列表，不写库，避免两处
 * 各自重复实现同一段兜底逻辑。无状态，风格对齐 {@code AppScopeGuard}，静态方法按需接收调用方
 * 已注入的 {@link MetadataFieldMapper}，不注册为 Spring bean。
 */
public final class AppUserinfoFieldMappingDefaults {

    /** 固定的"用户ID"伪字段应用侧字段编码/名称。 */
    private static final String USER_ID_FIELD_CODE = "id";

    /** 固定的"用户ID"伪字段展示名称。 */
    private static final String USER_ID_FIELD_NAME = "用户ID";

    /** 默认"姓名"字段的应用侧字段编码。 */
    private static final String NAME_FIELD_CODE = "name";

    /** 默认"姓名"字段的应用侧字段展示名称。 */
    private static final String NAME_FIELD_APP_NAME = "姓名";

    /**
     * 工具类不允许实例化。
     */
    private AppUserinfoFieldMappingDefaults() {
    }

    /**
     * 现算默认的用户信息字段映射列表：固定的"用户ID"伪字段一行，以及按
     * {@code bizType=USER AND columnName=name} 查到的"姓名"元数据字段一行；查不到"姓名"
     * 元数据字段时（当前无删除入口，防御性处理）退化为只有"用户ID"一行。
     *
     * @param metadataFieldMapper 元数据字段数据访问接口
     * @return 默认的用户信息字段映射行列表（{@code id} 均为 {@code null}，未落库）
     */
    public static List<AppUserinfoFieldMappingRow> compute(MetadataFieldMapper metadataFieldMapper) {
        List<AppUserinfoFieldMappingRow> rows = new ArrayList<>();
        rows.add(AppUserinfoFieldMappingRow.builder()
                .metadataFieldId(null)
                .fieldName(USER_ID_FIELD_NAME)
                .fieldCode(USER_ID_FIELD_CODE)
                .appFieldName(USER_ID_FIELD_NAME)
                .appFieldCode(USER_ID_FIELD_CODE)
                .transformType(TransformType.NO_TRANSFORM)
                .build());

        MetadataFieldEntity nameField = metadataFieldMapper.selectOne(new LambdaQueryWrapper<MetadataFieldEntity>()
                .eq(MetadataFieldEntity::getBizType, FormFieldBizType.USER)
                .eq(MetadataFieldEntity::getColumnName, NAME_FIELD_CODE));
        if (nameField != null) {
            rows.add(AppUserinfoFieldMappingRow.builder()
                    .metadataFieldId(nameField.getId())
                    .fieldName(nameField.getFieldName())
                    .fieldCode(nameField.getFieldCode())
                    .appFieldName(NAME_FIELD_APP_NAME)
                    .appFieldCode(NAME_FIELD_CODE)
                    .transformType(TransformType.NO_TRANSFORM)
                    .build());
        }
        return rows;
    }
}
