package cn.nihility.rbac.chat.constant;

/**
 * 会话成员状态常量，对应 {@code tab_chat_conversation_member.status}。
 */
public final class ConversationMemberStatus {

    /** 在会话中（正常成员）。 */
    public static final int NORMAL = 2000;

    /** 已退出/被移出，不再接收该会话的实时推送或离线补偿。 */
    public static final int LEFT = 3000;

    /**
     * 工具类不允许实例化。
     */
    private ConversationMemberStatus() {
    }
}
