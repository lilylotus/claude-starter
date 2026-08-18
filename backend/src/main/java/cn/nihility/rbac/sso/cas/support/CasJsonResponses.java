package cn.nihility.rbac.sso.cas.support;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CAS 3.0 {@code serviceResponse} JSON 响应构造工具（add-sso-userinfo-field-mapping change
 * design.md Decision 8），风格对齐 {@link CasXmlResponses}，经
 * {@code ProtocolResponseWriter.json} 输出。
 */
public final class CasJsonResponses {

    /**
     * 工具类不允许实例化。
     */
    private CasJsonResponses() {
    }

    /**
     * 构造认证成功的 CAS 3.0 JSON 响应体：
     * {@code {"serviceResponse":{"authenticationSuccess":{"user":"...","attributes":{...}}}}}。
     * {@code user} 固定取用户 code，不受 {@code attributes} 影响；{@code attributes} 为空时
     * 不生成 {@code attributes} 节点。
     *
     * @param user       用户标识（{@code tab_user.code}）
     * @param attributes 用户信息响应属性，按用户信息字段映射配置动态生成，可能为空
     * @return CAS 3.0 认证成功 JSON 响应体
     */
    public static Map<String, Object> success(String user, Map<String, Object> attributes) {
        Map<String, Object> authenticationSuccess = new LinkedHashMap<>();
        authenticationSuccess.put("user", user);
        if (attributes != null && !attributes.isEmpty()) {
            authenticationSuccess.put("attributes", attributes);
        }

        Map<String, Object> serviceResponse = new LinkedHashMap<>();
        serviceResponse.put("authenticationSuccess", authenticationSuccess);
        return Map.of("serviceResponse", serviceResponse);
    }

    /**
     * 构造认证失败的 CAS 3.0 JSON 响应体：
     * {@code {"serviceResponse":{"authenticationFailure":{"code":"...","description":"..."}}}}。
     *
     * @param code    失败代码
     * @param message 失败提示信息
     * @return CAS 3.0 认证失败 JSON 响应体
     */
    public static Map<String, Object> failure(String code, String message) {
        Map<String, Object> authenticationFailure = new LinkedHashMap<>();
        authenticationFailure.put("code", code);
        authenticationFailure.put("description", message);

        Map<String, Object> serviceResponse = new LinkedHashMap<>();
        serviceResponse.put("authenticationFailure", authenticationFailure);
        return Map.of("serviceResponse", serviceResponse);
    }
}
