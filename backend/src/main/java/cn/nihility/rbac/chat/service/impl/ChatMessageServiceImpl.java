package cn.nihility.rbac.chat.service.impl;

import cn.nihility.rbac.chat.constant.ChatErrorCode;
import cn.nihility.rbac.chat.constant.ChatMessageType;
import cn.nihility.rbac.chat.constant.ConversationMemberStatus;
import cn.nihility.rbac.chat.dto.ChatMessageVO;
import cn.nihility.rbac.chat.dto.MessageRecipient;
import cn.nihility.rbac.chat.dto.SendMessageResult;
import cn.nihility.rbac.chat.entity.ChatMessageEntity;
import cn.nihility.rbac.chat.entity.ChatMessageOfflineEntity;
import cn.nihility.rbac.chat.entity.ConversationEntity;
import cn.nihility.rbac.chat.entity.ConversationMemberEntity;
import cn.nihility.rbac.chat.mapper.ChatMessageMapper;
import cn.nihility.rbac.chat.mapper.ChatMessageOfflineMapper;
import cn.nihility.rbac.chat.mapper.ConversationMemberMapper;
import cn.nihility.rbac.chat.mapstruct.ChatConvert;
import cn.nihility.rbac.chat.service.ChatMessageService;
import cn.nihility.rbac.chat.service.ConversationService;
import cn.nihility.rbac.chat.service.SensitiveWordFilterService;
import cn.nihility.rbac.chat.service.support.AhoCorasickAutomaton;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.common.result.PageResult;
import cn.nihility.rbac.user.service.UserDisplayService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 消息发送与历史查询业务逻辑实现。
 */
@Service
@RequiredArgsConstructor
public class ChatMessageServiceImpl implements ChatMessageService {

    /** 消息数据访问接口。 */
    private final ChatMessageMapper chatMessageMapper;

    /** 离线消息队列数据访问接口。 */
    private final ChatMessageOfflineMapper chatMessageOfflineMapper;

    /** 会话成员数据访问接口。 */
    private final ConversationMemberMapper conversationMemberMapper;

    /** 会话管理业务逻辑接口，用于会话内取号与成员校验。 */
    private final ConversationService conversationService;

    /** 敏感词过滤服务。 */
    private final SensitiveWordFilterService sensitiveWordFilterService;

    /** 用户展示名批量解析服务，用于历史消息的发送者展示名回填。 */
    private final UserDisplayService userDisplayService;

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public SendMessageResult sendSingleMessage(Long senderId, Long toUserId, String msgId, Integer msgType,
            String content, Predicate<Long> onlineChecker) {
        if (Objects.equals(senderId, toUserId)) {
            throw new BusinessException(ChatErrorCode.INVALID_FRAME, "不能向自己发送单聊消息");
        }
        Optional<ChatMessageEntity> existing = findByMsgId(msgId);
        if (existing.isPresent()) {
            return SendMessageResult.duplicate(existing.get());
        }

        ConversationEntity conversation = conversationService.getOrCreateSingleConversation(senderId, toUserId);
        ChatMessageEntity message;
        try {
            message = insertMessage(conversation.getId(), senderId, msgId, msgType, content);
        } catch (DuplicateKeyException e) {
            return SendMessageResult.duplicate(findByMsgId(msgId).orElseThrow(() -> e));
        }

        boolean online = onlineChecker.test(toUserId);
        if (!online) {
            writeOffline(message.getId(), toUserId);
        }
        return SendMessageResult.of(message, List.of(new MessageRecipient(toUserId, online)));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public SendMessageResult sendGroupMessage(Long senderId, Long conversationId, String msgId, Integer msgType,
            String content, Predicate<Long> onlineChecker) {
        Optional<ChatMessageEntity> existing = findByMsgId(msgId);
        if (existing.isPresent()) {
            return SendMessageResult.duplicate(existing.get());
        }

        List<ConversationMemberEntity> members = conversationMemberMapper.selectList(
                new LambdaQueryWrapper<ConversationMemberEntity>()
                        .eq(ConversationMemberEntity::getConversationId, conversationId)
                        .eq(ConversationMemberEntity::getStatus, ConversationMemberStatus.NORMAL));
        boolean senderIsMember = members.stream().anyMatch(member -> Objects.equals(member.getUserId(), senderId));
        if (!senderIsMember) {
            throw new BusinessException(ChatErrorCode.FORBIDDEN, "您不是该群聊成员，无法发送消息");
        }

        ChatMessageEntity message;
        try {
            message = insertMessage(conversationId, senderId, msgId, msgType, content);
        } catch (DuplicateKeyException e) {
            return SendMessageResult.duplicate(findByMsgId(msgId).orElseThrow(() -> e));
        }

        List<MessageRecipient> recipients = new ArrayList<>();
        for (ConversationMemberEntity member : members) {
            if (Objects.equals(member.getUserId(), senderId)) {
                continue;
            }
            boolean online = onlineChecker.test(member.getUserId());
            if (!online) {
                writeOffline(message.getId(), member.getUserId());
            }
            recipients.add(new MessageRecipient(member.getUserId(), online));
        }
        return SendMessageResult.of(message, recipients);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResult<ChatMessageVO> listMessages(Long userId, Long conversationId, Long beforeSeq,
            Integer pageSize) {
        conversationService.assertActiveMember(conversationId, userId);
        int size = (pageSize == null || pageSize <= 0) ? 20 : Math.min(pageSize, 100);

        LambdaQueryWrapper<ChatMessageEntity> wrapper = new LambdaQueryWrapper<ChatMessageEntity>()
                .eq(ChatMessageEntity::getConversationId, conversationId)
                .lt(beforeSeq != null, ChatMessageEntity::getConversationSeq, beforeSeq)
                .orderByDesc(ChatMessageEntity::getConversationSeq);
        Page<ChatMessageEntity> page = chatMessageMapper.selectPage(new Page<>(1, size), wrapper);

        List<ChatMessageVO> records = ChatConvert.INSTANCE.toMessageVOList(page.getRecords());
        if (!records.isEmpty()) {
            Set<String> senderKeys = page.getRecords().stream()
                    .map(entity -> String.valueOf(entity.getSenderId()))
                    .collect(Collectors.toSet());
            Map<String, String> displayNames = userDisplayService.resolveDisplayNames(senderKeys);
            for (int i = 0; i < records.size(); i++) {
                String key = String.valueOf(page.getRecords().get(i).getSenderId());
                records.get(i).setSenderName(displayNames.getOrDefault(key, "未知用户"));
            }
        }
        return PageResult.of(records, page);
    }

    /**
     * 按 {@code msgId} 查询已存在的消息记录。
     *
     * @param msgId 客户端生成的消息幂等 id
     * @return 已存在的消息记录，不存在时为空
     */
    private Optional<ChatMessageEntity> findByMsgId(String msgId) {
        return Optional.ofNullable(chatMessageMapper.selectOne(
                new LambdaQueryWrapper<ChatMessageEntity>().eq(ChatMessageEntity::getMsgId, msgId)));
    }

    /**
     * 对消息内容做敏感词过滤、在同一事务内取会话级序号并落库。
     *
     * @param conversationId 会话 id
     * @param senderId       发送者用户 id
     * @param msgId          客户端生成的消息幂等 id
     * @param msgType        消息内容类型
     * @param content        原始消息内容
     * @return 落库后的消息实体
     * @throws DuplicateKeyException 并发场景下 {@code msgId} 唯一索引冲突，
     *                                调用方需捕获并按幂等处理（design.md Decision 6）
     */
    private ChatMessageEntity insertMessage(Long conversationId, Long senderId, String msgId, Integer msgType,
            String content) {
        AhoCorasickAutomaton.FilterResult filterResult = sensitiveWordFilterService.filter(content);
        long seq = conversationService.nextSeq(conversationId);
        LocalDateTime now = LocalDateTime.now();
        String operator = Objects.toString(senderId, null);
        ChatMessageEntity message = ChatMessageEntity.builder()
                .msgId(msgId)
                .conversationId(conversationId)
                .conversationSeq(seq)
                .senderId(senderId)
                .msgType(msgType == null ? ChatMessageType.TEXT : msgType)
                .content(filterResult.content())
                .filtered(filterResult.hit())
                .sendTime(now)
                .createBy(operator)
                .createTime(now)
                .updateBy(operator)
                .updateTime(now)
                .build();
        chatMessageMapper.insert(message);
        return message;
    }

    /**
     * 写入一条离线消息队列记录。
     *
     * @param messageId  消息 id
     * @param receiverId 接收者用户 id
     */
    private void writeOffline(Long messageId, Long receiverId) {
        LocalDateTime now = LocalDateTime.now();
        ChatMessageOfflineEntity offline = ChatMessageOfflineEntity.builder()
                .messageId(messageId)
                .receiverId(receiverId)
                .delivered(false)
                .createTime(now)
                .updateTime(now)
                .build();
        chatMessageOfflineMapper.insert(offline);
    }
}
