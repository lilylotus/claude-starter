package cn.nihility.rbac.appaccess.policy.constant;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.util.StringUtils;

/**
 * 策略请求控制条件-浏览器白名单枚举常量（design.md Decision 1）：取值与
 * {@code cn.nihility.rbac.operationlog.util.UserAgentParser#parseBrowser} 能识别的
 * 浏览器一一对应，不支持自定义 User-Agent 关键字匹配。
 */
public final class PolicyRequestControlBrowser {

    /** Chrome。 */
    public static final String CHROME = "CHROME";

    /** Firefox。 */
    public static final String FIREFOX = "FIREFOX";

    /** Safari。 */
    public static final String SAFARI = "SAFARI";

    /** Edge。 */
    public static final String EDGE = "EDGE";

    /** Opera。 */
    public static final String OPERA = "OPERA";

    /** IE。 */
    public static final String IE = "IE";

    /** 全部合法取值，供保存时校验请求参数使用。 */
    public static final Set<String> ALL_CODES = Set.of(CHROME, FIREFOX, SAFARI, EDGE, OPERA, IE);

    /** 编码到展示名称的映射。 */
    private static final Map<String, String> LABELS = Map.of(
            CHROME, "Chrome",
            FIREFOX, "Firefox",
            SAFARI, "Safari",
            EDGE, "Edge",
            OPERA, "Opera",
            IE, "IE");

    /**
     * 工具类不允许实例化。
     */
    private PolicyRequestControlBrowser() {
    }

    /**
     * 按编码取展示名称，未知编码原样返回编码本身。
     *
     * @param browserCode 浏览器编码
     * @return 展示名称
     */
    public static String label(String browserCode) {
        return LABELS.getOrDefault(browserCode, browserCode);
    }

    /**
     * 把 {@code UserAgentParser.parseBrowser} 的返回值（如 {@code "Chrome 120"}）解析为
     * 本枚举的编码；识别不出（含 {@code null}、非受支持浏览器）时返回 {@code null}。
     *
     * @param parsedBrowser {@code UserAgentParser.parseBrowser} 的返回值
     * @return 浏览器编码，识别不出时为 {@code null}
     */
    public static String resolveCode(String parsedBrowser) {
        if (!StringUtils.hasText(parsedBrowser)) {
            return null;
        }
        String name = parsedBrowser.split(" ")[0].toUpperCase(Locale.ROOT);
        return ALL_CODES.contains(name) ? name : null;
    }
}
