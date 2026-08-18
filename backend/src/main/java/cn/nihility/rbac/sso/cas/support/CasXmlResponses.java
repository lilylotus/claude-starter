package cn.nihility.rbac.sso.cas.support;

/**
 * CAS 3.0 {@code serviceResponse} XML 响应拼接工具（app-sso-protocol-runtime change
 * design.md Decision 5）。
 */
public final class CasXmlResponses {

    /**
     * 工具类不允许实例化。
     */
    private CasXmlResponses() {
    }

    /**
     * 构造认证成功的 CAS 3.0 XML 响应。
     *
     * @param user 用户标识（{@code tab_user.code}）
     * @param name 用户姓名
     * @return CAS 3.0 认证成功 XML 响应
     */
    public static String success(String user, String name) {
        return "<cas:serviceResponse xmlns:cas=\"http://www.yale.edu/tp/cas\">\n"
                + "  <cas:authenticationSuccess>\n"
                + "    <cas:user>" + escape(user) + "</cas:user>\n"
                + "    <cas:attributes><cas:name>" + escape(name) + "</cas:name></cas:attributes>\n"
                + "  </cas:authenticationSuccess>\n"
                + "</cas:serviceResponse>";
    }

    /**
     * 构造认证失败的 CAS 3.0 XML 响应。
     *
     * @param code    失败代码
     * @param message 失败提示信息
     * @return CAS 3.0 认证失败 XML 响应
     */
    public static String failure(String code, String message) {
        return "<cas:serviceResponse xmlns:cas=\"http://www.yale.edu/tp/cas\">\n"
                + "  <cas:authenticationFailure code=\"" + escape(code) + "\">" + escape(message)
                + "</cas:authenticationFailure>\n"
                + "</cas:serviceResponse>";
    }

    /**
     * 转义 XML 特殊字符，避免用户标识/姓名中含有 {@code <}/{@code &} 等字符破坏 XML 结构。
     *
     * @param value 原始文本，可能为 {@code null}
     * @return 转义后的文本
     */
    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
