package cn.nihility.rbac.chat.gateway.protocol.body;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * {@code LOGIN_ACK} 帧消息体（服务端 -> 客户端）：认证结果回执。
 */
@Getter
@Setter
@NoArgsConstructor
public class LoginAckFrameBody {

    /** 是否认证成功。 */
    private boolean success;

    /** 认证成功后绑定的用户 id，失败时为 {@code null}。 */
    private Long userId;

    /** 提示信息，失败时说明原因。 */
    private String message;

    private LoginAckFrameBody(boolean success, Long userId, String message) {
        this.success = success;
        this.userId = userId;
        this.message = message;
    }

    /**
     * 构造一个认证成功的回执。
     *
     * @param userId 绑定的用户 id
     * @return 认证结果回执
     */
    public static LoginAckFrameBody success(Long userId) {
        return new LoginAckFrameBody(true, userId, "认证成功");
    }

    /**
     * 构造一个认证失败的回执。
     *
     * @param message 失败原因
     * @return 认证结果回执
     */
    public static LoginAckFrameBody failure(String message) {
        return new LoginAckFrameBody(false, null, message);
    }
}
