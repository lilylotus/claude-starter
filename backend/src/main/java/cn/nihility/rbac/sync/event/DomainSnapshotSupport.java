package cn.nihility.rbac.sync.event;

import cn.nihility.rbac.common.util.JacksonUtils;
import java.util.Map;

/**
 * 领域变更事件快照构造工具（app-sync-notify-pull-api change design.md Decision 4）：
 * 直接把实体对象转换为 {@code Map<String,Object>}，key 为实体属性名（camelCase），与
 * {@code tab_metadata_field.field_code} 对齐（{@code fieldCode} 取值如 {@code idCard}/
 * {@code showOrder}，本就是实体的 Java 属性名）。刻意与 {@code OperationLogRecorder}
 * 使用的"中文字段名 + 人类可读格式化值"快照（各 {@code *ServiceImpl} 内部的
 * {@code toLogSnapshot}）区分开：操作日志快照面向管理端页面展示，本快照面向外部应用的
 * 拉取接口/字段映射转换，字段名必须是可编程匹配的原始属性名，不能是中文展示文案。
 */
public final class DomainSnapshotSupport {

    /**
     * 工具类不允许实例化。
     */
    private DomainSnapshotSupport() {
    }

    /**
     * 把实体对象转换为字段快照 Map。
     *
     * @param entity 实体对象
     * @return 字段快照，key 为实体属性名（camelCase）
     */
    public static Map<String, Object> snapshot(Object entity) {
        return JacksonUtils.convert(entity, JacksonUtils.MAP_OBJECT_TYPE_REFERENCE);
    }
}
