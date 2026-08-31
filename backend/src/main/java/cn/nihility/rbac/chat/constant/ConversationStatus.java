package cn.nihility.rbac.chat.constant;

/**
 * 会话状态常量，对应 {@code tab_chat_conversation.status}。本阶段暂未提供解散群聊入口，
 * {@link #DISSOLVED} 为预留取值。
 */
public final class ConversationStatus {

    /** 正常。 */
    public static final int NORMAL = 2000;

    /** 已解散（预留，本阶段未提供对应操作入口）。 */
    public static final int DISSOLVED = 3000;

    /**
     * 工具类不允许实例化。
     */
    private ConversationStatus() {
    }
}
