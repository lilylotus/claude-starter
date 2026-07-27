package cn.nihility.rbac.auth.service.impl;

import cn.nihility.rbac.auth.context.CurrentUserContext;
import cn.nihility.rbac.auth.entity.UserPasswordEntity;
import cn.nihility.rbac.auth.mapper.UserPasswordMapper;
import cn.nihility.rbac.auth.service.PasswordService;
import cn.nihility.rbac.auth.util.PasswordDigestUtils;
import cn.nihility.rbac.common.exception.BusinessException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 密码业务逻辑实现，只操作 {@code tab_user_password} 表，不依赖 {@code user} 模块的任何类型，
 * 避免模块间反向依赖。
 */
@Service
@RequiredArgsConstructor
public class PasswordServiceImpl implements PasswordService {

    /**
     * 系统级操作（如新增用户联动创建默认密码等发生在已认证会话中的操作，
     * {@link CurrentUserContext} 理应已有值）取不到当前登录用户时的兜底操作人标识，
     * 正常业务链路下不会用到，仅用于脱离请求上下文调用本服务的场景（如单测）。
     */
    private static final String SYSTEM_OPERATOR = "system";

    /** 用户密码数据访问接口。 */
    private final UserPasswordMapper userPasswordMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    public void createDefaultPassword(Long userId) {
        String salt = PasswordDigestUtils.randomSalt();
        String digest = PasswordDigestUtils.digest(DEFAULT_PASSWORD, salt);
        LocalDateTime now = LocalDateTime.now();
        String operator = currentOperator();

        UserPasswordEntity entity = UserPasswordEntity.builder()
                .userId(userId)
                .passwordDigest(digest)
                .salt(salt)
                .firstLogin(Boolean.TRUE)
                .createBy(operator)
                .createTime(now)
                .updateBy(operator)
                .updateTime(now)
                .build();
        userPasswordMapper.insert(entity);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void resetToDefault(Long userId) {
        String salt = PasswordDigestUtils.randomSalt();
        String digest = PasswordDigestUtils.digest(DEFAULT_PASSWORD, salt);
        LocalDateTime now = LocalDateTime.now();
        String operator = currentOperator();

        UserPasswordEntity entity = getByUserId(userId);
        if (entity == null) {
            entity = UserPasswordEntity.builder()
                    .userId(userId)
                    .passwordDigest(digest)
                    .salt(salt)
                    .firstLogin(Boolean.TRUE)
                    .createBy(operator)
                    .createTime(now)
                    .updateBy(operator)
                    .updateTime(now)
                    .build();
            userPasswordMapper.insert(entity);
            return;
        }

        entity.setPasswordDigest(digest);
        entity.setSalt(salt);
        entity.setFirstLogin(Boolean.TRUE);
        entity.setUpdateBy(operator);
        entity.setUpdateTime(now);
        userPasswordMapper.updateById(entity);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean verifyPassword(Long userId, String plainPassword) {
        UserPasswordEntity entity = getByUserId(userId);
        if (entity == null) {
            return false;
        }
        String digest = PasswordDigestUtils.digest(plainPassword, entity.getSalt());
        return digest.equalsIgnoreCase(entity.getPasswordDigest());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updatePassword(Long userId, String newPlainPassword) {
        UserPasswordEntity entity = getByUserId(userId);
        if (entity == null) {
            throw new BusinessException("用户密码记录不存在");
        }
        String salt = PasswordDigestUtils.randomSalt();
        String digest = PasswordDigestUtils.digest(newPlainPassword, salt);
        entity.setPasswordDigest(digest);
        entity.setSalt(salt);
        entity.setFirstLogin(Boolean.FALSE);
        entity.setUpdateBy(currentOperator());
        entity.setUpdateTime(LocalDateTime.now());
        userPasswordMapper.updateById(entity);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isFirstLogin(Long userId) {
        UserPasswordEntity entity = getByUserId(userId);
        return entity != null && Boolean.TRUE.equals(entity.getFirstLogin());
    }

    /**
     * 按用户 id 查询其当前密码记录。
     *
     * @param userId 用户 id
     * @return 密码记录，不存在时返回 {@code null}
     */
    private UserPasswordEntity getByUserId(Long userId) {
        return userPasswordMapper.selectOne(new LambdaQueryWrapper<UserPasswordEntity>()
                .eq(UserPasswordEntity::getUserId, userId));
    }

    /**
     * 解析当前操作人标识：优先取 {@link CurrentUserContext} 中的当前登录用户 id
     * （改密/重置密码/新增用户联动创建默认密码等均发生在已登录会话中），取不到时回退为
     * {@link #SYSTEM_OPERATOR}。
     *
     * @return 操作人标识字符串
     */
    private String currentOperator() {
        Long userId = CurrentUserContext.getUserId();
        return userId != null ? String.valueOf(userId) : SYSTEM_OPERATOR;
    }
}
