package cn.nihility.rbac.operationlog.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

/**
 * 手写的轻量级 User-Agent 解析工具，不依赖任何第三方库：通过正则从 User-Agent
 * 请求头里提取浏览器名称/主版本号、操作系统名称/版本、操作终端类型。只覆盖主流
 * 桌面/移动浏览器与操作系统，识别不出时对应字段返回 {@code null}，不追求覆盖所有
 * 小众客户端/爬虫。
 */
public final class UserAgentParser {

    /** Edge 浏览器版本号正则，需在 Chrome 之前匹配——Edge 的 User-Agent 同样包含 "Chrome" 关键字。 */
    private static final Pattern EDGE = Pattern.compile("Edg(?:e|A|iOS)?/([\\d.]+)");

    /** Opera 浏览器版本号正则，需在 Chrome 之前匹配——Opera 的 User-Agent 同样包含 "Chrome"/"Safari" 关键字。 */
    private static final Pattern OPERA = Pattern.compile("(?:OPR|Opera)/([\\d.]+)");

    /** Firefox 浏览器版本号正则。 */
    private static final Pattern FIREFOX = Pattern.compile("Firefox/([\\d.]+)");

    /** Chrome 浏览器版本号正则。 */
    private static final Pattern CHROME = Pattern.compile("Chrome/([\\d.]+)");

    /** Safari 浏览器版本号正则，需在 Chrome 之后匹配——Chrome 的 User-Agent 同样包含 "Safari" 关键字。 */
    private static final Pattern SAFARI_VERSION = Pattern.compile("Version/([\\d.]+).*Safari");

    /** IE11（Trident 内核）版本号正则。 */
    private static final Pattern IE_TRIDENT = Pattern.compile("Trident/.*rv:([\\d.]+)");

    /** IE10 及更早版本号正则。 */
    private static final Pattern IE_MSIE = Pattern.compile("MSIE ([\\d.]+)");

    /** Windows 内核版本号正则。 */
    private static final Pattern WINDOWS_NT = Pattern.compile("Windows NT ([\\d.]+)");

    /** Android 系统版本号正则。 */
    private static final Pattern ANDROID = Pattern.compile("Android ([\\d.]+)");

    /** iOS 系统版本号正则（iPhone/iPad/iPod）。 */
    private static final Pattern IOS = Pattern.compile("(?:iPhone|iPad|iPod).*OS ([\\d_]+)");

    /** macOS 系统版本号正则。 */
    private static final Pattern MAC_OS = Pattern.compile("Mac OS X ([\\d_.]+)");

    /**
     * 工具类不允许实例化。
     */
    private UserAgentParser() {
    }

    /**
     * 从 User-Agent 中解析浏览器名称与主版本号，如 "Chrome 120"；识别不出时返回
     * {@code null}。
     *
     * @param userAgent 原始 User-Agent 字符串
     * @return 浏览器名称 + 主版本号，识别不出时为 {@code null}
     */
    public static String parseBrowser(String userAgent) {
        if (!StringUtils.hasText(userAgent)) {
            return null;
        }
        String label = matchVersion(EDGE, userAgent, "Edge");
        if (label == null) {
            label = matchVersion(OPERA, userAgent, "Opera");
        }
        if (label == null) {
            label = matchVersion(FIREFOX, userAgent, "Firefox");
        }
        if (label == null) {
            label = matchVersion(CHROME, userAgent, "Chrome");
        }
        if (label == null) {
            label = matchVersion(SAFARI_VERSION, userAgent, "Safari");
        }
        if (label == null) {
            label = matchVersion(IE_TRIDENT, userAgent, "IE");
        }
        if (label == null) {
            label = matchVersion(IE_MSIE, userAgent, "IE");
        }
        return label;
    }

    /**
     * 从 User-Agent 中解析操作系统名称与版本，如 "Windows 10"、"macOS 14.2"、
     * "Android 14"、"iOS 17.2"；识别不出时返回 {@code null}。
     *
     * @param userAgent 原始 User-Agent 字符串
     * @return 操作系统名称 + 版本，识别不出时为 {@code null}
     */
    public static String parseOs(String userAgent) {
        if (!StringUtils.hasText(userAgent)) {
            return null;
        }
        Matcher ios = IOS.matcher(userAgent);
        if (ios.find()) {
            return "iOS " + ios.group(1).replace('_', '.');
        }
        Matcher android = ANDROID.matcher(userAgent);
        if (android.find()) {
            return "Android " + android.group(1);
        }
        Matcher windows = WINDOWS_NT.matcher(userAgent);
        if (windows.find()) {
            return windowsName(windows.group(1));
        }
        Matcher mac = MAC_OS.matcher(userAgent);
        if (mac.find()) {
            return "macOS " + mac.group(1).replace('_', '.');
        }
        if (userAgent.contains("Linux")) {
            return "Linux";
        }
        return null;
    }

    /**
     * 从 User-Agent 中粗略判断操作终端类型：平板/移动设备/电脑。
     *
     * @param userAgent 原始 User-Agent 字符串
     * @return "Tablet"/"Mobile"/"Computer"，User-Agent 为空时返回 {@code null}
     */
    public static String parseTerminal(String userAgent) {
        if (!StringUtils.hasText(userAgent)) {
            return null;
        }
        if (userAgent.contains("iPad") || (userAgent.contains("Android") && !userAgent.contains("Mobile"))) {
            return "Tablet";
        }
        if (userAgent.contains("iPhone") || userAgent.contains("iPod") || userAgent.contains("Android")
                || userAgent.contains("Windows Phone")) {
            return "Mobile";
        }
        return "Computer";
    }

    /**
     * 用给定正则从 User-Agent 中提取版本号，拼出 "浏览器名 主版本号" 展示文案。
     *
     * @param pattern     版本号正则，捕获组 1 为版本号
     * @param userAgent   原始 User-Agent 字符串
     * @param browserName 浏览器展示名称
     * @return 拼接后的展示文案，未匹配到时为 {@code null}
     */
    private static String matchVersion(Pattern pattern, String userAgent, String browserName) {
        Matcher matcher = pattern.matcher(userAgent);
        if (matcher.find()) {
            return browserName + " " + majorVersion(matcher.group(1));
        }
        return null;
    }

    /**
     * 取版本号字符串的主版本号（第一个 "." 之前的部分）。
     *
     * @param version 完整版本号，如 "120.0.0.0"
     * @return 主版本号，如 "120"
     */
    private static String majorVersion(String version) {
        int dotIndex = version.indexOf('.');
        return dotIndex > 0 ? version.substring(0, dotIndex) : version;
    }

    /**
     * 把 Windows NT 内核版本号映射为常见的市场名称。Windows 11 与 Windows 10 共用
     * NT 10.0 内核版本号，浏览器 User-Agent 本身不做区分，这里统一映射为
     * "Windows 10"，是操作系统层面已知的固有限制，不是本方法的解析缺陷。
     *
     * @param ntVersion NT 内核版本号，如 "10.0"
     * @return 市场名称，如 "Windows 10"；无法识别的版本号原样拼接为 "Windows NT x.x"
     */
    private static String windowsName(String ntVersion) {
        return switch (ntVersion) {
            case "10.0" -> "Windows 10";
            case "6.3" -> "Windows 8.1";
            case "6.2" -> "Windows 8";
            case "6.1" -> "Windows 7";
            case "6.0" -> "Windows Vista";
            case "5.1", "5.2" -> "Windows XP";
            default -> "Windows NT " + ntVersion;
        };
    }
}
