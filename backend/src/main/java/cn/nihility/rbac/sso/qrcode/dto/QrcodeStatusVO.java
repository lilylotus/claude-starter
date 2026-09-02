package cn.nihility.rbac.sso.qrcode.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

/**
 * 二维码登录会话状态查询响应。
 */
@Getter
@Builder
@Schema(description = "二维码登录会话状态")
public class QrcodeStatusVO {

    /** 当前状态：PENDING=待扫码，SCANNED=已扫码待确认，CONFIRMED=已确认，EXPIRED=已过期/不存在/已被消费。 */
    @Schema(description = "当前状态：PENDING/SCANNED/CONFIRMED/EXPIRED")
    private String status;

    /**
     * 是否需要完成首次登录密码修改，仅本次响应把状态从 {@code CONFIRMED} 首次签发会话时非空，
     * 其余情况为 {@code null}。
     */
    @Schema(description = "是否需要完成首次登录密码修改，仅本次响应完成会话签发时非空")
    private Boolean firstLogin;
}
