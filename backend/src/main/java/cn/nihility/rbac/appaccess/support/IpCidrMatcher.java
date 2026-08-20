package cn.nihility.rbac.appaccess.support;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import org.springframework.util.StringUtils;

/**
 * 手写的轻量级 IP/CIDR 匹配工具，不依赖任何第三方库（design.md Decision 4）：只处理
 * IPv4，用 {@link InetAddress} 解析出字节数组后逐位比较。识别不出/非 4 字节地址一律
 * 视为不匹配，不抛异常，调用方无需额外做异常处理（app-access-request-control change
 * Non-Goals：本期不处理 IPv6）。
 */
public final class IpCidrMatcher {

    /** IPv4 地址的字节数。 */
    private static final int IPV4_BYTE_LENGTH = 4;

    /** IPv4 地址的最大前缀长度（bit 数）。 */
    private static final int MAX_PREFIX_LENGTH = 32;

    /** CIDR 网段书写中，网络地址与前缀长度之间的分隔符。 */
    private static final char CIDR_SEPARATOR = '/';

    /**
     * 工具类不允许实例化。
     */
    private IpCidrMatcher() {
    }

    /**
     * 判断客户端 IP 是否命中给定的单 IP/CIDR 规则。
     *
     * @param clientIp    客户端 IP 字符串，取不到/为空时视为不匹配
     * @param ipCidrRule  单个 IP（如 {@code 192.168.1.100}）或 CIDR 网段（如
     *                    {@code 192.168.1.0/24}）
     * @return {@code true} 表示命中
     */
    public static boolean matches(String clientIp, String ipCidrRule) {
        if (!StringUtils.hasText(clientIp) || !StringUtils.hasText(ipCidrRule)) {
            return false;
        }
        byte[] clientBytes = toIpv4Bytes(clientIp);
        if (clientBytes == null) {
            return false;
        }

        int separatorIndex = ipCidrRule.indexOf(CIDR_SEPARATOR);
        if (separatorIndex < 0) {
            byte[] ruleBytes = toIpv4Bytes(ipCidrRule);
            return ruleBytes != null && Arrays.equals(clientBytes, ruleBytes);
        }

        String networkPart = ipCidrRule.substring(0, separatorIndex);
        String prefixPart = ipCidrRule.substring(separatorIndex + 1);
        byte[] networkBytes = toIpv4Bytes(networkPart);
        Integer prefixLength = toPrefixLength(prefixPart);
        if (networkBytes == null || prefixLength == null) {
            return false;
        }
        return withinNetwork(clientBytes, networkBytes, prefixLength);
    }

    /**
     * 校验单 IP/CIDR 规则的格式是否合法，供 Service 层保存前校验复用。
     *
     * @param ipCidrRule 单个 IP 或 CIDR 网段
     * @return {@code true} 表示格式合法
     */
    public static boolean isValidRule(String ipCidrRule) {
        if (!StringUtils.hasText(ipCidrRule)) {
            return false;
        }
        int separatorIndex = ipCidrRule.indexOf(CIDR_SEPARATOR);
        if (separatorIndex < 0) {
            return toIpv4Bytes(ipCidrRule) != null;
        }
        String networkPart = ipCidrRule.substring(0, separatorIndex);
        String prefixPart = ipCidrRule.substring(separatorIndex + 1);
        return toIpv4Bytes(networkPart) != null && toPrefixLength(prefixPart) != null;
    }

    /**
     * 把 IP 字符串解析为 4 字节的 IPv4 地址数组，解析失败或非 4 字节（如 IPv6）时返回
     * {@code null}。
     *
     * @param ip IP 字符串
     * @return 4 字节地址数组，解析失败时为 {@code null}
     */
    private static byte[] toIpv4Bytes(String ip) {
        try {
            byte[] bytes = InetAddress.getByName(ip.trim()).getAddress();
            return bytes.length == IPV4_BYTE_LENGTH ? bytes : null;
        } catch (UnknownHostException e) {
            return null;
        }
    }

    /**
     * 解析 CIDR 前缀长度，须为 0-32 之间的整数，非法（含非数字、越界）时返回 {@code null}。
     *
     * @param prefixPart CIDR {@code /} 后的前缀长度文本
     * @return 前缀长度，非法时为 {@code null}
     */
    private static Integer toPrefixLength(String prefixPart) {
        try {
            int prefixLength = Integer.parseInt(prefixPart.trim());
            return prefixLength >= 0 && prefixLength <= MAX_PREFIX_LENGTH ? prefixLength : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 判断客户端地址是否落在给定网络地址 + 前缀长度圈定的网段内：逐字节比较完整字节，
     * 剩余不足 8 位的部分按位掩码比较。
     *
     * @param clientBytes  客户端地址字节数组，长度 4
     * @param networkBytes 网络地址字节数组，长度 4
     * @param prefixLength CIDR 前缀长度，0-32
     * @return {@code true} 表示客户端地址落在该网段内
     */
    private static boolean withinNetwork(byte[] clientBytes, byte[] networkBytes, int prefixLength) {
        int fullBytes = prefixLength / 8;
        int remainingBits = prefixLength % 8;
        for (int i = 0; i < fullBytes; i++) {
            if (clientBytes[i] != networkBytes[i]) {
                return false;
            }
        }
        if (remainingBits == 0) {
            return true;
        }
        int mask = 0xFF << (8 - remainingBits) & 0xFF;
        return (clientBytes[fullBytes] & mask) == (networkBytes[fullBytes] & mask);
    }
}
