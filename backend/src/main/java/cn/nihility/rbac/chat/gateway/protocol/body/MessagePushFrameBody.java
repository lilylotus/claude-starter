package cn.nihility.rbac.chat.gateway.protocol.body;

import cn.nihility.rbac.chat.entity.ChatMessageEntity;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * {@code MESSAGE_PUSH} 帧消息体（服务端 -> 客户端）：实时投递与离线补偿推送共用同一结构，
 * {@code offline} 字段区分场景，供前端展示"离线消息补偿"提示或做去重合并。
 */
@Getter
@Setter
@NoArgsConstructor
public class MessagePushFrameBody {

    /** 客户端生成的消息幂等 id。 */
    private String msgId;

    /** 消息所属会话 id。 */
    private Long conversationId;

    /** 会话内消息序号，客户端应按该字段排序展示。 */
    private Long conversationSeq;

    /** 发送者用户 id。 */
    private Long senderId;

    /** 消息内容类型。 */
    private Integer msgType;

    /** 消息内容（敏感词过滤/替换后的内容）。 */
    private String content;

    /** 发送时间。 */
    private LocalDateTime sendTime;

    /** 是否为离线补偿推送（{@code true}=用户上线后补偿推送，{@code false}=实时投递）。 */
    private boolean offline;

    /**
     * 基于消息实体构造推送帧消息体。
     *
     * @param message 消息实体
     * @param offline 是否为离线补偿推送
     * @return 推送帧消息体
     */
    public static MessagePushFrameBody from(ChatMessageEntity message, boolean offline) {
        MessagePushFrameBody body = new MessagePushFrameBody();
        body.setMsgId(message.getMsgId());
        body.setConversationId(message.getConversationId());
        body.setConversationSeq(message.getConversationSeq());
        body.setSenderId(message.getSenderId());
        body.setMsgType(message.getMsgType());
        body.setContent(message.getContent());
        body.setSendTime(message.getSendTime());
        body.setOffline(offline);
        return body;
    }
}
