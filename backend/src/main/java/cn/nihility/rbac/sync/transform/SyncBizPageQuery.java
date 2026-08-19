package cn.nihility.rbac.sync.transform;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import lombok.Builder;
import lombok.Getter;

/**
 * 分页拉取业务表当前数据的查询参数（app-sync-drop-changelog change design.md
 * Decision 2/3/4/5），由 {@link SyncBizPageQueryResolver} 按 {@code dataType} 分发到组织/
 * 用户/任职/应用/角色各自的分页查询，不适用于某个数据域的字段由调用方置空，查询侧直接忽略。
 */
@Getter
@Builder
public class SyncBizPageQuery {

    /** 页码，从 1 开始。 */
    private final int page;

    /** 每页大小。 */
    private final int pageSize;

    /** 更新时间范围起点（含），可为空。 */
    private final LocalDateTime updateTimeFrom;

    /** 更新时间范围终点（含），可为空。 */
    private final LocalDateTime updateTimeTo;

    /** 主键 id 列表精确过滤，{@code null} 表示不过滤。 */
    private final List<Long> ids;

    /**
     * 业务编码列表精确过滤，{@code null} 表示不过滤；POSITION 数据域没有业务编码字段，
     * 调用方不应传入该字段（查询侧也不使用）。
     */
    private final List<String> codes;

    /** 用户手机号精确过滤，仅 USER 数据域使用，其余数据域查询侧不使用该字段。 */
    private final String mobile;

    /**
     * 组织范围过滤下推所需的允许组织 id 全集，仅 ORG/USER/POSITION 三个数据域使用：
     * {@code null} 表示不限制（对应 {@code AppSyncOrgScopeResolver.resolveAllowedOrgIds}
     * 的空 {@code Optional}），非空 {@link Set}（可能为空集合）表示受限，只返回落在集合内的
     * 记录。
     */
    private final Set<Long> allowedOrgIds;
}
