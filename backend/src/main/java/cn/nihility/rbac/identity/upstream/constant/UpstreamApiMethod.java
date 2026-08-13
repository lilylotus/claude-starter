package cn.nihility.rbac.identity.upstream.constant;

import java.util.Set;

/**
 * 接口方式数据域配置的 HTTP 请求方式常量。取值直接对应
 * {@code tab_upstream_domain_config.api_method} 列的存储值。
 */
public final class UpstreamApiMethod {

    /** GET 请求。 */
    public static final String GET = "GET";

    /** POST 请求。 */
    public static final String POST = "POST";

    /** 全部取值，供请求参数校验使用。 */
    public static final Set<String> ALL_METHODS = Set.of(GET, POST);

    /**
     * 工具类不允许实例化。
     */
    private UpstreamApiMethod() {
    }
}
