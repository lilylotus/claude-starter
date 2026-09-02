package cn.nihility.rbac.sso.support;

import cn.nihility.rbac.user.constant.UserStatus;
import cn.nihility.rbac.user.entity.UserEntity;
import cn.nihility.rbac.user.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 按手机号解析"当前能唯一定位到的可登录账号"，供短信验证码发送/登录两处共用同一份判断
 * 逻辑（add-sso-login-methods change design.md Decision 4）：{@code tab_user.mobile}
 * 不做唯一性约束，只有精确匹配且状态为启用（天然排除已删除）的记录数恰为 1 条时，才视为
 * 命中；0 条（未注册/已停用）或多条（一号多绑定）均不视为命中，由调用方按各自场景决定如何
 * 处理（发送验证码场景静默跳过发送，登录场景按通用失败处理），本组件本身不做任何"存在与否"
 * 的信息泄露判断。
 */
@Component
@RequiredArgsConstructor
public class SsoMobileUserResolver {

    /** 用户数据访问接口。 */
    private final UserMapper userMapper;

    /**
     * 按手机号精确匹配、状态为启用查询用户，命中数恰为 1 条时返回该用户，否则返回空。
     *
     * @param mobile 手机号
     * @return 唯一命中的启用状态用户，未命中或命中多条时返回空
     */
    public Optional<UserEntity> resolveUniqueEnabledUser(String mobile) {
        if (!StringUtils.hasText(mobile)) {
            return Optional.empty();
        }
        List<UserEntity> matched = userMapper.selectList(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getMobile, mobile)
                .eq(UserEntity::getStatus, UserStatus.ENABLED));
        return matched.size() == 1 ? Optional.of(matched.get(0)) : Optional.empty();
    }
}
