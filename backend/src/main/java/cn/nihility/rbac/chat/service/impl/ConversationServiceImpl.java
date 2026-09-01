package cn.nihility.rbac.chat.service.impl;

import cn.nihility.rbac.chat.constant.ConversationMemberRole;
import cn.nihility.rbac.chat.constant.ConversationMemberStatus;
import cn.nihility.rbac.chat.constant.ConversationStatus;
import cn.nihility.rbac.chat.constant.ConversationType;
import cn.nihility.rbac.chat.dto.ConversationMemberVO;
import cn.nihility.rbac.chat.dto.ConversationVO;
import cn.nihility.rbac.chat.dto.CreateGroupConversationRequest;
import cn.nihility.rbac.chat.entity.ChatMessageEntity;
import cn.nihility.rbac.chat.entity.ConversationEntity;
import cn.nihility.rbac.chat.entity.ConversationMemberEntity;
import cn.nihility.rbac.chat.mapper.ChatMessageMapper;
import cn.nihility.rbac.chat.mapper.ConversationMapper;
import cn.nihility.rbac.chat.mapper.ConversationMemberMapper;
import cn.nihility.rbac.chat.mapstruct.ChatConvert;
import cn.nihility.rbac.chat.service.ConversationService;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.user.service.UserDisplayService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 会话与会话成员管理业务逻辑实现。
 */
@Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {

    /** 会话数据访问接口。 */
    private final ConversationMapper conversationMapper;

    /** 会话成员数据访问接口。 */
    private final ConversationMemberMapper conversationMemberMapper;

    /** 消息数据访问接口，用于会话列表的"最近消息摘要"。 */
    private final ChatMessageMapper chatMessageMapper;

    /** 用户展示名批量解析服务。 */
    private final UserDisplayService userDisplayService;

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ConversationVO> listMyConversations(Long userId) {
        List<ConversationMemberEntity> myMemberships = conversationMemberMapper.selectList(
                new LambdaQueryWrapper<ConversationMemberEntity>()
                        .eq(ConversationMemberEntity::getUserId, userId)
                        .eq(ConversationMemberEntity::getStatus, ConversationMemberStatus.NORMAL));
        if (myMemberships.isEmpty()) {
            return List.of();
        }
        List<Long> conversationIds = myMemberships.stream().map(ConversationMemberEntity::getConversationId).toList();

        Map<Long, ConversationEntity> conversationById = conversationMapper.selectByIds(conversationIds).stream()
                .collect(Collectors.toMap(ConversationEntity::getId, c -> c));

        List<ConversationMemberEntity> allMembers = conversationMemberMapper.selectList(
                new LambdaQueryWrapper<ConversationMemberEntity>()
                        .in(ConversationMemberEntity::getConversationId, conversationIds)
                        .eq(ConversationMemberEntity::getStatus, ConversationMemberStatus.NORMAL));
        Map<Long, List<ConversationMemberEntity>> membersByConversation = allMembers.stream()
                .collect(Collectors.groupingBy(ConversationMemberEntity::getConversationId));

        Map<Long, ChatMessageEntity> lastMessageByConversation = chatMessageMapper
                .selectLatestByConversationIds(conversationIds).stream()
                .collect(Collectors.toMap(ChatMessageEntity::getConversationId, m -> m));

        Set<String> counterpartKeys = new HashSet<>();
        for (List<ConversationMemberEntity> members : membersByConversation.values()) {
            for (ConversationMemberEntity member : members) {
                if (!Objects.equals(member.getUserId(), userId)) {
                    counterpartKeys.add(String.valueOf(member.getUserId()));
                }
            }
        }
        Map<String, String> displayNames = userDisplayService.resolveDisplayNames(counterpartKeys);

        List<ConversationVO> result = new ArrayList<>();
        for (Long conversationId : conversationIds) {
            ConversationEntity conversation = conversationById.get(conversationId);
            if (conversation == null) {
                continue;
            }
            List<ConversationMemberEntity> members = membersByConversation.getOrDefault(conversationId, List.of());
            ConversationVO vo = ChatConvert.INSTANCE.toConversationVO(conversation);
            vo.setMemberCount(members.size());
            if (Objects.equals(conversation.getConversationType(), ConversationType.SINGLE)) {
                vo.setName(members.stream()
                        .filter(member -> !Objects.equals(member.getUserId(), userId))
                        .findFirst()
                        .map(member -> displayNames.getOrDefault(String.valueOf(member.getUserId()), "未知用户"))
                        .orElse("未知用户"));
            }
            ChatMessageEntity lastMessage = lastMessageByConversation.get(conversationId);
            if (lastMessage != null) {
                vo.setLastMessageContent(lastMessage.getContent());
                vo.setLastMessageSenderId(lastMessage.getSenderId());
                vo.setLastMessageSendTime(lastMessage.getSendTime());
            }
            result.add(vo);
        }
        result.sort(Comparator.comparing(ConversationVO::getLastMessageSendTime,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public ConversationVO createGroupConversation(Long ownerId, CreateGroupConversationRequest request) {
        LocalDateTime now = LocalDateTime.now();
        ConversationEntity conversation = ConversationEntity.builder()
                .conversationType(ConversationType.GROUP)
                .name(request.getName())
                .nextSeq(1L)
                .status(ConversationStatus.NORMAL)
                .createBy(Objects.toString(ownerId, null))
                .createTime(now)
                .updateBy(Objects.toString(ownerId, null))
                .updateTime(now)
                .build();
        conversationMapper.insert(conversation);

        insertMember(conversation.getId(), ownerId, ConversationMemberRole.OWNER, now, ownerId);
        Set<Long> memberIds = new LinkedHashSet<>(request.getMemberUserIds());
        memberIds.remove(ownerId);
        for (Long memberId : memberIds) {
            insertMember(conversation.getId(), memberId, ConversationMemberRole.MEMBER, now, ownerId);
        }

        ConversationVO vo = ChatConvert.INSTANCE.toConversationVO(conversation);
        vo.setMemberCount(memberIds.size() + 1);
        return vo;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ConversationMemberVO> listMembers(Long userId, Long conversationId) {
        assertActiveMember(conversationId, userId);
        List<ConversationMemberEntity> members = conversationMemberMapper.selectList(
                new LambdaQueryWrapper<ConversationMemberEntity>()
                        .eq(ConversationMemberEntity::getConversationId, conversationId)
                        .eq(ConversationMemberEntity::getStatus, ConversationMemberStatus.NORMAL)
                        .orderByAsc(ConversationMemberEntity::getJoinedTime));

        Set<String> keys = members.stream().map(member -> String.valueOf(member.getUserId()))
                .collect(Collectors.toSet());
        Map<String, String> displayNames = userDisplayService.resolveDisplayNames(keys);

        List<ConversationMemberVO> result = ChatConvert.INSTANCE.toMemberVOList(members);
        for (int i = 0; i < members.size(); i++) {
            String key = String.valueOf(members.get(i).getUserId());
            result.get(i).setUserName(displayNames.getOrDefault(key, "未知用户"));
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void addMembers(Long userId, Long conversationId, List<Long> memberUserIds) {
        assertGroupConversation(conversationId);
        assertActiveMember(conversationId, userId);

        LocalDateTime now = LocalDateTime.now();
        for (Long memberId : new LinkedHashSet<>(memberUserIds)) {
            ConversationMemberEntity existing = conversationMemberMapper.selectOne(
                    new LambdaQueryWrapper<ConversationMemberEntity>()
                            .eq(ConversationMemberEntity::getConversationId, conversationId)
                            .eq(ConversationMemberEntity::getUserId, memberId));
            if (existing == null) {
                insertMember(conversationId, memberId, ConversationMemberRole.MEMBER, now, userId);
            } else if (!Objects.equals(existing.getStatus(), ConversationMemberStatus.NORMAL)) {
                existing.setStatus(ConversationMemberStatus.NORMAL);
                existing.setJoinedTime(now);
                existing.setUpdateBy(Objects.toString(userId, null));
                existing.setUpdateTime(now);
                conversationMemberMapper.updateById(existing);
            }
            // 已在群内（状态正常）的成员按幂等处理，忽略不重复添加。
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void removeMember(Long userId, Long conversationId, Long targetUserId) {
        assertGroupConversation(conversationId);
        ConversationMemberEntity operatorMembership = getActiveMemberOrThrow(conversationId, userId);
        boolean selfLeave = Objects.equals(userId, targetUserId);
        if (!selfLeave && !Objects.equals(operatorMembership.getRole(), ConversationMemberRole.OWNER)) {
            throw new BusinessException("只有群主可以移除其他成员");
        }

        ConversationMemberEntity target = getActiveMemberOrThrow(conversationId, targetUserId);
        if (!selfLeave && Objects.equals(target.getRole(), ConversationMemberRole.OWNER)) {
            throw new BusinessException("不能移除群主");
        }

        target.setStatus(ConversationMemberStatus.LEFT);
        target.setUpdateBy(Objects.toString(userId, null));
        target.setUpdateTime(LocalDateTime.now());
        conversationMemberMapper.updateById(target);
    }

    /**
     * {@inheritDoc}
     * <p>
     * 两个用户并发首次互发消息时可能各自判断"会话不存在"而分别创建出两条单聊会话，
     * 这是本阶段接受的边界情况（极小概率、非致命，仅表现为消息分散在两条会话记录中），
     * 暂不引入跨用户对的应用层加锁机制，保持实现简单（design.md 未展开讨论此边界，
     * 留待后续如有实际影响再优化）。
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public ConversationEntity getOrCreateSingleConversation(Long userId1, Long userId2) {
        Long existingId = conversationMemberMapper.selectSingleConversationId(userId1, userId2);
        if (existingId != null) {
            return conversationMapper.selectById(existingId);
        }

        LocalDateTime now = LocalDateTime.now();
        ConversationEntity conversation = ConversationEntity.builder()
                .conversationType(ConversationType.SINGLE)
                .name(null)
                .nextSeq(1L)
                .status(ConversationStatus.NORMAL)
                .createBy(Objects.toString(userId1, null))
                .createTime(now)
                .updateBy(Objects.toString(userId1, null))
                .updateTime(now)
                .build();
        conversationMapper.insert(conversation);
        insertMember(conversation.getId(), userId1, ConversationMemberRole.MEMBER, now, userId1);
        insertMember(conversation.getId(), userId2, ConversationMemberRole.MEMBER, now, userId1);
        return conversation;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public long nextSeq(Long conversationId) {
        Long current = conversationMapper.selectNextSeqForUpdate(conversationId);
        if (current == null) {
            throw new BusinessException("会话不存在：" + conversationId);
        }
        conversationMapper.incrementNextSeq(conversationId);
        return current;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void assertActiveMember(Long conversationId, Long userId) {
        getActiveMemberOrThrow(conversationId, userId);
    }

    /**
     * 断言指定会话存在且为群聊类型，否则抛出业务异常。
     *
     * @param conversationId 会话 id
     */
    private void assertGroupConversation(Long conversationId) {
        ConversationEntity conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw new BusinessException("会话不存在");
        }
        if (!Objects.equals(conversation.getConversationType(), ConversationType.GROUP)) {
            throw new BusinessException("仅群聊会话支持成员管理");
        }
    }

    /**
     * 查询指定用户在指定会话内的正常成员记录，不存在则抛出业务异常。
     *
     * @param conversationId 会话 id
     * @param userId         用户 id
     * @return 会话成员实体
     */
    private ConversationMemberEntity getActiveMemberOrThrow(Long conversationId, Long userId) {
        ConversationMemberEntity member = conversationMemberMapper.selectOne(
                new LambdaQueryWrapper<ConversationMemberEntity>()
                        .eq(ConversationMemberEntity::getConversationId, conversationId)
                        .eq(ConversationMemberEntity::getUserId, userId)
                        .eq(ConversationMemberEntity::getStatus, ConversationMemberStatus.NORMAL));
        if (member == null) {
            throw new BusinessException("您不是该会话成员，无权操作");
        }
        return member;
    }

    /**
     * 插入一条会话成员记录。
     *
     * @param conversationId 会话 id
     * @param userId         成员用户 id
     * @param role           成员角色
     * @param now            当前时间
     * @param operatorId     操作人用户 id，写入审计字段
     */
    private void insertMember(Long conversationId, Long userId, int role, LocalDateTime now, Long operatorId) {
        ConversationMemberEntity member = ConversationMemberEntity.builder()
                .conversationId(conversationId)
                .userId(userId)
                .role(role)
                .joinedTime(now)
                .status(ConversationMemberStatus.NORMAL)
                .createBy(Objects.toString(operatorId, null))
                .createTime(now)
                .updateBy(Objects.toString(operatorId, null))
                .updateTime(now)
                .build();
        conversationMemberMapper.insert(member);
    }
}
