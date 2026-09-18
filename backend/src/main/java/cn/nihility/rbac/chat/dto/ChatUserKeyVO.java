package cn.nihility.rbac.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 聊天密钥目录视图对象：某个用户当前注册的身份公钥信息，供发起单聊/首次建立加密会话前
 * 查询使用。
 */
@Getter
@Setter
@Schema(description = "聊天用户身份公钥")
public class ChatUserKeyVO {

    /** 用户 id。 */
    @Schema(description = "用户 id")
    private Long userId;

    /** X25519 身份公钥（Base64 编码）。 */
    @Schema(description = "X25519 身份公钥（Base64 编码）")
    private String identityPublicKey;

    /** 身份公钥指纹，服务端留痕/审计用，非安全校验唯一依据。 */
    @Schema(description = "身份公钥指纹（服务端留痕，客户端安全码比对以自行计算的指纹为准）")
    private String keyFingerprint;

    /** 最近一次更新时间。 */
    @Schema(description = "最近一次更新时间")
    private LocalDateTime updateTime;
}
