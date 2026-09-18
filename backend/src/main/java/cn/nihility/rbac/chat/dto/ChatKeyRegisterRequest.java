package cn.nihility.rbac.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 注册/更新本人聊天身份公钥请求，只能写入当前登录用户自己的记录（用户 id 从登录态解析，
 * 不接受请求体传入）。
 */
@Getter
@Setter
@Schema(description = "注册/更新本人聊天身份公钥请求")
public class ChatKeyRegisterRequest {

    /** X25519 身份公钥（Base64 编码，定长，最长 64 个字符）。 */
    @Schema(description = "X25519 身份公钥（Base64 编码）", example = "MCowBQYDK2VuAyEAy6f0k9v4W3nQe1c...",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "身份公钥不能为空")
    @Size(max = 64, message = "身份公钥长度不能超过 64 个字符")
    private String identityPublicKey;
}
