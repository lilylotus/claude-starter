package cn.nihility.rbac.common.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

/**
 * HTTP 请求客户端信息解析共享工具，不接入 Spring 容器，全部为无状态静态方法。当前只提供
 * 客户端 IP 解析，逻辑从 {@code cn.nihility.rbac.loginlog.service.impl.LoginLogRecorderImpl}
 * 的私有实现原样提炼而来（app-access-request-control change design.md Decision 6），
 * 该模块与本次新增的 SSO 登录拦截请求控制校验共用同一份逻辑，避免两处维护同一段实现后续
 * 漂移。
 */
public final class ClientRequestUtils {

    /** 反向代理场景下客户端真实 IP 所在的请求头。 */
    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

    /**
     * 工具类不允许实例化。
     */
    private ClientRequestUtils() {
    }

    /**
     * 解析当前请求的客户端 IP：优先取 {@code X-Forwarded-For} 请求头的第一个 IP（兼容
     * 经反向代理/网关的场景），否则取 {@code request.getRemoteAddr()}；请求为空时返回
     * {@code null}。
     *
     * @param request 当前 HTTP 请求，可为 {@code null}
     * @return 客户端 IP，取不到时为 {@code null}
     */
    public static String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwardedFor = request.getHeader(FORWARDED_FOR_HEADER);
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
