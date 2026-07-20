package cn.nihility.rbac.operationlog.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * {@link UserAgentParser} 的单元测试，覆盖主流桌面/移动浏览器与操作系统的解析结果，
 * 以及无法识别时各字段应为 {@code null} 的兜底行为。这里刻意用真实版本较新的
 * User-Agent（如 Chrome 120）验证主版本号解析不会像某些年久失修的第三方 UA 解析库
 * 那样把 "Chrome/120.0.0.0" 误判成过时的 "Chrome 12"。
 */
class UserAgentParserTest {

    /** 桌面 Chrome（Windows）的浏览器/操作系统/终端类型均应正确解析，且不截断主版本号。 */
    @Test
    void shouldParseModernDesktopChromeOnWindows() {
        String ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                + "Chrome/120.0.0.0 Safari/537.36";

        assertThat(UserAgentParser.parseBrowser(ua)).isEqualTo("Chrome 120");
        assertThat(UserAgentParser.parseOs(ua)).isEqualTo("Windows 10");
        assertThat(UserAgentParser.parseTerminal(ua)).isEqualTo("Computer");
    }

    /** Edge 的 User-Agent 同样包含 "Chrome" 关键字，应优先识别为 Edge 而不是 Chrome。 */
    @Test
    void shouldParseEdgeInsteadOfChrome_whenBothKeywordsPresent() {
        String ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                + "Chrome/120.0.0.0 Safari/537.36 Edg/120.0.2210.91";

        assertThat(UserAgentParser.parseBrowser(ua)).isEqualTo("Edge 120");
    }

    /** macOS 桌面 Safari 的浏览器/操作系统均应正确解析。 */
    @Test
    void shouldParseSafariOnMac() {
        String ua = "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_2_1) AppleWebKit/605.1.15 (KHTML, like Gecko) "
                + "Version/17.2 Safari/605.1.15";

        assertThat(UserAgentParser.parseBrowser(ua)).isEqualTo("Safari 17");
        assertThat(UserAgentParser.parseOs(ua)).isEqualTo("macOS 14.2.1");
        assertThat(UserAgentParser.parseTerminal(ua)).isEqualTo("Computer");
    }

    /** iPhone Safari 应识别为 iOS + Mobile 终端。 */
    @Test
    void shouldParseIphoneSafariAsMobile() {
        String ua = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_2 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) "
                + "Version/17.2 Mobile/15E148 Safari/604.1";

        assertThat(UserAgentParser.parseOs(ua)).isEqualTo("iOS 17.2");
        assertThat(UserAgentParser.parseTerminal(ua)).isEqualTo("Mobile");
    }

    /** Android 手机 Chrome 应识别为 Android + Mobile 终端。 */
    @Test
    void shouldParseAndroidPhoneChromeAsMobile() {
        String ua = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) "
                + "Chrome/120.0.0.0 Mobile Safari/537.36";

        assertThat(UserAgentParser.parseBrowser(ua)).isEqualTo("Chrome 120");
        assertThat(UserAgentParser.parseOs(ua)).isEqualTo("Android 14");
        assertThat(UserAgentParser.parseTerminal(ua)).isEqualTo("Mobile");
    }

    /** Android 平板（不含 Mobile 关键字）应识别为 Tablet 终端。 */
    @Test
    void shouldParseAndroidTabletAsTablet() {
        String ua = "Mozilla/5.0 (Linux; Android 14; SM-X710) AppleWebKit/537.36 (KHTML, like Gecko) "
                + "Chrome/120.0.0.0 Safari/537.36";

        assertThat(UserAgentParser.parseTerminal(ua)).isEqualTo("Tablet");
    }

    /** iPad 应识别为 Tablet 终端。 */
    @Test
    void shouldParseIpadAsTablet() {
        String ua = "Mozilla/5.0 (iPad; CPU OS 17_2 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) "
                + "Version/17.2 Mobile/15E148 Safari/604.1";

        assertThat(UserAgentParser.parseTerminal(ua)).isEqualTo("Tablet");
    }

    /** Firefox（Linux 桌面）应正确解析浏览器与操作系统。 */
    @Test
    void shouldParseFirefoxOnLinux() {
        String ua = "Mozilla/5.0 (X11; Linux x86_64; rv:121.0) Gecko/20100101 Firefox/121.0";

        assertThat(UserAgentParser.parseBrowser(ua)).isEqualTo("Firefox 121");
        assertThat(UserAgentParser.parseOs(ua)).isEqualTo("Linux");
        assertThat(UserAgentParser.parseTerminal(ua)).isEqualTo("Computer");
    }

    /** 无法识别的 User-Agent，浏览器/操作系统应为 null，终端类型兜底为 Computer。 */
    @Test
    void shouldReturnNullForUnrecognizableUserAgent() {
        String ua = "SomeCustomBot/1.0";

        assertThat(UserAgentParser.parseBrowser(ua)).isNull();
        assertThat(UserAgentParser.parseOs(ua)).isNull();
        assertThat(UserAgentParser.parseTerminal(ua)).isEqualTo("Computer");
    }

    /** 空白/空 User-Agent 时全部字段应为 null。 */
    @Test
    void shouldReturnNullForBlankUserAgent() {
        assertThat(UserAgentParser.parseBrowser("")).isNull();
        assertThat(UserAgentParser.parseOs(null)).isNull();
        assertThat(UserAgentParser.parseTerminal("   ")).isNull();
    }
}
