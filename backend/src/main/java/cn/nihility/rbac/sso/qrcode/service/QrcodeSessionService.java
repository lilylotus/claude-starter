package cn.nihility.rbac.sso.qrcode.service;

import cn.nihility.rbac.sso.qrcode.dto.QrcodeSessionPayload;
import java.util.Optional;

/**
 * 二维码登录会话状态机业务逻辑（add-sso-login-methods change design.md Decision 5）。
 */
public interface QrcodeSessionService {

    /**
     * 创建一个新的二维码登录会话，初始状态为 {@code PENDING}。
     *
     * @param redirect SSO 登录页 {@code redirect} 参数原始值
     * @param appId    反解出的目标应用对外标识，可为 {@code null}
     * @return 会话令牌
     */
    String create(String redirect, String appId);

    /**
     * 查询一个二维码登录会话当前的存储内容。
     *
     * @param token 会话令牌
     * @return 会话存储内容，令牌不存在/已过期时返回空
     */
    Optional<QrcodeSessionPayload> find(String token);

    /**
     * 手机浏览器扫码后把状态从 {@code PENDING} 标记为 {@code SCANNED}，幂等——令牌不存在、
     * 已过期或状态已不是 {@code PENDING}（含 {@code SCANNED}/{@code CONFIRMED}/已消费）时
     * 静默忽略，不抛出异常。
     *
     * @param token 会话令牌
     */
    void markScanned(String token);

    /**
     * 手机浏览器确认登录：把状态置为 {@code CONFIRMED} 并绑定 {@code userId}。要求令牌存在
     * 且当前状态为 {@code PENDING}/{@code SCANNED}（未被消费），否则拒绝。
     *
     * @param token  会话令牌
     * @param userId 手机浏览器 SSO 会话对应的用户 id
     */
    void confirm(String token, Long userId);

    /**
     * 把一个已确认的会话标记为"已消费"（已为 PC 端签发过会话），并把剩余有效期收紧为一个
     * 很短的窗口，之后自然过期，防止同一 token 被重复轮询时重复签发会话。
     *
     * @param token   会话令牌
     * @param payload 调用方已持有的最新会话存储内容（避免重复读取一次）
     */
    void markConsumed(String token, QrcodeSessionPayload payload);
}
