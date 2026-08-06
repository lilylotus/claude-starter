package cn.nihility.rbac.auth.service.impl;

import cn.nihility.rbac.auth.context.CurrentUserContext;
import cn.nihility.rbac.auth.service.CurrentOperatorService;
import org.springframework.stereotype.Service;

/**
 * "当前登录操作人用户 id"解析能力实现：直接返回 {@link CurrentUserContext#getUserId()}
 * 标记的用户 id。不做静默兜底——脱离已登录上下文调用属于调用方编程错误，应尽早以异常暴露，
 * 而不是悄悄退化为某个固定占位符。
 */
@Service
public class CurrentOperatorServiceImpl implements CurrentOperatorService {

    /**
     * {@inheritDoc}
     */
    @Override
    public Long resolveUserId() {
        Long userId = CurrentUserContext.getUserId();
        if (userId == null) {
            throw new IllegalStateException("当前线程不处于已登录上下文中，无法解析操作人用户 id");
        }
        return userId;
    }
}
