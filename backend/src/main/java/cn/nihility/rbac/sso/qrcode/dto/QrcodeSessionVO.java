package cn.nihility.rbac.sso.qrcode.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

/**
 * 创建二维码登录会话的响应。
 */
@Getter
@Builder
@Schema(description = "二维码登录会话")
public class QrcodeSessionVO {

    /** 会话令牌，PC 端据此轮询状态、拼接二维码内容地址。 */
    @Schema(description = "会话令牌")
    private String token;

    /**
     * 扫码确认页的相对路径（不含域名/端口），前端自行用当前页面 origin 拼接成完整地址后
     * 渲染成二维码内容，后端不猜测前端部署的 origin。
     */
    @Schema(description = "扫码确认页相对路径，前端自行拼接 origin 后渲染为二维码内容")
    private String confirmPath;
}
