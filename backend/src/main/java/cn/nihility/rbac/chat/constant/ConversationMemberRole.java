package cn.nihility.rbac.chat.constant;

/**
 * 会话成员角色常量，对应 {@code tab_chat_conversation_member.role}。单聊场景下两条成员
 * 记录均使用 {@link #MEMBER}，仅群聊区分群主与普通成员。
 */
public final class ConversationMemberRole {

    /** 群主（群聊创建者），拥有移除其他成员的权限。 */
    public static final int OWNER = 1;

    /** 普通成员。 */
    public static final int MEMBER = 2;

    /**
     * 工具类不允许实例化。
     */
    private ConversationMemberRole() {
    }
}
