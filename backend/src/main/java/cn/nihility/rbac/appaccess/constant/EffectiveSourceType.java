package cn.nihility.rbac.appaccess.constant;

/**
 * 最终生效权限查询结果的判定依据类型常量（供
 * {@code AppAccessEffectivePermissionService#listEffectiveByUser}/
 * {@code #listEffectiveByApp} 标注每条结果的来源，见 spec.md"最终生效权限查询"需求）。
 */
public final class EffectiveSourceType {

    /** 判定依据为人工追加授权（{@code GRANT} 例外）。 */
    public static final String MANUAL_GRANT = "MANUAL_GRANT";

    /** 判定依据为启用中策略产生的策略授权记录。 */
    public static final String POLICY = "POLICY";

    /** 曾经可能可访问（命中策略或存在人工追加授权），但被人工收回（{@code DENY} 例外）覆盖，
     *  仅在 {@code includeRevoked=true} 时出现，供审计查看。 */
    public static final String REVOKED = "REVOKED";

    /**
     * 工具类不允许实例化。
     */
    private EffectiveSourceType() {
    }
}
