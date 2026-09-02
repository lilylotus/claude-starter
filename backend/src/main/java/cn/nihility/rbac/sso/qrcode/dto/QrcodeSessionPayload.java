package cn.nihility.rbac.sso.qrcode.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 二维码登录会话在 Redis 中的存储结构（{@code sso:qrcode:<token>}，JSON 序列化存储，
 * add-sso-login-methods change design.md Decision 5）。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QrcodeSessionPayload {

    /** 当前状态，取值见 {@code QrcodeSessionStatus}（不含 {@code EXPIRED}，该状态由缺失该 key 表达）。 */
    private String status;

    /** 会话创建时反解出的目标应用对外标识，解析不出时为 {@code null}。 */
    private String appId;

    /** 创建会话时的 SSO 登录页 {@code redirect} 参数原始值，供 {@code confirm} 端点二次校验允许的登录方式。 */
    private String redirect;

    /** 手机浏览器确认登录后绑定的用户 id，{@code CONFIRMED} 之前为 {@code null}。 */
    private Long userId;

    /**
     * 是否已被消费：PC 端轮询命中 {@code CONFIRMED} 并成功签发会话后置为 {@code true}，
     * 防止同一 token 被重复轮询时重复签发会话。
     */
    private boolean consumed;
}
