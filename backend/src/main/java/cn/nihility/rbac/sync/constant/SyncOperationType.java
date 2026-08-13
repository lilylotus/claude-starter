package cn.nihility.rbac.sync.constant;

import cn.nihility.rbac.operationlog.constant.OperationType;
import java.util.Map;

/**
 * 把 {@link OperationType} 的整型码值转换为对外通知/拉取契约使用的英文字符串编码
 * （app-sync-notify-pull-api change design.md Decision 8 示例 JSON 中的
 * {@code "operationType": "UPDATE"}），与操作日志展示用的中文文案（{@link OperationType#label}）
 * 是两套独立的映射，互不影响。
 */
public final class SyncOperationType {

    /** 码值 -> 英文字符串编码。 */
    private static final Map<Integer, String> CODES = Map.of(
            OperationType.CREATE, "CREATE",
            OperationType.UPDATE, "UPDATE",
            OperationType.ENABLE, "ENABLE",
            OperationType.DISABLE, "DISABLE",
            OperationType.DELETE, "DELETE");

    /**
     * 工具类不允许实例化。
     */
    private SyncOperationType() {
    }

    /**
     * 按操作类型码值查询对外英文字符串编码。
     *
     * @param operationType 操作类型码值
     * @return 英文字符串编码，取值不合法时返回 {@code null}
     */
    public static String code(Integer operationType) {
        return operationType != null ? CODES.get(operationType) : null;
    }
}
