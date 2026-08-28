package cn.nihility.rbac.approval.constant;

import java.util.Set;

/**
 * 审批申请支持的主数据写操作类型常量。
 */
public final class ApprovalOperationType {

    /** 新增。 */
    public static final String CREATE = "CREATE";

    /** 更新。 */
    public static final String UPDATE = "UPDATE";

    /** 启用。 */
    public static final String ENABLE = "ENABLE";

    /** 停用。 */
    public static final String DISABLE = "DISABLE";

    /** 删除。 */
    public static final String DELETE = "DELETE";

    /** 所有合法操作类型。 */
    private static final Set<String> ALL = Set.of(CREATE, UPDATE, ENABLE, DISABLE, DELETE);

    /** 工具类不允许实例化。 */
    private ApprovalOperationType() {
    }

    /**
     * 判断操作类型是否合法。
     *
     * @param operationType 操作类型
     * @return 合法时返回 {@code true}
     */
    public static boolean isSupported(String operationType) {
        return ALL.contains(operationType);
    }
}
