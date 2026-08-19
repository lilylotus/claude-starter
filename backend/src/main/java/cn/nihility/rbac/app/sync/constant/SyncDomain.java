package cn.nihility.rbac.app.sync.constant;

import java.util.Set;

/**
 * 应用同步数据域常量：组织/用户/任职/应用/角色/字典共 6 个（app-sync-field-mapping change
 * proposal.md；任职域由 app-sync-notify-pull-api change design.md Decision 1 补入）。
 * 取值直接对应 {@code tab_app_sync_domain_config.sync_domain}/
 * {@code tab_app_sync_field_mapping.sync_domain} 列的存储值，也是接口层 {@code domain}
 * 请求参数直接传递的字符串。
 */
public final class SyncDomain {

    /** 组织。 */
    public static final String ORG = "ORG";

    /** 用户。 */
    public static final String USER = "USER";

    /** 任职。 */
    public static final String POSITION = "POSITION";

    /** 应用。 */
    public static final String APP = "APP";

    /** 角色。 */
    public static final String ROLE = "ROLE";

    /** 字典。 */
    public static final String DICT = "DICT";

    /** 全部 6 个数据域，供 {@code updateDomainConfig} 校验 {@code syncDomain} 参数合法性。 */
    public static final Set<String> ALL_DOMAINS = Set.of(ORG, USER, POSITION, APP, ROLE, DICT);

    /**
     * 支持字段级同步映射配置的 5 个数据域（不含字典），供接口层校验字段映射相关请求的
     * {@code domain} 参数不能是 {@code DICT}（design.md Decision 8；任职域补入见
     * app-sync-notify-pull-api change design.md Decision 1）。
     */
    public static final Set<String> FIELD_MAPPING_DOMAINS = Set.of(ORG, USER, POSITION, APP, ROLE);

    /**
     * 参与数据变更同步事件、支持分页拉取的 6 个数据域（含字典），供
     * {@code app-sync-notify-pull} 能力校验拉取接口请求的 {@code dataType} 参数合法性
     * （app-sync-notify-pull-api change design.md Decision 1/6/9；拉取改为直接分页查询业务表
     * 当前数据后由 {@code CHANGE_LOG_DOMAINS} 更名而来，见 app-sync-drop-changelog change
     * design.md Decision 3；字典拉取的是字典项而非字典类型，见 design.md Decision 7）。
     */
    public static final Set<String> SYNC_PULL_DOMAINS = Set.of(ORG, USER, POSITION, APP, ROLE, DICT);

    /**
     * 支持按应用配置同步范围（全部数据/指定组织范围）的 3 个数据域，供接口层校验组织范围
     * 相关请求的 {@code syncDomain} 参数只能是这三者之一（app-sync-org-scope-and-app-change-log
     * change design.md Decision 2）。应用/角色/字典三个数据域没有"归属组织"的概念，不提供
     * 该设置。
     */
    public static final Set<String> ORG_SCOPE_DOMAINS = Set.of(ORG, USER, POSITION);

    /**
     * 工具类不允许实例化。
     */
    private SyncDomain() {
    }
}
