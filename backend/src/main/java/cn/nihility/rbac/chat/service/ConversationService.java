package cn.nihility.rbac.chat.service;

import cn.nihility.rbac.chat.dto.ConversationMemberVO;
import cn.nihility.rbac.chat.dto.ConversationVO;
import cn.nihility.rbac.chat.dto.CreateGroupConversationRequest;
import cn.nihility.rbac.chat.entity.ConversationEntity;
import java.util.List;

/**
 * 会话（单聊/群聊）与会话成员管理业务逻辑接口（chat-messaging spec"群聊会话与成员管理"
 * 需求）。会话内消息序号（{@link #nextSeq}）的取号流程见 design.md Decision 7。
 */
public interface ConversationService {

    /**
     * 查询当前用户参与的全部会话（单聊+群聊），按最近消息时间倒序。
     *
     * @param userId 当前登录用户 id
     * @return 会话列表
     */
    List<ConversationVO> listMyConversations(Long userId);

    /**
     * 创建群聊，创建者自动成为群主并计入初始成员。
     *
     * @param ownerId 创建者用户 id
     * @param request 创建请求
     * @return 创建后的会话详情
     */
    ConversationVO createGroupConversation(Long ownerId, CreateGroupConversationRequest request);

    /**
     * 查询群成员列表，调用方必须是该会话的当前成员。
     *
     * @param userId         当前登录用户 id
     * @param conversationId 会话 id
     * @return 成员列表
     */
    List<ConversationMemberVO> listMembers(Long userId, Long conversationId);

    /**
     * 添加群成员，已在群内（含已退出后重新加入）的成员按幂等处理。
     *
     * @param userId         当前登录用户 id，必须是该会话的当前成员
     * @param conversationId 会话 id
     * @param memberUserIds  待添加的成员用户 id 列表
     */
    void addMembers(Long userId, Long conversationId, List<Long> memberUserIds);

    /**
     * 移除群成员或主动退出群聊：{@code targetUserId} 等于 {@code userId} 时为主动退出，
     * 任何当前成员都可以操作；否则为移除他人，仅群主可操作，且不能移除群主自己。
     *
     * @param userId         当前登录用户 id
     * @param conversationId 会话 id
     * @param targetUserId   目标用户 id
     */
    void removeMember(Long userId, Long conversationId, Long targetUserId);

    /**
     * 查询或创建两个用户之间的单聊会话（不存在则创建）。
     *
     * @param userId1 用户 1 id
     * @param userId2 用户 2 id
     * @return 单聊会话实体
     */
    ConversationEntity getOrCreateSingleConversation(Long userId1, Long userId2);

    /**
     * 在事务内为指定会话取一个新的会话级消息序号（{@code SELECT ... FOR UPDATE} 行锁 +
     * 原子自增），调用方必须已处于事务上下文中，且与消息落库在同一事务内完成
     * （design.md Decision 7）。
     *
     * @param conversationId 会话 id
     * @return 本次取到的序号
     */
    long nextSeq(Long conversationId);

    /**
     * 断言指定用户是该会话的当前成员（状态正常），否则抛出业务异常。
     *
     * @param conversationId 会话 id
     * @param userId         用户 id
     */
    void assertActiveMember(Long conversationId, Long userId);
}
