package cn.nihility.rbac.excelimport.constant;

/**
 * 组织（ORG）导入专用的固定伪字段标识常量。ORG 的表单字段定义清单里没有、也不应该
 * 有 {@code parentId}（组织管理页面里用的是树形选择器，不是可开放配置的展示字段），
 * 但 {@code OrgCreateRequest}/{@code OrgUpdateRequest} 上 {@code parentId} 为
 * {@code @NotNull}（默认 0 表示顶级），Excel 导入必须能通过人可读的编码定位到具体的
 * 上级组织，因此在数据库迁移里预置一条不可删除的固定导入配置行，{@code fieldCode}
 * 取本类定义的保留标识，不与真实业务字段冲突（design.md Decision 2，比照
 * {@link AppPseudoFieldCode}/{@link PositionPseudoFieldCode} 的做法）。
 */
public final class OrgPseudoFieldCode {

    /** 上级组织编码伪字段标识，导入时按其取值匹配 {@code tab_org.code} 得到 {@code parentId}。 */
    public static final String PARENT_CODE = "__parentCode";

    /**
     * 工具类不允许实例化。
     */
    private OrgPseudoFieldCode() {
    }
}
