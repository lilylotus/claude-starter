package cn.nihility.rbac.excelimport.constant;

/**
 * 应用（APP）导入专用的固定伪字段标识常量。APP 的表单字段定义清单里没有、也不应该
 * 有 {@code ownerId}/{@code orgId}（它们是选择器，不是可开放配置的展示字段），但
 * {@code AppCreateRequest}/{@code AppUpdateRequest} 上二者均为必填，Excel 导入
 * 必须能通过人可读的编码定位到具体的负责人和组织，因此在数据库迁移里预置两条
 * 不可删除的固定导入配置行，{@code fieldCode} 取本类定义的保留标识，不与真实业务
 * 字段冲突（design.md Decision 2，比照 {@link PositionPseudoFieldCode} 的做法）。
 */
public final class AppPseudoFieldCode {

    /** 负责人编号伪字段标识，导入时按其取值匹配 {@code tab_user.code} 得到 {@code ownerId}。 */
    public static final String OWNER_CODE = "__ownerCode";

    /** 组织编码伪字段标识，导入时按其取值匹配 {@code tab_org.code} 得到 {@code orgId}。 */
    public static final String ORG_CODE = "__orgCode";

    /**
     * 工具类不允许实例化。
     */
    private AppPseudoFieldCode() {
    }
}
