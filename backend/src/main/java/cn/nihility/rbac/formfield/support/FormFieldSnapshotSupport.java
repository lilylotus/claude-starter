package cn.nihility.rbac.formfield.support;

import cn.nihility.rbac.formfield.dto.FormFieldDefinitionVO;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 组织/用户/任职/应用四个业务模块共用的操作日志字段快照扩展字段填充工具，把当前启用的
 * {@code ext1}..{@code ext10} 字段定义补充进各模块 {@code toLogSnapshot} 手工构建的
 * {@code Map<String, Object>} 快照里，key 使用字段定义的展示名（{@code fieldName}）而不是
 * {@code ext1} 这类技术列名，避免四个模块各自重复实现同样的过滤+写入逻辑（design.md
 * Decision 3）。
 */
public final class FormFieldSnapshotSupport {

    /** {@code ext1}..{@code ext10} 列名集合，用于过滤字段定义中绑定到扩展字段的部分。 */
    private static final Set<String> EXT_COLUMN_NAMES = Set.of(
            "ext1", "ext2", "ext3", "ext4", "ext5", "ext6", "ext7", "ext8", "ext9", "ext10");

    /**
     * 工具类不允许实例化。
     */
    private FormFieldSnapshotSupport() {
    }

    /**
     * 把 {@code definitions} 中绑定到 {@code ext1}..{@code ext10} 的字段定义对应的当前值
     * 追加进快照 map，未配置字段定义的 {@code extN} 列不出现在快照里。
     *
     * @param snapshot              待追加的操作日志字段快照，直接原地修改
     * @param definitions           当前启用的字段定义列表，通常来自
     *                              {@code FormFieldDefinitionService.listActiveByBizType}
     * @param extValuesByColumnName {@code ext1}..{@code ext10} 列名到实体当前对应值的映射
     */
    public static void appendExtFieldSnapshot(Map<String, Object> snapshot, List<FormFieldDefinitionVO> definitions,
            Map<String, String> extValuesByColumnName) {
        if (definitions == null || definitions.isEmpty()) {
            return;
        }
        for (FormFieldDefinitionVO definition : definitions) {
            String columnName = definition.getColumnName();
            if (columnName != null && EXT_COLUMN_NAMES.contains(columnName)) {
                snapshot.put(definition.getFieldName(), extValuesByColumnName.get(columnName));
            }
        }
    }
}
