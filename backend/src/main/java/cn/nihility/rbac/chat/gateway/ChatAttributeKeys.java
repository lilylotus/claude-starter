package cn.nihility.rbac.chat.gateway;

import io.netty.util.AttributeKey;
import io.netty.util.concurrent.ScheduledFuture;

/**
 * 聊天网关 Channel 级属性键集中定义，避免各 Handler 各自定义同名字符串导致的排查困难；
 * {@link AttributeKey#valueOf(String)} 按名称全局缓存单例，集中定义仅为便于统一管理与查找。
 */
public final class ChatAttributeKeys {

    /** 是否已完成认证。 */
    public static final AttributeKey<Boolean> AUTHENTICATED = AttributeKey.valueOf("chat.authenticated");

    /** 认证成功后绑定的用户 id。 */
    public static final AttributeKey<Long> USER_ID = AttributeKey.valueOf("chat.userId");

    /** 认证超时定时任务句柄，认证成功/连接关闭时需要取消，避免任务空跑。 */
    public static final AttributeKey<ScheduledFuture<?>> AUTH_TIMEOUT_TASK =
            AttributeKey.valueOf("chat.authTimeoutTask");

    /**
     * 工具类不允许实例化。
     */
    private ChatAttributeKeys() {
    }
}
