package cn.nihility.rbac.sync.openapi.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link SyncDigestCanonicalCodec} 的单元测试，覆盖键按字典序排列（与 {@link Map} 插入顺序
 * 无关）、{@code null} 值显式保留不省略字段两条 canonical 编码核心约束
 * （app-sync-changelog-pull change design.md Decision 10）。
 */
class SyncDigestCanonicalCodecTest {

    /**
     * 两份内容相同但 {@link Map} 插入顺序不同的记录，编码结果应完全一致。
     */
    @Test
    void encode_shouldBeStable_regardlessOfMapInsertionOrder() {
        Map<String, Object> record1 = new LinkedHashMap<>();
        record1.put("bizId", 1L);
        record1.put("bizCode", "ORG001");
        record1.put("name", "组织一");

        Map<String, Object> record2 = new LinkedHashMap<>();
        record2.put("name", "组织一");
        record2.put("bizId", 1L);
        record2.put("bizCode", "ORG001");

        assertThat(SyncDigestCanonicalCodec.encode(record1)).isEqualTo(SyncDigestCanonicalCodec.encode(record2));
    }

    /**
     * {@code null} 值应显式保留在编码结果中（而不是被省略），保证含 {@code null} 字段与不含
     * 该字段的两份记录编码结果不同。
     */
    @Test
    void encode_shouldKeepNullValuesExplicitly() {
        Map<String, Object> withNull = new HashMap<>();
        withNull.put("remark", null);
        withNull.put("bizId", 1L);

        Map<String, Object> withoutField = new HashMap<>();
        withoutField.put("bizId", 1L);

        byte[] encodedWithNull = SyncDigestCanonicalCodec.encode(withNull);
        byte[] encodedWithoutField = SyncDigestCanonicalCodec.encode(withoutField);

        assertThat(encodedWithNull).isNotEqualTo(encodedWithoutField);
        assertThat(new String(encodedWithNull, java.nio.charset.StandardCharsets.UTF_8)).contains("\"remark\":null");
    }

    /**
     * {@code LocalDateTime} 值应格式化为固定的可读字符串，保证不同调用之间编码结果稳定。
     */
    @Test
    void encode_shouldFormatLocalDateTimeConsistently() {
        Map<String, Object> record = new HashMap<>();
        record.put("updateTime", LocalDateTime.of(2026, 1, 1, 12, 30, 0));

        String json = new String(SyncDigestCanonicalCodec.encode(record), java.nio.charset.StandardCharsets.UTF_8);

        assertThat(json).contains("\"updateTime\":\"2026-01-01 12:30:00\"");
    }
}
