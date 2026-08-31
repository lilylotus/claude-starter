package cn.nihility.rbac.chat.gateway.protocol.body;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * {@code ERROR} 帧消息体（服务端 -> 客户端）：见
 * {@link cn.nihility.rbac.chat.constant.ChatErrorCode} 取值说明。
 */
@Getter
@Setter
@NoArgsConstructor
public class ErrorFrameBody {

    /** 错误码，见 {@link cn.nihility.rbac.chat.constant.ChatErrorCode}。 */
    private int code;

    /** 错误提示信息。 */
    private String message;

    /** 关联的客户端消息 msgId，与具体某次发送相关的错误才会携带，否则为 {@code null}。 */
    private String msgId;

    private ErrorFrameBody(int code, String message, String msgId) {
        this.code = code;
        this.message = message;
        this.msgId = msgId;
    }

    /**
     * 构造一个错误帧消息体。
     *
     * @param code    错误码
     * @param message 错误提示信息
     * @param msgId   关联的客户端消息 msgId，可为 {@code null}
     * @return 错误帧消息体
     */
    public static ErrorFrameBody of(int code, String message, String msgId) {
        return new ErrorFrameBody(code, message, msgId);
    }
}
