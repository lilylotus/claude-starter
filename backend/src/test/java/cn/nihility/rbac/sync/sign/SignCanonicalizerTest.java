package cn.nihility.rbac.sync.sign;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link SignCanonicalizer} 的单元测试：验证按 key 升序拼接、乱序输入产生相同结果。
 */
class SignCanonicalizerTest {

    /**
     * 按 key 的 ASCII 码升序拼接为 {@code k1=v1&k2=v2} 形式。
     */
    @Test
    void canonicalize_shouldSortByKeyAscending() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("ts", "1000");
        params.put("appKey", "abc");
        params.put("nonce", "xyz");

        String canonical = SignCanonicalizer.canonicalize(params);

        assertThat(canonical).isEqualTo("appKey=abc&nonce=xyz&ts=1000");
    }

    /**
     * 输入 Map 的插入顺序不影响拼接结果（同一组参数无论以何种顺序传入，规范化结果一致）。
     */
    @Test
    void canonicalize_shouldBeOrderIndependent() {
        Map<String, String> orderA = new LinkedHashMap<>();
        orderA.put("ts", "1000");
        orderA.put("appKey", "abc");

        Map<String, String> orderB = new LinkedHashMap<>();
        orderB.put("appKey", "abc");
        orderB.put("ts", "1000");

        assertThat(SignCanonicalizer.canonicalize(orderA)).isEqualTo(SignCanonicalizer.canonicalize(orderB));
    }
}
