package cn.nihility.rbac.chat.gateway.support;

import java.time.LocalDateTime;

/**
 * msgId 去重短路缓存的缓存值：重建 ACK 帧所需的最小字段集合（design.md Decision 6）。
 *
 * @param msgId           客户端生成的消息幂等 id
 * @param conversationId  消息所属会话 id
 * @param conversationSeq 会话内消息序号
 * @param sendTime        服务端记录的发送时间
 */
public record ChatAckCacheEntry(String msgId, Long conversationId, Long conversationSeq, LocalDateTime sendTime) {
}
