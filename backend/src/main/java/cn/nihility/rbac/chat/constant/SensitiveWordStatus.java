package cn.nihility.rbac.chat.constant;

/**
 * 敏感词状态常量，对应 {@code tab_chat_sensitive_word.status}。删除词条为物理删除，
 * 不使用逻辑删除状态，因此本类只有启用/停用两个取值。
 */
public final class SensitiveWordStatus {

    /** 启用，参与内存 AC 自动机构建，命中后按策略拦截/替换消息内容。 */
    public static final int ENABLED = 2000;

    /** 停用，不参与内存 AC 自动机构建。 */
    public static final int DISABLED = 3000;

    /**
     * 工具类不允许实例化。
     */
    private SensitiveWordStatus() {
    }
}
