package cn.nihility.rbac.chat.gateway.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;

/**
 * 聊天网关自定义应用层协议帧的字节级编解码器（design.md Decision 2/3）：
 * <pre>
 * +----------+---------+-----------+--------------+------------------+
 * | magic 4B | version | msgType   | bodyLength   | body（JSON UTF-8）|
 * |          | 1B      | 1B        | 4B           | bodyLength 字节   |
 * +----------+---------+-----------+--------------+------------------+
 * </pre>
 * 帧头固定 10 字节。本阶段传输层是 WebSocket（{@code BinaryWebSocketFrame}），每个 WebSocket
 * 消息帧（经 {@code WebSocketFrameAggregator} 聚合分片后）恰好承载一个完整的协议帧，因此
 * {@link #decode} 要求传入的 {@link ByteBuf} 可读字节数与帧头声明的 {@code bodyLength}
 * 严格一致；帧头结构本身不依赖具体传输方式，为后续裸 TCP 传输（需要
 * {@code LengthFieldBasedFrameDecoder} 处理粘包半包）复用同一套帧结构留出空间。
 */
public final class ChatFrameCodec {

    /** 协议魔数（ASCII "CHAT"），用于快速识别/校验协议帧，防止误解析非本协议流量。 */
    public static final int MAGIC = 0x43_48_41_54;

    /** 当前协议版本，为后续协议升级预留版本协商空间。 */
    public static final byte VERSION = 1;

    /** 固定帧头长度（魔数 4B + 版本 1B + 消息类型 1B + 长度域 4B）。 */
    public static final int HEADER_LENGTH = 10;

    /**
     * 工具类不允许实例化。
     */
    private ChatFrameCodec() {
    }

    /**
     * 把协议帧编码为字节缓冲区。
     *
     * @param frame     待编码的协议帧
     * @param allocator 字节缓冲区分配器（通常取自目标 Channel 的 {@code alloc()}）
     * @return 编码后的字节缓冲区
     */
    public static ByteBuf encode(ChatFrame frame, ByteBufAllocator allocator) {
        byte[] body = frame.getBody();
        ByteBuf buf = allocator.buffer(HEADER_LENGTH + body.length);
        buf.writeInt(MAGIC);
        buf.writeByte(VERSION);
        buf.writeByte(frame.getType().getCode());
        buf.writeInt(body.length);
        buf.writeBytes(body);
        return buf;
    }

    /**
     * 把字节缓冲区解码为协议帧；缓冲区必须恰好是"一个完整帧头 + 声明长度的消息体"，
     * 不多不少。
     *
     * @param buf 待解码的字节缓冲区
     * @return 解码后的协议帧
     * @throws IllegalArgumentException 帧头损坏、魔数/版本不匹配、消息类型未知、
     *                                   长度域与实际可读字节数不一致
     */
    public static ChatFrame decode(ByteBuf buf) {
        if (buf.readableBytes() < HEADER_LENGTH) {
            throw new IllegalArgumentException("协议帧长度不足，无法解析帧头");
        }
        int magic = buf.readInt();
        if (magic != MAGIC) {
            throw new IllegalArgumentException("协议帧魔数不匹配");
        }
        byte version = buf.readByte();
        if (version != VERSION) {
            throw new IllegalArgumentException("不支持的协议版本：" + version);
        }
        byte typeCode = buf.readByte();
        ChatFrameType type = ChatFrameType.fromCode(typeCode);
        if (type == null) {
            throw new IllegalArgumentException("未知的消息类型：" + typeCode);
        }
        int bodyLength = buf.readInt();
        if (bodyLength < 0 || buf.readableBytes() != bodyLength) {
            throw new IllegalArgumentException("协议帧长度域与实际消息体长度不一致");
        }
        byte[] body = new byte[bodyLength];
        buf.readBytes(body);
        return new ChatFrame(type, body);
    }
}
