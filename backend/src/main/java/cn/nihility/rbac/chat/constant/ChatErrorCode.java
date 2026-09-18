package cn.nihility.rbac.chat.constant;

/**
 * 聊天模块业务错误码。1001-1006 为聊天网关协议层错误码，用于 {@code ERROR} 类型帧的
 * {@code code} 字段，与 {@link cn.nihility.rbac.common.exception.BusinessException#DEFAULT_CODE}
 * （REST 接口默认业务错误码）体系相互独立，仅在 Netty 网关协议范围内使用；1007 起为聊天
 * 模块 REST 接口专用的 {@link cn.nihility.rbac.common.exception.BusinessException} 状态码，
 * 与网关错误码共用同一编号段仅为聊天模块内部管理方便，两者互不混用、互不影响。
 */
public final class ChatErrorCode {

    /** 连接尚未完成认证，拒绝处理业务帧。 */
    public static final int UNAUTHENTICATED = 1001;

    /** 认证帧携带的 accessKey 无效或已过期。 */
    public static final int AUTH_FAILED = 1002;

    /** 消息发送频率超过令牌桶限流阈值。 */
    public static final int RATE_LIMITED = 1003;

    /** 无权限（如非群成员发送群聊消息）。 */
    public static final int FORBIDDEN = 1004;

    /** 协议帧格式非法（帧头损坏、消息体无法解析等）。 */
    public static final int INVALID_FRAME = 1005;

    /** 未预期的服务端内部错误。 */
    public static final int INTERNAL_ERROR = 1006;

    /** 目标用户尚未注册聊天身份公钥，无法建立单聊加密会话（REST 密钥目录接口专用）。 */
    public static final int CHAT_KEY_NOT_FOUND = 1007;

    /**
     * 工具类不允许实例化。
     */
    private ChatErrorCode() {
    }
}
