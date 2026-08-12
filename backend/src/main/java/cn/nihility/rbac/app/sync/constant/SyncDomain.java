package cn.nihility.rbac.app.sync.constant;

import java.util.Set;

/**
 * 应用同步数据域常量：组织/用户/应用/角色/字典共 5 个（app-sync-field-mapping change
 * proposal.md）。取值直接对应 {@code tab_app_sync_domain_config.sync_domain}/
 * {@code tab_app_sync_field_mapping.sync_domain} 列的存储值，也是接口层 {@code domain}
 * 请求参数直接传递的字符串。
 */
public final class SyncDomain {

    /** 组织。 */
    public static final String ORG = "ORG";

    /** 用户。 */
    public static final String USER = "USER";

    /** 应用。 */
    public static final String APP = "APP";

    /** 角色。 */
    public static final String ROLE = "ROLE";

    /** 字典。 */
    public static final String DICT = "DICT";

    /** 全部 5 个数据域，供 {@code updateDomainConfig} 校验 {@code syncDomain} 参数合法性。 */
    public static final Set<String> ALL_DOMAINS = Set.of(ORG, USER, APP, ROLE, DICT);

    /**
     * 支持字段级同步映射配置的 4 个数据域（不含字典），供接口层校验字段映射相关请求的
     * {@code domain} 参数不能是 {@code DICT}（design.md Decision 8）。
     */
    public static final Set<String> FIELD_MAPPING_DOMAINS = Set.of(ORG, USER, APP, ROLE);

    /**
     * 工具类不允许实例化。
     */
    private SyncDomain() {
    }
}
