package cn.nihility.rbac.chat.service.impl;

import cn.nihility.rbac.chat.constant.ChatErrorCode;
import cn.nihility.rbac.chat.dto.ChatKeyRegisterRequest;
import cn.nihility.rbac.chat.dto.ChatUserKeyVO;
import cn.nihility.rbac.chat.entity.ChatUserKeyEntity;
import cn.nihility.rbac.chat.mapper.ChatUserKeyMapper;
import cn.nihility.rbac.chat.mapstruct.ChatUserKeyConvert;
import cn.nihility.rbac.chat.service.ChatUserKeyService;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.workflow.dslv2.util.DigestUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 聊天密钥目录业务逻辑实现。
 */
@Service
@RequiredArgsConstructor
public class ChatUserKeyServiceImpl implements ChatUserKeyService {

    /** 密钥目录数据访问接口。 */
    private final ChatUserKeyMapper chatUserKeyMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public ChatUserKeyVO registerMyKey(Long userId, ChatKeyRegisterRequest request) {
        String operator = Objects.toString(userId, null);
        LocalDateTime now = LocalDateTime.now();
        String fingerprint = DigestUtils.sha256(request.getIdentityPublicKey());

        ChatUserKeyEntity existing = findByUserId(userId);
        if (existing == null) {
            ChatUserKeyEntity entity = ChatUserKeyEntity.builder()
                    .userId(userId)
                    .identityPublicKey(request.getIdentityPublicKey())
                    .keyFingerprint(fingerprint)
                    .createBy(operator)
                    .createTime(now)
                    .updateBy(operator)
                    .updateTime(now)
                    .build();
            try {
                chatUserKeyMapper.insert(entity);
            } catch (DuplicateKeyException e) {
                // 并发首次注册竞态：唯一索引冲突，回退为更新已存在的记录，保持接口整体幂等。
                return updateExisting(findByUserId(userId), request.getIdentityPublicKey(), fingerprint, operator,
                        now);
            }
            return ChatUserKeyConvert.INSTANCE.toVO(entity);
        }
        return updateExisting(existing, request.getIdentityPublicKey(), fingerprint, operator, now);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ChatUserKeyVO getUserKey(Long userId) {
        ChatUserKeyEntity entity = findByUserId(userId);
        if (entity == null) {
            throw new BusinessException(ChatErrorCode.CHAT_KEY_NOT_FOUND, "该用户尚未注册聊天身份公钥，无法建立加密会话");
        }
        return ChatUserKeyConvert.INSTANCE.toVO(entity);
    }

    /**
     * 按用户 id 查询密钥目录记录。
     *
     * @param userId 用户 id
     * @return 密钥目录记录，不存在时为 {@code null}
     */
    private ChatUserKeyEntity findByUserId(Long userId) {
        return chatUserKeyMapper.selectOne(
                new LambdaQueryWrapper<ChatUserKeyEntity>().eq(ChatUserKeyEntity::getUserId, userId));
    }

    /**
     * 更新已存在的密钥目录记录。
     *
     * @param existing    已存在的记录
     * @param publicKey   新的身份公钥
     * @param fingerprint 新公钥对应的指纹
     * @param operator    操作人（用户 id 文本）
     * @param now         当前时间
     * @return 更新后的视图对象
     */
    private ChatUserKeyVO updateExisting(ChatUserKeyEntity existing, String publicKey, String fingerprint,
            String operator, LocalDateTime now) {
        existing.setIdentityPublicKey(publicKey);
        existing.setKeyFingerprint(fingerprint);
        existing.setUpdateBy(operator);
        existing.setUpdateTime(now);
        chatUserKeyMapper.updateById(existing);
        return ChatUserKeyConvert.INSTANCE.toVO(existing);
    }
}
