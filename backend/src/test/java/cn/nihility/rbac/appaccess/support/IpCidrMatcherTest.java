package cn.nihility.rbac.appaccess.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * {@link IpCidrMatcher} 的测试（app-access-request-control change tasks.md 8.1），覆盖
 * 单 IP 精确匹配、CIDR 网段匹配（含网段边界地址）、非法格式、非 IPv4 地址（IPv6）。
 */
class IpCidrMatcherTest {

    /**
     * 单 IP 规则（不含 {@code /}）与客户端 IP 完全一致时应命中。
     */
    @Test
    void matches_shouldReturnTrue_whenSingleIpExactlyEquals() {
        assertThat(IpCidrMatcher.matches("192.168.1.100", "192.168.1.100")).isTrue();
    }

    /**
     * 单 IP 规则与客户端 IP 不一致时不应命中。
     */
    @Test
    void matches_shouldReturnFalse_whenSingleIpDiffers() {
        assertThat(IpCidrMatcher.matches("192.168.1.101", "192.168.1.100")).isFalse();
    }

    /**
     * CIDR 网段规则：客户端 IP 落在网段内（含网络地址本身、广播地址）应命中。
     */
    @Test
    void matches_shouldReturnTrue_whenIpWithinCidrRange() {
        assertThat(IpCidrMatcher.matches("192.168.1.0", "192.168.1.0/24")).isTrue();
        assertThat(IpCidrMatcher.matches("192.168.1.255", "192.168.1.0/24")).isTrue();
        assertThat(IpCidrMatcher.matches("192.168.1.128", "192.168.1.0/24")).isTrue();
    }

    /**
     * CIDR 网段规则：客户端 IP 落在网段边界之外（相邻网段）不应命中。
     */
    @Test
    void matches_shouldReturnFalse_whenIpOutsideCidrRange() {
        assertThat(IpCidrMatcher.matches("192.168.2.1", "192.168.1.0/24")).isFalse();
    }

    /**
     * CIDR 前缀长度不是 8 的整数倍时（如 {@code /25}），应逐位比较，正确区分网段内外的
     * 边界地址。
     */
    @Test
    void matches_shouldHandleNonByteAlignedPrefix() {
        // 192.168.1.0/25 网段范围为 192.168.1.0 ~ 192.168.1.127。
        assertThat(IpCidrMatcher.matches("192.168.1.127", "192.168.1.0/25")).isTrue();
        assertThat(IpCidrMatcher.matches("192.168.1.128", "192.168.1.0/25")).isFalse();
    }

    /**
     * {@code /32} 前缀等价于单 IP 精确匹配。
     */
    @Test
    void matches_shouldTreatSlash32_asExactMatch() {
        assertThat(IpCidrMatcher.matches("10.0.0.1", "10.0.0.1/32")).isTrue();
        assertThat(IpCidrMatcher.matches("10.0.0.2", "10.0.0.1/32")).isFalse();
    }

    /**
     * {@code /0} 前缀匹配任意 IPv4 地址。
     */
    @Test
    void matches_shouldMatchAnyAddress_whenPrefixIsZero() {
        assertThat(IpCidrMatcher.matches("8.8.8.8", "0.0.0.0/0")).isTrue();
    }

    /**
     * 规则格式不合法（既不是合法 IP，也不是合法 CIDR）时不应命中，不抛出异常。
     */
    @Test
    void matches_shouldReturnFalse_whenRuleFormatInvalid() {
        assertThat(IpCidrMatcher.matches("192.168.1.1", "not-an-ip")).isFalse();
        assertThat(IpCidrMatcher.matches("192.168.1.1", "192.168.1.0/33")).isFalse();
        assertThat(IpCidrMatcher.matches("192.168.1.1", "192.168.1.0/-1")).isFalse();
    }

    /**
     * 客户端 IP 或规则为空白/空字符串时不应命中。
     */
    @Test
    void matches_shouldReturnFalse_whenInputBlank() {
        assertThat(IpCidrMatcher.matches("", "192.168.1.0/24")).isFalse();
        assertThat(IpCidrMatcher.matches("192.168.1.1", "")).isFalse();
        assertThat(IpCidrMatcher.matches(null, "192.168.1.0/24")).isFalse();
    }

    /**
     * IPv6 客户端地址不应命中任何配置的 IPv4 CIDR 规则（Non-Goals：本期不处理 IPv6）。
     */
    @Test
    void matches_shouldReturnFalse_whenClientIpIsIpv6() {
        assertThat(IpCidrMatcher.matches("2001:db8::1", "192.168.1.0/24")).isFalse();
    }

    /**
     * {@link IpCidrMatcher#isValidRule} 对合法单 IP/CIDR 返回 {@code true}，非法格式/
     * 前缀越界返回 {@code false}。
     */
    @Test
    void isValidRule_shouldValidateFormat() {
        assertThat(IpCidrMatcher.isValidRule("192.168.1.100")).isTrue();
        assertThat(IpCidrMatcher.isValidRule("192.168.1.0/24")).isTrue();
        assertThat(IpCidrMatcher.isValidRule("192.168.1.0/0")).isTrue();
        assertThat(IpCidrMatcher.isValidRule("192.168.1.0/32")).isTrue();
        assertThat(IpCidrMatcher.isValidRule("not-an-ip")).isFalse();
        assertThat(IpCidrMatcher.isValidRule("192.168.1.0/33")).isFalse();
        assertThat(IpCidrMatcher.isValidRule("192.168.1.0/")).isFalse();
        assertThat(IpCidrMatcher.isValidRule("")).isFalse();
    }
}
