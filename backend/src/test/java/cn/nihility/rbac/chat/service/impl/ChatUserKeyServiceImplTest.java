package cn.nihility.rbac.chat.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.chat.constant.ChatErrorCode;
import cn.nihility.rbac.chat.dto.ChatKeyRegisterRequest;
import cn.nihility.rbac.chat.dto.ChatUserKeyVO;
import cn.nihility.rbac.chat.entity.ChatUserKeyEntity;
import cn.nihility.rbac.chat.mapper.ChatUserKeyMapper;
import cn.nihility.rbac.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link ChatUserKeyServiceImpl} 的单元测试，重点覆盖"注册只能写当前登录用户自己的记录"
 * 与"查询不存在的密钥目录记录时返回预期业务错误"两条分支。
 */
@ExtendWith(MockitoExtension.class)
class ChatUserKeyServiceImplTest {

    /** 被测服务的密钥目录数据访问依赖。 */
    @Mock
    private ChatUserKeyMapper chatUserKeyMapper;

    /** 被测服务实例。 */
    private ChatUserKeyServiceImpl chatUserKeyService;

    @BeforeEach
    void setUp() {
        chatUserKeyService = new ChatUserKeyServiceImpl(chatUserKeyMapper);
    }

    /** 首次注册（不存在记录）应新建一条记录，且写入的 userId 是接口入参的当前登录用户 id，
     *  不接受为他人注册（接口本身不暴露 userId 字段，从登录态解析后传入）。 */
    @Test
    void registerMyKey_shouldInsertRecordBoundToCurrentUserWhenNotExists() {
        when(chatUserKeyMapper.selectOne(any())).thenReturn(null);
        ChatKeyRegisterRequest request = new ChatKeyRegisterRequest();
        request.setIdentityPublicKey("base64-public-key-abc");

        ChatUserKeyVO vo = chatUserKeyService.registerMyKey(1L, request);

        ArgumentCaptor<ChatUserKeyEntity> captor = ArgumentCaptor.forClass(ChatUserKeyEntity.class);
        verify(chatUserKeyMapper, times(1)).insert(captor.capture());
        verify(chatUserKeyMapper, never()).updateById(any(ChatUserKeyEntity.class));
        ChatUserKeyEntity persisted = captor.getValue();
        assertThat(persisted.getUserId()).isEqualTo(1L);
        assertThat(persisted.getIdentityPublicKey()).isEqualTo("base64-public-key-abc");
        assertThat(persisted.getKeyFingerprint()).isNotBlank();
        assertThat(vo.getUserId()).isEqualTo(1L);
        assertThat(vo.getIdentityPublicKey()).isEqualTo("base64-public-key-abc");
    }

    /** 已存在记录时应按当前登录用户 id 更新该记录，而不是新建一条。 */
    @Test
    void registerMyKey_shouldUpdateOwnExistingRecord() {
        ChatUserKeyEntity existing = ChatUserKeyEntity.builder().id(100L).userId(1L)
                .identityPublicKey("old-key").keyFingerprint("old-fp").build();
        when(chatUserKeyMapper.selectOne(any())).thenReturn(existing);
        ChatKeyRegisterRequest request = new ChatKeyRegisterRequest();
        request.setIdentityPublicKey("new-public-key");

        ChatUserKeyVO vo = chatUserKeyService.registerMyKey(1L, request);

        verify(chatUserKeyMapper, never()).insert(any(ChatUserKeyEntity.class));
        verify(chatUserKeyMapper, times(1)).updateById(existing);
        assertThat(existing.getUserId()).isEqualTo(1L);
        assertThat(existing.getIdentityPublicKey()).isEqualTo("new-public-key");
        assertThat(vo.getIdentityPublicKey()).isEqualTo("new-public-key");
    }

    /** 查询尚未注册聊天密钥的用户时应抛出预期的业务错误。 */
    @Test
    void getUserKey_shouldThrowBusinessExceptionWhenNotRegistered() {
        when(chatUserKeyMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> chatUserKeyService.getUserKey(2L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ChatErrorCode.CHAT_KEY_NOT_FOUND);
    }

    /** 查询已注册用户的密钥应正常返回视图对象。 */
    @Test
    void getUserKey_shouldReturnVOWhenRegistered() {
        ChatUserKeyEntity entity = ChatUserKeyEntity.builder().id(200L).userId(2L)
                .identityPublicKey("public-key-of-2").keyFingerprint("fp-2").build();
        when(chatUserKeyMapper.selectOne(any())).thenReturn(entity);

        ChatUserKeyVO vo = chatUserKeyService.getUserKey(2L);

        assertThat(vo.getUserId()).isEqualTo(2L);
        assertThat(vo.getIdentityPublicKey()).isEqualTo("public-key-of-2");
        assertThat(vo.getKeyFingerprint()).isEqualTo("fp-2");
    }
}
