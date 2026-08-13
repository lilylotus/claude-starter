package cn.nihility.rbac.identity.upstream.constant;

import java.util.Set;

/**
 * 上游数据源按间隔调度时的间隔单位常量（design.md Decision 2）。取值直接对应
 * {@code tab_upstream_source.interval_unit} 列的存储值。
 */
public final class UpstreamIntervalUnit {

    /** 分钟。 */
    public static final String MINUTE = "MINUTE";

    /** 小时。 */
    public static final String HOUR = "HOUR";

    /** 全部取值，供请求参数校验使用。 */
    public static final Set<String> ALL_UNITS = Set.of(MINUTE, HOUR);

    /**
     * 工具类不允许实例化。
     */
    private UpstreamIntervalUnit() {
    }
}
