package cn.nihility.rbac.identity.upstream.constant;

/**
 * 任职（POSITION）数据域同步专用的固定伪字段编码。任职的落库匹配需要"所属人员标识"
 * （按用户 code/mobile/idCard 任一匹配）与"所属组织编码"（按组织 code 匹配）两个值，
 * 但这两者都不是 POSITION bizType 下的元数据字段（元数据字段目录只描述
 * {@code tab_user_position} 自身的开放配置列，不包含外键 {@code userId}/{@code orgId}），
 * 因此无法通过"上游字段映射"配置的常规 metadataFieldId 目标来传递。
 *
 * <p>比照 Excel 批量导入 {@code cn.nihility.rbac.excelimport.constant.PositionPseudoFieldCode}
 * 的既有解法，为任职数据域约定两个固定的"上游字段编码"（即取数 SQL 的列别名 / 接口
 * JSON 的字段名，不经过字段映射表配置，直接从取数阶段拉取到的原始行中按约定键名读取）。
 * 这是实现阶段发现的 design.md 遗漏（design.md Decision 3 只描述了字段映射驱动的转换，
 * 未覆盖 POSITION 域必须绕开字段映射解析外键的情形），已同步在 design.md 补充说明。
 */
public final class UpstreamPositionPseudoFieldCode {

    /**
     * 所属人员标识伪字段编码，管理员配置 POSITION 数据域的取数 SQL/接口时，须将其中一列
     * 按该固定编码命名（SQL 用 {@code AS __userIdentifier}，接口 JSON 对象需包含该 key），
     * 取值按用户 {@code code}/{@code mobile}/{@code idCard} 三者任一精确匹配。
     */
    public static final String USER_IDENTIFIER = "__userIdentifier";

    /**
     * 所属组织编码伪字段编码，管理员配置 POSITION 数据域的取数 SQL/接口时，须将其中一列
     * 按该固定编码命名，取值按组织 {@code code} 精确匹配。
     */
    public static final String ORG_CODE = "__orgCode";

    /**
     * 工具类不允许实例化。
     */
    private UpstreamPositionPseudoFieldCode() {
    }
}
