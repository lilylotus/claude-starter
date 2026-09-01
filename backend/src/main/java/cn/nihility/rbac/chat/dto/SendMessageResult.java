package cn.nihility.rbac.chat.dto;

import cn.nihility.rbac.chat.entity.ChatMessageEntity;
import java.util.List;
import lombok.Getter;

/**
 * 单聊/群聊消息发送的处理结果，供网关业务 Handler 据此回复 ACK 帧并向在线接收方推送
 * {@code MESSAGE_PUSH} 帧。{@code duplicate = true} 时 {@code recipients} 恒为空列表
 * ——重复提交的 {@code msgId} 只需要重新回一次 ACK，不需要再次投递（design.md Decision 6）。
 */
@Getter
public class SendMessageResult {

    /** 落库后的消息实体（重复提交时为此前已存在的那一条）。 */
    private final ChatMessageEntity message;

    /** 是否为 {@code msgId} 重复提交（幂等命中）。 */
    private final boolean duplicate;

    /** 需要处理投递的接收方列表（不含发送者自己），重复提交时为空列表。 */
    private final List<MessageRecipient> recipients;

    private SendMessageResult(ChatMessageEntity message, boolean duplicate, List<MessageRecipient> recipients) {
        this.message = message;
        this.duplicate = duplicate;
        this.recipients = recipients;
    }

    /**
     * 构造一个新消息的发送结果。
     *
     * @param message    落库后的消息实体
     * @param recipients 接收方列表（不含发送者）
     * @return 发送结果
     */
    public static SendMessageResult of(ChatMessageEntity message, List<MessageRecipient> recipients) {
        return new SendMessageResult(message, false, recipients);
    }

    /**
     * 构造一个幂等重复提交的发送结果。
     *
     * @param message 此前已存在的消息实体
     * @return 发送结果
     */
    public static SendMessageResult duplicate(ChatMessageEntity message) {
        return new SendMessageResult(message, true, List.of());
    }
}
