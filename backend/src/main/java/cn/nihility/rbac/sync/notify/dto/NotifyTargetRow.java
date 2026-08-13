package cn.nihility.rbac.sync.notify.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * {@code NotifyTargetMapper#selectNotifyTargets} 联表查询结果的载体 DTO（非对外 VO），
 * 承载 {@code tab_app_config} INNER JOIN {@code tab_app} INNER JOIN
 * {@code tab_app_sync_domain_config} 后的一行数据：某个 {@code syncMode=NOTIFY} 且
 * 目标数据域 {@code syncEnabled=true} 的启用中应用的通知所需配置
 * （app-sync-notify-pull-api change design.md Decision 3/8）。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotifyTargetRow {

    /** 应用 id（{@code tab_app.id}）。 */
    private Long appRefId;

    /** 对外应用标识（AppId）。 */
    private String appId;

    /** 对外接口 AccessKey。 */
    private String accessKey;

    /** 对外接口 SecretKey，经 SM4 对称加密后的密文。 */
    private String secretKey;

    /** 接口签名算法：SHA256 或 SM3。 */
    private String signAlgorithm;

    /** 是否需要签名/验签校验。 */
    private Boolean needSign;

    /** 通知回调接口地址。 */
    private String notifyUrl;

    /** 通知请求自定义参数，原始 JSON 文本。 */
    private String notifyParams;
}
