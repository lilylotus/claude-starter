package cn.nihility.rbac.chat.gateway.protocol.body;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * {@code ACK} 帧消息体（服务端 -> 客户端）：对客户端发送的带 {@code msgId} 消息
 * （{@code CHAT_SINGLE}/{@code CHAT_GROUP}）的处理结果回执，重复提交的 {@code msgId}
 * 同样返回 ACK（幂等，design.md Decision 6）。
 */
@Getter
@Setter
@NoArgsConstructor
public class AckFrameBody {

    /** 客户端生成的消息幂等 id。 */
    private String msgId;

    /** 消息所属会话 id（单聊场景下由服务端解析/创建后回填，客户端发送时不需要预先知道）。 */
    private Long conversationId;

    /** 会话内消息序号。 */
    private Long conversationSeq;

    /** 服务端记录的发送时间。 */
    private LocalDateTime sendTime;

    private AckFrameBody(String msgId, Long conversationId, Long conversationSeq, LocalDateTime sendTime) {
        this.msgId = msgId;
        this.conversationId = conversationId;
        this.conversationSeq = conversationSeq;
        this.sendTime = sendTime;
    }

    /**
     * 构造一个 ACK 帧消息体。
     *
     * @param msgId           客户端生成的消息幂等 id
     * @param conversationId  消息所属会话 id
     * @param conversationSeq 会话内消息序号
     * @param sendTime        服务端记录的发送时间
     * @return ACK 帧消息体
     */
    public static AckFrameBody of(String msgId, Long conversationId, Long conversationSeq, LocalDateTime sendTime) {
        return new AckFrameBody(msgId, conversationId, conversationSeq, sendTime);
    }
}
