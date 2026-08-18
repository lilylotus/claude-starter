package cn.nihility.rbac.sso.cas.support;

import java.util.Map;

/**
 * CAS 3.0 {@code serviceResponse} XML 响应拼接工具（app-sso-protocol-runtime change
 * design.md Decision 5；{@code success} 签名变更见 add-sso-userinfo-field-mapping change
 * design.md Decision 8）。
 */
public final class CasXmlResponses {

    /**
     * 工具类不允许实例化。
     */
    private CasXmlResponses() {
    }

    /**
     * 构造认证成功的 CAS 3.0 XML 响应。{@code cas:user} 固定取用户 code，不受
     * {@code attributes} 影响；{@code attributes} 内每个 entry 生成一个同名子元素，
     * {@code attributes} 为空时不生成 {@code <cas:attributes>} 整个节点。
     *
     * @param user       用户标识（{@code tab_user.code}）
     * @param attributes 用户信息响应属性，按用户信息字段映射配置动态生成，可能为空
     * @return CAS 3.0 认证成功 XML 响应
     */
    public static String success(String user, Map<String, Object> attributes) {
        StringBuilder body = new StringBuilder();
        body.append("<cas:serviceResponse xmlns:cas=\"http://www.yale.edu/tp/cas\">\n")
                .append("  <cas:authenticationSuccess>\n")
                .append("    <cas:user>").append(escape(user)).append("</cas:user>\n");
        if (attributes != null && !attributes.isEmpty()) {
            body.append("    <cas:attributes>");
            for (Map.Entry<String, Object> entry : attributes.entrySet()) {
                String tagName = entry.getKey();
                String value = entry.getValue() != null ? entry.getValue().toString() : "";
                body.append("<cas:").append(tagName).append('>').append(escape(value))
                        .append("</cas:").append(tagName).append('>');
            }
            body.append("</cas:attributes>\n");
        }
        body.append("  </cas:authenticationSuccess>\n").append("</cas:serviceResponse>");
        return body.toString();
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
