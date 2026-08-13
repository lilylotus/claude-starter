package cn.nihility.rbac.identity.upstream.constant;

import java.util.List;
import java.util.Set;

/**
 * 上游数据同步涉及的数据域常量：只有组织/用户/任职三值，独立于
 * {@code cn.nihility.rbac.app.sync.constant.SyncDomain}（design.md Decision 6）——
 * 后者是六值（含 APP/ROLE/DICT），复用会带来"另外四个取值在本能力里永远非法"的隐晦
 * 约束，因此新建一个只含三值的常量类，取值字符串与 {@code SyncDomain} 对应常量保持一致
 * 便于理解，但类型独立。
 */
public final class UpstreamDataType {

    /** 组织。 */
    public static final String ORG = "ORG";

    /** 用户。 */
    public static final String USER = "USER";

    /** 任职。 */
    public static final String POSITION = "POSITION";

    /** 全部取值，供请求参数校验使用。 */
    public static final Set<String> ALL_TYPES = Set.of(ORG, USER, POSITION);

    /**
     * 一次同步执行时处理数据域的固定顺序：组织→用户→任职（proposal.md/design.md
     * Decision 3），任职依赖组织与用户已存在，必须最后处理。
     */
    public static final List<String> SYNC_ORDER = List.of(ORG, USER, POSITION);

    /**
     * 工具类不允许实例化。
     */
    private UpstreamDataType() {
    }
}
