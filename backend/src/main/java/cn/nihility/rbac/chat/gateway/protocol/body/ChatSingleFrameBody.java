package cn.nihility.rbac.chat.gateway.protocol.body;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * {@code CHAT_SINGLE} 帧消息体（客户端 -> 服务端）：发送单聊消息。
 */
@Getter
@Setter
@NoArgsConstructor
public class ChatSingleFrameBody {

    /** 客户端生成的消息幂等 id，用于 ACK 确认与重发去重。 */
    private String msgId;

    /** 接收方用户 id。 */
    private Long toUserId;

    /** 消息内容类型，见 {@link cn.nihility.rbac.chat.constant.ChatMessageType}；为空时按文本处理。 */
    private Integer msgType;

    /** 消息内容（明文，落库前经过敏感词过滤）。 */
    private String content;
}
