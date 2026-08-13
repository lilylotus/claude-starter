package cn.nihility.rbac.identity.upstream.constant;

import java.util.Set;

/**
 * 上游数据源调度方式常量，二选一（design.md Decision 2）。取值直接对应
 * {@code tab_upstream_source.schedule_type} 列的存储值。
 */
public final class UpstreamScheduleType {

    /** 按间隔：每 N 分钟/小时同步一次。 */
    public static final String INTERVAL = "INTERVAL";

    /** 按每日固定时间点：每天 HH:mm 同步一次。 */
    public static final String FIXED_TIME = "FIXED_TIME";

    /** 全部取值，供请求参数校验使用。 */
    public static final Set<String> ALL_TYPES = Set.of(INTERVAL, FIXED_TIME);

    /**
     * 工具类不允许实例化。
     */
    private UpstreamScheduleType() {
    }
}
