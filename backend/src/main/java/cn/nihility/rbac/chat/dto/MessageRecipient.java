package cn.nihility.rbac.chat.dto;

/**
 * 一条消息的接收方及其在发送处理这一时刻的在线状态快照，供网关业务 Handler 决定
 * 是否需要实时推送（在线）或已经在同一事务内写入离线队列（离线，见
 * {@link cn.nihility.rbac.chat.service.ChatMessageService}）。
 *
 * @param userId 接收方用户 id
 * @param online 处理消息时该用户是否存在至少一个在线连接
 */
public record MessageRecipient(Long userId, boolean online) {
}
