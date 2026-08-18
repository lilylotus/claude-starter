package cn.nihility.rbac.sync.transform;

import cn.nihility.rbac.app.sync.constant.TransformType;
import cn.nihility.rbac.app.sync.dto.AppSyncFieldMappingRow;
import cn.nihility.rbac.app.sync.mapper.AppSyncFieldMappingMapper;
import cn.nihility.rbac.common.util.ScriptTransformExecutor;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 拉取结果字段映射转换执行器（app-sync-notify-pull-api change design.md Decision 9）：
 * {@code NO_TRANSFORM}/{@code FIXED_VALUE} 直接取值，{@code SCRIPT} 委托
 * {@link ScriptTransformExecutor}（GraalVM 沙箱执行，绑定方式与 {@code
 * TransformScriptValidator} 现有语法校验的执行方言保持一致：脚本以 {@code value} 全局变量
 * 读入源字段值，脚本最后一个表达式的值作为结果），限制权限（不注入宿主对象、不给网络/文件
 * 系统访问能力）并加执行超时保护，超时判定该字段转换失败、跳过（design.md Risks；沙箱执行
 * 细节抽取见 add-sso-userinfo-field-mapping change design.md Decision 5）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FieldMappingTransformer {

    /** 应用同步字段映射数据访问接口，复用 {@code app.sync} 模块既有 Mapper（跨模块只读查询）。 */
    private final AppSyncFieldMappingMapper appSyncFieldMappingMapper;

    /**
     * 按应用、数据域的字段映射配置转换一条变更记录的字段快照。未配置任何字段映射时原样
     * 返回全部字段（design.md Decision 9 兜底策略）。
     *
     * @param appRefId   应用 id
     * @param syncDomain 数据域
     * @param snapshot   变更记录的原始字段快照（key 为实体属性名）
     * @return 转换后的字段 Map（key 为应用字段编码），或原始快照（未配置字段映射时）
     */
    public Map<String, Object> transform(Long appRefId, String syncDomain, Map<String, Object> snapshot) {
        List<AppSyncFieldMappingRow> mappings = appSyncFieldMappingMapper.selectByAppRefIdAndDomain(appRefId,
                syncDomain);
        if (mappings.isEmpty()) {
            return snapshot;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        for (AppSyncFieldMappingRow mapping : mappings) {
            Object sourceValue = snapshot != null ? snapshot.get(mapping.getFieldCode()) : null;
            Object targetValue = switch (mapping.getTransformType()) {
                case TransformType.FIXED_VALUE -> mapping.getTransformValue();
                case TransformType.SCRIPT ->
                        ScriptTransformExecutor.execute(mapping.getTransformValue(), sourceValue);
                default -> sourceValue;
            };
            result.put(mapping.getAppFieldCode(), targetValue);
        }
        return result;
    }
}
