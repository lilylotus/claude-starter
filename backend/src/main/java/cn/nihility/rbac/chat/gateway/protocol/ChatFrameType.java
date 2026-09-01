package cn.nihility.rbac.chat.gateway.protocol;

/**
 * 聊天网关自定义应用层协议的消息类型（帧头 1 字节，design.md Decision 2）。取值范围
 * 0x01~0x7F，预留 0x80 以上区间供后续阶段（chat-cluster/chat-e2ee）扩展，避免与本阶段
 * 已定义类型冲突。
 */
public enum ChatFrameType {

    /** 认证帧（客户端 -> 服务端），body 携带 accessKey。 */
    LOGIN((byte) 0x01),

    /** 认证结果帧（服务端 -> 客户端）。 */
    LOGIN_ACK((byte) 0x02),

    /** 心跳帧（客户端 -> 服务端）。 */
    HEARTBEAT((byte) 0x03),

    /** 心跳回执帧（服务端 -> 客户端）。 */
    HEARTBEAT_ACK((byte) 0x04),

    /** 单聊消息发送帧（客户端 -> 服务端）。 */
    CHAT_SINGLE((byte) 0x05),

    /** 群聊消息发送帧（客户端 -> 服务端）。 */
    CHAT_GROUP((byte) 0x06),

    /** 消息推送帧（服务端 -> 客户端），含实时投递与离线补偿推送两种场景。 */
    MESSAGE_PUSH((byte) 0x07),

    /** 消息确认帧（服务端 -> 客户端），对客户端发送的带 msgId 消息的处理结果回执。 */
    ACK((byte) 0x08),

    /** 错误帧（服务端 -> 客户端）。 */
    ERROR((byte) 0x09);

    /** 协议帧头中的消息类型字节值。 */
    private final byte code;

    ChatFrameType(byte code) {
        this.code = code;
    }

    /**
     * 获取协议帧头中的消息类型字节值。
     *
     * @return 消息类型字节值
     */
    public byte getCode() {
        return code;
    }

    /**
     * 按字节值反查消息类型。
     *
     * @param code 消息类型字节值
     * @return 对应的消息类型，无匹配时返回 {@code null}
     */
    public static ChatFrameType fromCode(byte code) {
        for (ChatFrameType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        return null;
    }
}
