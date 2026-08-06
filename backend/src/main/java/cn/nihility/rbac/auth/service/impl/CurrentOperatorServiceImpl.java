package cn.nihility.rbac.auth.service.impl;

import cn.nihility.rbac.auth.context.CurrentUserContext;
import cn.nihility.rbac.auth.service.CurrentOperatorService;
import cn.nihility.rbac.user.entity.UserEntity;
import cn.nihility.rbac.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * "当前登录操作人账号编码"解析能力实现：取 {@link CurrentUserContext#getUserId()} 标记的用户 id，
 * 查出对应的 {@link UserEntity} 后返回其 {@code code}。不做静默兜底——脱离已登录上下文调用属于
 * 调用方编程错误，应尽早以异常暴露，而不是悄悄退化为某个固定占位符（本次修复的正是这种退化
 * 造成的问题）。
 */
@Service
@RequiredArgsConstructor
public class CurrentOperatorServiceImpl implements CurrentOperatorService {

    /** 用户数据访问接口，用于把当前登录用户 id 换算为账号编码。 */
    private final UserMapper userMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    public String resolveCode() {
        Long userId = CurrentUserContext.getUserId();
        if (userId == null) {
            throw new IllegalStateException("当前线程不处于已登录上下文中，无法解析操作人账号编码");
        }
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new IllegalStateException("当前登录用户 id=" + userId + " 查不到对应的用户记录");
        }
        return user.getCode();
    }
}
