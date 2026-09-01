package cn.nihility.rbac.chat.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.chat.constant.ConversationMemberStatus;
import cn.nihility.rbac.chat.constant.ConversationType;
import cn.nihility.rbac.chat.dto.SendMessageResult;
import cn.nihility.rbac.chat.entity.ChatMessageEntity;
import cn.nihility.rbac.chat.entity.ChatMessageOfflineEntity;
import cn.nihility.rbac.chat.entity.ConversationEntity;
import cn.nihility.rbac.chat.entity.ConversationMemberEntity;
import cn.nihility.rbac.chat.mapper.ChatMessageMapper;
import cn.nihility.rbac.chat.mapper.ChatMessageOfflineMapper;
import cn.nihility.rbac.chat.mapper.ConversationMemberMapper;
import cn.nihility.rbac.chat.service.ConversationService;
import cn.nihility.rbac.chat.service.SensitiveWordFilterService;
import cn.nihility.rbac.chat.service.support.AhoCorasickAutomaton;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.user.service.UserDisplayService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link ChatMessageServiceImpl} 的单元测试，重点覆盖 msgId 幂等短路、发送者/接收者校验、
 * 群聊非成员拒绝、在线/离线接收方分流等分支逻辑。
 */
@ExtendWith(MockitoExtension.class)
class ChatMessageServiceImplTest {

    /** 被测服务的消息数据访问依赖。 */
    @Mock
    private ChatMessageMapper chatMessageMapper;

    /** 被测服务的离线消息队列数据访问依赖。 */
    @Mock
    private ChatMessageOfflineMapper chatMessageOfflineMapper;

    /** 被测服务的会话成员数据访问依赖。 */
    @Mock
    private ConversationMemberMapper conversationMemberMapper;

    /** 被测服务的会话管理依赖。 */
    @Mock
    private ConversationService conversationService;

    /** 被测服务的敏感词过滤依赖。 */
    @Mock
    private SensitiveWordFilterService sensitiveWordFilterService;

    /** 被测服务的用户展示名解析依赖。 */
    @Mock
    private UserDisplayService userDisplayService;

    /** 被测服务实例。 */
    private ChatMessageServiceImpl chatMessageService;

    @BeforeEach
    void setUp() {
        chatMessageService = new ChatMessageServiceImpl(chatMessageMapper, chatMessageOfflineMapper,
                conversationMemberMapper, conversationService, sensitiveWordFilterService, userDisplayService);
    }

    /** 发送者不能是接收者本人。 */
    @Test
    void sendSingleMessage_shouldRejectSendingToSelf() {
        assertThatThrownBy(() -> chatMessageService.sendSingleMessage(1L, 1L, "msg-1", 1, "hi", id -> true))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("自己");
    }

    /** 相同 msgId 重复提交应直接返回幂等结果，不重复落库、不再判断在线状态。 */
    @Test
    void sendSingleMessage_shouldShortCircuitOnDuplicateMsgId() {
        ChatMessageEntity existing = ChatMessageEntity.builder().id(100L).msgId("msg-1").build();
        when(chatMessageMapper.selectOne(any())).thenReturn(existing);

        SendMessageResult result = chatMessageService.sendSingleMessage(1L, 2L, "msg-1", 1, "hi", id -> true);

        assertThat(result.isDuplicate()).isTrue();
        assertThat(result.getMessage()).isSameAs(existing);
        assertThat(result.getRecipients()).isEmpty();
        verify(conversationService, never()).getOrCreateSingleConversation(anyLong(), anyLong());
        verify(chatMessageMapper, never()).insert(any(ChatMessageEntity.class));
    }

    /** 接收方在线时应标记 online = true，不写入离线队列。 */
    @Test
    void sendSingleMessage_shouldNotWriteOfflineWhenRecipientOnline() {
        when(chatMessageMapper.selectOne(any())).thenReturn(null);
        when(conversationService.getOrCreateSingleConversation(1L, 2L))
                .thenReturn(ConversationEntity.builder().id(10L).conversationType(ConversationType.SINGLE).build());
        when(conversationService.nextSeq(10L)).thenReturn(1L);
        when(sensitiveWordFilterService.filter(anyString()))
                .thenReturn(new AhoCorasickAutomaton.FilterResult("hi", false));

        SendMessageResult result = chatMessageService.sendSingleMessage(1L, 2L, "msg-2", 1, "hi", id -> true);

        assertThat(result.isDuplicate()).isFalse();
        assertThat(result.getRecipients()).hasSize(1);
        assertThat(result.getRecipients().get(0).online()).isTrue();
        verify(chatMessageOfflineMapper, never()).insert(any(ChatMessageOfflineEntity.class));
        verify(chatMessageMapper, times(1)).insert(any(ChatMessageEntity.class));
    }

    /** 接收方离线时应标记 online = false，并写入离线消息队列。 */
    @Test
    void sendSingleMessage_shouldWriteOfflineWhenRecipientOffline() {
        when(chatMessageMapper.selectOne(any())).thenReturn(null);
        when(conversationService.getOrCreateSingleConversation(1L, 2L))
                .thenReturn(ConversationEntity.builder().id(10L).conversationType(ConversationType.SINGLE).build());
        when(conversationService.nextSeq(10L)).thenReturn(1L);
        when(sensitiveWordFilterService.filter(anyString()))
                .thenReturn(new AhoCorasickAutomaton.FilterResult("hi", false));

        SendMessageResult result = chatMessageService.sendSingleMessage(1L, 2L, "msg-3", 1, "hi", id -> false);

        assertThat(result.getRecipients().get(0).online()).isFalse();
        verify(chatMessageOfflineMapper, times(1)).insert(any(ChatMessageOfflineEntity.class));
    }

    /** 非群成员发送群聊消息应被拒绝，不落库不投递。 */
    @Test
    void sendGroupMessage_shouldRejectNonMember() {
        when(chatMessageMapper.selectOne(any())).thenReturn(null);
        when(conversationMemberMapper.selectList(any())).thenReturn(List.of(
                ConversationMemberEntity.builder().conversationId(5L).userId(2L)
                        .status(ConversationMemberStatus.NORMAL).build()));

        assertThatThrownBy(() -> chatMessageService.sendGroupMessage(1L, 5L, "msg-4", 1, "hi", id -> true))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("群聊成员");
        verify(chatMessageMapper, never()).insert(any(ChatMessageEntity.class));
    }

    /** 群聊消息只落库一次，接收方按在线状态分别标记，发送者不计入接收方列表。 */
    @Test
    void sendGroupMessage_shouldSplitRecipientsByOnlineStatus() {
        when(chatMessageMapper.selectOne(any())).thenReturn(null);
        when(conversationMemberMapper.selectList(any())).thenReturn(List.of(
                ConversationMemberEntity.builder().conversationId(5L).userId(1L)
                        .status(ConversationMemberStatus.NORMAL).build(),
                ConversationMemberEntity.builder().conversationId(5L).userId(2L)
                        .status(ConversationMemberStatus.NORMAL).build(),
                ConversationMemberEntity.builder().conversationId(5L).userId(3L)
                        .status(ConversationMemberStatus.NORMAL).build()));
        when(conversationService.nextSeq(5L)).thenReturn(1L);
        when(sensitiveWordFilterService.filter(anyString()))
                .thenReturn(new AhoCorasickAutomaton.FilterResult("hi", false));

        SendMessageResult result = chatMessageService.sendGroupMessage(1L, 5L, "msg-5", 1, "hi",
                id -> id.equals(2L));

        assertThat(result.getRecipients()).hasSize(2);
        assertThat(result.getRecipients().stream().noneMatch(recipient -> recipient.userId().equals(1L))).isTrue();
        verify(chatMessageMapper, times(1)).insert(any(ChatMessageEntity.class));
        verify(chatMessageOfflineMapper, times(1)).insert(any(ChatMessageOfflineEntity.class));
    }
}
