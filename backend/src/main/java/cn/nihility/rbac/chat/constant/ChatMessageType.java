package cn.nihility.rbac.chat.constant;

/**
 * 消息内容类型常量，对应 {@code tab_chat_message.msg_type}。本阶段仅支持文本消息，
 * 其余取值为后续阶段（图片/文件等富媒体消息）预留占位，不做实际处理。
 */
public final class ChatMessageType {

    /** 文本消息，本阶段唯一实际支持的类型。 */
    public static final int TEXT = 1;

    /** 图片消息（预留占位，本阶段不做内容审核/存储对接）。 */
    public static final int IMAGE = 2;

    /**
     * 工具类不允许实例化。
     */
    private ChatMessageType() {
    }
}
