package cn.nihility.rbac.identity.upstream.constant;

import java.util.Set;

/**
 * 上游数据源同步方式常量：接口拉取或数据库表查询，二选一（proposal.md）。取值直接对应
 * {@code tab_upstream_source.sync_type} 列的存储值。
 */
public final class UpstreamSyncType {

    /** 接口方式：HTTP 拉取。 */
    public static final String API = "API";

    /** 数据库表方式：JDBC 查询。 */
    public static final String DB_TABLE = "DB_TABLE";

    /** 全部取值，供请求参数校验使用。 */
    public static final Set<String> ALL_TYPES = Set.of(API, DB_TABLE);

    /**
     * 工具类不允许实例化。
     */
    private UpstreamSyncType() {
    }
}
