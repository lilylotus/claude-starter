package cn.nihility.rbac.auth.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.auth.context.CurrentUserContext;
import cn.nihility.rbac.auth.entity.UserPasswordEntity;
import cn.nihility.rbac.auth.mapper.UserPasswordMapper;
import cn.nihility.rbac.auth.service.PasswordService;
import cn.nihility.rbac.auth.util.PasswordDigestUtils;
import cn.nihility.rbac.common.exception.BusinessException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link PasswordServiceImpl} 的单元测试，覆盖默认密码创建/重置、密码校验、修改密码、
 * 首登标识查询等分支逻辑（password-login-auth spec.md 密码摘要存储/首次登录相关 Scenario）。
 */
@ExtendWith(MockitoExtension.class)
class PasswordServiceImplTest {

    /** 被测服务的用户密码数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private UserPasswordMapper userPasswordMapper;

    /** 被测服务实例。 */
    private PasswordServiceImpl passwordService;

    /**
     * 每个用例执行前重新构造被测服务，并清空线程级当前登录用户标记。
     */
    @BeforeEach
    void setUp() {
        passwordService = new PasswordServiceImpl(userPasswordMapper);
        CurrentUserContext.clear();
    }

    /**
     * 每个用例结束后清空线程级当前登录用户标记，避免污染后续用例。
     */
    @AfterEach
    void tearDown() {
        CurrentUserContext.clear();
    }

    /**
     * 创建默认密码时，持久化的记录应只包含密码摘要与盐值（不包含明文密码），摘要与该用户的盐值
     * 计算结果一致，且首登标识为待改密状态（spec.md "保存密码摘要而非明文"/"创建用户联动生成
     * 默认密码" Scenario）。
     */
    @Test
    void createDefaultPassword_shouldPersistDigestAndSaltWithFirstLoginTrue() {
        ArgumentCaptor<UserPasswordEntity> captor = ArgumentCaptor.forClass(UserPasswordEntity.class);

        passwordService.createDefaultPassword(1L);

        verify(userPasswordMapper).insert(captor.capture());
        UserPasswordEntity entity = captor.getValue();
        assertThat(entity.getUserId()).isEqualTo(1L);
        assertThat(entity.getFirstLogin()).isTrue();
        assertThat(entity.getPasswordDigest())
                .isEqualTo(PasswordDigestUtils.digest(PasswordService.DEFAULT_PASSWORD, entity.getSalt()));
    }

    /**
     * 重置密码时，若该用户已存在密码记录，应更新既有记录为默认密码并重新置首登标识，
     * 不额外插入新记录。
     */
    @Test
    void resetToDefault_shouldUpdateExistingRecord_whenRecordExists() {
        UserPasswordEntity existing = existingEntity(1L, "旧摘要", "旧盐值", false);
        when(userPasswordMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        passwordService.resetToDefault(1L);

        ArgumentCaptor<UserPasswordEntity> captor = ArgumentCaptor.forClass(UserPasswordEntity.class);
        verify(userPasswordMapper).updateById(captor.capture());
        verify(userPasswordMapper, never()).insert(any(UserPasswordEntity.class));
        UserPasswordEntity updated = captor.getValue();
        assertThat(updated.getFirstLogin()).isTrue();
        assertThat(updated.getPasswordDigest())
                .isEqualTo(PasswordDigestUtils.digest(PasswordService.DEFAULT_PASSWORD, updated.getSalt()));
    }

    /**
     * 重置密码时，若该用户此前不存在密码记录（历史遗留数据），应兜底补建一条默认密码记录。
     */
    @Test
    void resetToDefault_shouldInsertNewRecord_whenRecordMissing() {
        when(userPasswordMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        passwordService.resetToDefault(1L);

        verify(userPasswordMapper).insert(any(UserPasswordEntity.class));
        verify(userPasswordMapper, never()).updateById(any(UserPasswordEntity.class));
    }

    /**
     * 密码校验：明文密码经该用户盐值计算出的摘要与存储摘要一致时应返回 {@code true}。
     */
    @Test
    void verifyPassword_shouldReturnTrue_whenDigestMatches() {
        String salt = PasswordDigestUtils.randomSalt();
        String digest = PasswordDigestUtils.digest("Default#123456", salt);
        when(userPasswordMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(existingEntity(1L, digest, salt, true));

        assertThat(passwordService.verifyPassword(1L, "Default#123456")).isTrue();
    }

    /**
     * 密码校验：明文密码不匹配时应返回 {@code false}。
     */
    @Test
    void verifyPassword_shouldReturnFalse_whenDigestMismatches() {
        String salt = PasswordDigestUtils.randomSalt();
        String digest = PasswordDigestUtils.digest("Default#123456", salt);
        when(userPasswordMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(existingEntity(1L, digest, salt, true));

        assertThat(passwordService.verifyPassword(1L, "wrong-password")).isFalse();
    }

    /**
     * 密码校验：用户不存在密码记录时应返回 {@code false}，而不是抛出异常。
     */
    @Test
    void verifyPassword_shouldReturnFalse_whenRecordMissing() {
        when(userPasswordMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThat(passwordService.verifyPassword(1L, "any-password")).isFalse();
    }

    /**
     * 修改密码成功后，应重新生成盐值与摘要，并清除首登标识（spec.md "旧密码正确时修改成功" Scenario）。
     */
    @Test
    void updatePassword_shouldRegenerateDigestAndClearFirstLogin() {
        UserPasswordEntity existing = existingEntity(1L, "旧摘要", "旧盐值", true);
        when(userPasswordMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        passwordService.updatePassword(1L, "NewPass#123");

        ArgumentCaptor<UserPasswordEntity> captor = ArgumentCaptor.forClass(UserPasswordEntity.class);
        verify(userPasswordMapper).updateById(captor.capture());
        UserPasswordEntity updated = captor.getValue();
        assertThat(updated.getFirstLogin()).isFalse();
        assertThat(updated.getSalt()).isNotEqualTo("旧盐值");
        assertThat(updated.getPasswordDigest())
                .isEqualTo(PasswordDigestUtils.digest("NewPass#123", updated.getSalt()));
    }

    /**
     * 修改密码时，若该用户不存在密码记录，应抛出业务异常。
     */
    @Test
    void updatePassword_shouldThrowBusinessException_whenRecordMissing() {
        when(userPasswordMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> passwordService.updatePassword(1L, "NewPass#123"))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * 首登标识查询：记录存在且 {@code firstLogin=true} 时返回 {@code true}。
     */
    @Test
    void isFirstLogin_shouldReturnTrue_whenFlagIsTrue() {
        when(userPasswordMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(existingEntity(1L, "digest", "salt", true));

        assertThat(passwordService.isFirstLogin(1L)).isTrue();
    }

    /**
     * 首登标识查询：记录存在且 {@code firstLogin=false} 时返回 {@code false}。
     */
    @Test
    void isFirstLogin_shouldReturnFalse_whenFlagIsFalse() {
        when(userPasswordMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(existingEntity(1L, "digest", "salt", false));

        assertThat(passwordService.isFirstLogin(1L)).isFalse();
    }

    /**
     * 首登标识查询：用户不存在密码记录时返回 {@code false}。
     */
    @Test
    void isFirstLogin_shouldReturnFalse_whenRecordMissing() {
        when(userPasswordMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThat(passwordService.isFirstLogin(1L)).isFalse();
    }

    /**
     * 构造一条测试用的既有密码记录。
     *
     * @param userId     用户 id
     * @param digest     密码摘要
     * @param salt       盐值
     * @param firstLogin 首登标识
     * @return 密码记录实体
     */
    private UserPasswordEntity existingEntity(long userId, String digest, String salt, boolean firstLogin) {
        LocalDateTime now = LocalDateTime.now();
        return UserPasswordEntity.builder()
                .id(1L)
                .userId(userId)
                .passwordDigest(digest)
                .salt(salt)
                .firstLogin(firstLogin)
                .createBy("system")
                .createTime(now)
                .updateBy("system")
                .updateTime(now)
                .build();
    }
}
