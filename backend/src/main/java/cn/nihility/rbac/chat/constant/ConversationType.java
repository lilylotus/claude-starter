package cn.nihility.rbac.chat.constant;

/**
 * 会话类型常量，对应 {@code tab_chat_conversation.conversation_type}。
 */
public final class ConversationType {

    /** 单聊会话，固定两名成员。 */
    public static final int SINGLE = 1;

    /** 群聊会话。 */
    public static final int GROUP = 2;

    /**
     * 工具类不允许实例化。
     */
    private ConversationType() {
    }
}
