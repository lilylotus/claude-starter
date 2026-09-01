package cn.nihility.rbac.chat.gateway.protocol.body;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * {@code LOGIN} 帧消息体（客户端 -> 服务端）：携带既有登录会话的 accessKey，网关调用
 * {@code TokenService.verifyAccessKey} 校验并解析出 {@code userId}（design.md Decision 4）。
 */
@Getter
@Setter
@NoArgsConstructor
public class LoginFrameBody {

    /** 登录会话的 accessKey（即 HTTP 接口的 {@code identity-token} 请求头值）。 */
    private String accessKey;
}
