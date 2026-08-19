package cn.nihility.rbac.app.authconfig.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * {@code AppAuthConfigMapper#selectActiveProtocolApps} 联表查询结果的载体 DTO（非对外 VO），
 * 承载 {@code tab_app_auth_config} INNER JOIN {@code tab_app_config} 后的一行数据：某个已
 * 启用单点登录协议（CAS/OAuth2.0）应用的登出通知回调地址与对外接口凭证签名参数，供
 * {@code cn.nihility.rbac.sso.support.SsoLogoutNotifyService} 登出时按 {@code appId} 匹配
 * 回调目标使用（add-sso-single-logout change tasks.md 3.1，对齐 {@link AppUserinfoFieldMappingRow}
 * "联表查询结果 DTO 归属查询方所在包"的既有模式）。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppProtocolInfo {

    /** 应用对外标识（{@code tab_app_config.open_app_id}）。 */
    private String appId;

    /** 单点登录协议类型：CAS 或 OAUTH2（本查询已过滤掉 NONE）。 */
    private String authProtocol;

    /** 登出通知回调地址，未配置时为 {@code null}。 */
    private String logoutNotifyUrl;

    /** 对外接口 AccessKey，签名请求头 {@code appKey} 取值。 */
    private String accessKey;

    /** 对外接口 SecretKey，经 SM4 对称加密后的 Base64 密文，需解密后才能用于计算签名。 */
    private String secretKey;

    /** 接口签名算法：{@code SHA256} 或 {@code SM3}。 */
    private String signAlgorithm;

    /** 是否需要签名，回调请求按此开关决定是否附加签名请求头。 */
    private Boolean needSign;
}
