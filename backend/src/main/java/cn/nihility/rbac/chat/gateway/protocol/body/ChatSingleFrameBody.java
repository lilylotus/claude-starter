package cn.nihility.rbac.chat.gateway.protocol.body;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * {@code CHAT_SINGLE} 帧消息体（客户端 -> 服务端）：发送单聊消息。单聊端到端加密后，
 * {@code content} 承载客户端生成的密文信封（含 ciphertext/nonce/ratchetHeader 等，具体
 * 结构由客户端定义），服务端只做透传/落库，不解析内部结构、不再执行敏感词过滤（见
 * chat-end-to-end-encryption change design.md Decision 1/5）。
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

    /** 消息内容：客户端生成的密文信封（服务端不解析，只透传/落库）。 */
    private String content;

    /** 发送方身份公钥指纹快照，服务端原样落库到 {@code sender_identity_key_fingerprint}
     *  列，不做业务判断，供接收端在解密前做安全码比对。 */
    private String senderIdentityKeyFingerprint;
}
