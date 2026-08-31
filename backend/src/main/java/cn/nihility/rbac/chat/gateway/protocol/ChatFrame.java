package cn.nihility.rbac.chat.gateway.protocol;

import cn.nihility.rbac.common.util.JacksonUtils;
import java.nio.charset.StandardCharsets;
import lombok.Getter;

/**
 * 聊天网关协议帧的内存表示：消息类型 + 消息体原始字节（UTF-8 编码的 JSON，design.md
 * Decision 2/3）。本类只持有一个普通 {@code byte[]}，不持有 Netty {@link io.netty.buffer.ByteBuf}
 * 引用，可以安全地被同一实例多次编码写入不同 Channel（多端在线广播场景），不涉及引用计数
 * 管理。与 {@link io.netty.channel.socket.nio.NioSocketChannel} 之间的字节级编解码由
 * {@code ChatFrameCodec} 负责。
 */
@Getter
public final class ChatFrame {

    /** 消息类型。 */
    private final ChatFrameType type;

    /** 消息体原始字节（UTF-8 编码的 JSON），可能为空数组（如心跳帧）。 */
    private final byte[] body;

    /**
     * 构造一个协议帧。
     *
     * @param type 消息类型
     * @param body 消息体原始字节
     */
    public ChatFrame(ChatFrameType type, byte[] body) {
        this.type = type;
        this.body = body == null ? new byte[0] : body;
    }

    /**
     * 把消息体解析为 UTF-8 文本，供日志/调试使用。
     *
     * @return 消息体文本
     */
    public String bodyAsText() {
        return new String(body, StandardCharsets.UTF_8);
    }

    /**
     * 基于任意对象构造一个协议帧，对象会被序列化为 JSON 作为消息体；{@code bodyObject} 为
     * {@code null} 时消息体为空 JSON 对象 {@code {}}。
     *
     * @param type       消息类型
     * @param bodyObject 消息体对象，可为 {@code null}
     * @return 协议帧
     */
    public static ChatFrame of(ChatFrameType type, Object bodyObject) {
        String json = bodyObject == null ? "{}" : JacksonUtils.toJson(bodyObject);
        return new ChatFrame(type, json.getBytes(StandardCharsets.UTF_8));
    }
}
