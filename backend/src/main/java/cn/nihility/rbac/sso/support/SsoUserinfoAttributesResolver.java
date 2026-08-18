package cn.nihility.rbac.sso.support;

import cn.nihility.rbac.app.authconfig.dto.AppUserinfoFieldMappingRow;
import cn.nihility.rbac.app.authconfig.mapper.AppUserinfoFieldMappingMapper;
import cn.nihility.rbac.app.authconfig.support.AppUserinfoFieldMappingDefaults;
import cn.nihility.rbac.app.sync.constant.TransformType;
import cn.nihility.rbac.common.util.ScriptTransformExecutor;
import cn.nihility.rbac.metadata.mapper.MetadataFieldMapper;
import cn.nihility.rbac.user.entity.UserEntity;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.stereotype.Component;

/**
 * CAS/OAuth2 用户信息响应属性运行时解析组件（add-sso-userinfo-field-mapping change
 * design.md Decision 4/5）：按 {@code appRefId} 查询用户信息字段映射配置（无记录时用与
 * {@code AppAuthConfigServiceImpl#listUserinfoFieldMappings} 相同的
 * {@link AppUserinfoFieldMappingDefaults} 现算默认两行逻辑），对每行按
 * {@code fieldCode}（"id"/"name"/"mobile" 等，与 {@code tab_metadata_field.columnName}/
 * 固定伪字段"id"对应）从 {@link UserEntity} 取值，按 {@code transformType} 应用转换后返回
 * {@code LinkedHashMap}（key 为 {@code appFieldCode}，保持配置顺序）。不经过
 * {@code AppAuthConfigService}——那是带管辖组织范围校验/操作日志等管理端语义的服务层，这里
 * 是无登录态的公开协议端点内部使用，直接查 Mapper 更清晰（同 {@link AppProtocolGuard} 既有
 * 模式）。
 */
@Component
@RequiredArgsConstructor
public class SsoUserinfoAttributesResolver {

    /** 应用用户信息响应字段映射数据访问接口。 */
    private final AppUserinfoFieldMappingMapper appUserinfoFieldMappingMapper;

    /** 元数据字段数据访问接口，供无记录时现算默认两行使用。 */
    private final MetadataFieldMapper metadataFieldMapper;

    /**
     * 按应用 id 解析用户信息响应属性 Map。
     *
     * @param appRefId 应用 id（{@code tab_app.id}）
     * @param user     已通过认证的用户实体
     * @return 属性 Map，key 为 {@code appFieldCode}，保持配置顺序
     */
    public Map<String, Object> resolve(Long appRefId, UserEntity user) {
        List<AppUserinfoFieldMappingRow> rows = appUserinfoFieldMappingMapper.selectByAppRefId(appRefId);
        if (rows.isEmpty()) {
            rows = AppUserinfoFieldMappingDefaults.compute(metadataFieldMapper);
        }

        BeanWrapper userBeanWrapper = new BeanWrapperImpl(user);
        Map<String, Object> attributes = new LinkedHashMap<>();
        for (AppUserinfoFieldMappingRow row : rows) {
            Object sourceValue = resolveSourceValue(userBeanWrapper, row.getFieldCode());
            Object targetValue = switch (row.getTransformType()) {
                case TransformType.FIXED_VALUE -> row.getTransformValue();
                case TransformType.SCRIPT -> ScriptTransformExecutor.execute(row.getTransformValue(), sourceValue);
                default -> sourceValue;
            };
            attributes.put(row.getAppFieldCode(), targetValue);
        }
        return attributes;
    }

    /**
     * 按 {@code fieldCode}（固定伪字段"id"，或 {@code tab_metadata_field.fieldCode}，均与
     * {@link UserEntity} 的 Java 属性名一致）从用户实体读取源字段值，读取失败（属性不存在等）
     * 时返回 {@code null}，不向上抛出异常影响其余字段的解析。
     *
     * @param userBeanWrapper 用户实体的属性访问器
     * @param fieldCode       字段编码，同时也是 {@link UserEntity} 的属性名
     * @return 源字段值，读取失败时返回 {@code null}
     */
    private Object resolveSourceValue(BeanWrapper userBeanWrapper, String fieldCode) {
        try {
            return fieldCode != null ? userBeanWrapper.getPropertyValue(fieldCode) : null;
        } catch (Exception e) {
            return null;
        }
    }
}
