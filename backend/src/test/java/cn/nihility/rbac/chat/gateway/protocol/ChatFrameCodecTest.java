package cn.nihility.rbac.chat.gateway.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** {@link ChatFrameCodec} 编解码往返一致性与异常帧头处理测试。 */
class ChatFrameCodecTest {

    /** 编码后再解码应还原出等价的消息类型与消息体。 */
    @Test
    void encodeThenDecode_shouldRoundTrip() {
        ChatFrame original = ChatFrame.of(ChatFrameType.CHAT_SINGLE, new TestBody("hello"));

        ByteBuf encoded = ChatFrameCodec.encode(original, UnpooledByteBufAllocator.DEFAULT);
        ChatFrame decoded = ChatFrameCodec.decode(encoded);

        assertThat(decoded.getType()).isEqualTo(ChatFrameType.CHAT_SINGLE);
        assertThat(decoded.bodyAsText()).contains("hello");
    }

    /** 魔数不匹配应抛出异常。 */
    @Test
    void decode_shouldRejectInvalidMagic() {
        ByteBuf buf = Unpooled.buffer();
        buf.writeInt(0xDEADBEEF);
        buf.writeByte(ChatFrameCodec.VERSION);
        buf.writeByte(ChatFrameType.HEARTBEAT.getCode());
        buf.writeInt(0);

        assertThatThrownBy(() -> ChatFrameCodec.decode(buf)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("魔数");
    }

    /** 长度域与实际消息体长度不一致应抛出异常。 */
    @Test
    void decode_shouldRejectLengthMismatch() {
        ByteBuf buf = Unpooled.buffer();
        buf.writeInt(ChatFrameCodec.MAGIC);
        buf.writeByte(ChatFrameCodec.VERSION);
        buf.writeByte(ChatFrameType.HEARTBEAT.getCode());
        buf.writeInt(10);
        buf.writeBytes("abc".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> ChatFrameCodec.decode(buf)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("长度域");
    }

    /** 帧头不完整（可读字节数不足）应抛出异常。 */
    @Test
    void decode_shouldRejectIncompleteHeader() {
        ByteBuf buf = Unpooled.buffer();
        buf.writeInt(ChatFrameCodec.MAGIC);

        assertThatThrownBy(() -> ChatFrameCodec.decode(buf)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("长度不足");
    }

    /** 简单的测试用消息体。 */
    private record TestBody(String value) {
    }
}
