package cn.nihility.rbac.chat.service;

import cn.nihility.rbac.chat.dto.ChatMessageVO;
import cn.nihility.rbac.chat.dto.SendMessageResult;
import cn.nihility.rbac.common.result.PageResult;
import java.util.function.Predicate;

/**
 * 消息发送与历史查询业务逻辑接口（chat-messaging spec"单聊消息发送与路由"/"群聊消息发送与
 * 路由"/"消息 ACK 确认与幂等重发"/"会话内消息顺序保证"需求）。发送方法不直接依赖 Netty
 * {@code Channel} 类型，"接收方当前是否在线"由调用方（网关业务 Handler）通过
 * {@code onlineChecker} 回调传入，保持本服务框架无关、便于单元测试。
 */
public interface ChatMessageService {

    /**
     * 发送单聊消息：校验发送者不能是接收者本人 → msgId 幂等命中检查 → 敏感词过滤 →
     * 会话内取号并落库 → 按 {@code onlineChecker} 判断接收方在线状态，离线则在同一事务内
     * 写入离线消息队列。
     *
     * @param senderId      发送者用户 id
     * @param toUserId      接收者用户 id
     * @param msgId         客户端生成的消息幂等 id
     * @param msgType       消息内容类型
     * @param content       原始消息内容（落库前会经过敏感词过滤）
     * @param onlineChecker 判断指定用户当前是否存在在线连接的回调
     * @return 发送处理结果
     */
    SendMessageResult sendSingleMessage(Long senderId, Long toUserId, String msgId, Integer msgType, String content,
            Predicate<Long> onlineChecker);

    /**
     * 发送群聊消息：校验发送者是当前群成员 → msgId 幂等命中检查 → 敏感词过滤 → 会话内取号
     * 并落库一次 → 按 {@code onlineChecker} 逐一判断其余成员在线状态，离线成员各在同一事务内
     * 写入一条离线消息队列记录。
     *
     * @param senderId       发送者用户 id
     * @param conversationId 群聊会话 id
     * @param msgId          客户端生成的消息幂等 id
     * @param msgType        消息内容类型
     * @param content        原始消息内容（落库前会经过敏感词过滤）
     * @param onlineChecker  判断指定用户当前是否存在在线连接的回调
     * @return 发送处理结果
     */
    SendMessageResult sendGroupMessage(Long senderId, Long conversationId, String msgId, Integer msgType,
            String content, Predicate<Long> onlineChecker);

    /**
     * 按会话游标分页查询历史消息，调用方必须是该会话的当前成员；结果按
     * {@code conversationSeq} 降序（最新在前）排列，与"向历史翻页"的滚动加载场景对应，
     * 前端展示时需要自行反转为时间正序。
     *
     * @param userId         当前登录用户 id
     * @param conversationId 会话 id
     * @param beforeSeq      游标：只返回 {@code conversationSeq} 小于该值的历史消息，
     *                       {@code null} 表示查最新一页
     * @param pageSize       每页条数，{@code null}/非正数时使用默认值 20，上限 100
     * @return 历史消息分页结果
     */
    PageResult<ChatMessageVO> listMessages(Long userId, Long conversationId, Long beforeSeq, Integer pageSize);
}
