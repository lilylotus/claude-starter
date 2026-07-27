package cn.nihility.rbac.auth.service;

/**
 * 密码业务逻辑接口：负责默认密码创建/重置、密码校验、修改密码及首登标识查询，
 * 供 {@code auth} 模块自身（登录/改密）与 {@code user} 模块（新增用户联动创建默认密码、
 * 管理员重置密码）复用；{@code user} 模块只依赖本接口做依赖注入调用，本接口的实现不
 * 反向依赖 {@code user} 模块。
 */
public interface PasswordService {

    /** 默认密码明文：新用户初始密码、管理员重置密码后的密码，均为该值。 */
    String DEFAULT_PASSWORD = "Default#123456";

    /**
     * 为新用户创建默认密码记录（摘要对应 {@link #DEFAULT_PASSWORD}），并标记为待首次登录强制改密。
     *
     * @param userId 用户 id
     */
    void createDefaultPassword(Long userId);

    /**
     * 将指定用户的密码重置为默认密码，并重新标记为待首次登录强制改密（管理员重置密码场景）；
     * 若该用户此前不存在密码记录（历史遗留数据），兜底补建一条。
     *
     * @param userId 用户 id
     */
    void resetToDefault(Long userId);

    /**
     * 校验明文密码是否与指定用户当前密码摘要匹配。
     *
     * @param userId        用户 id
     * @param plainPassword 明文密码
     * @return 是否匹配；用户不存在密码记录时返回 {@code false}
     */
    boolean verifyPassword(Long userId, String plainPassword);

    /**
     * 更新指定用户的密码（重新生成盐值与摘要），并清除待首次登录强制改密标识。
     *
     * @param userId           用户 id
     * @param newPlainPassword 新密码明文
     */
    void updatePassword(Long userId, String newPlainPassword);

    /**
     * 查询指定用户是否处于待首次登录强制改密状态。
     *
     * @param userId 用户 id
     * @return 是否待首次登录强制改密；用户不存在密码记录时返回 {@code false}
     */
    boolean isFirstLogin(Long userId);
}
