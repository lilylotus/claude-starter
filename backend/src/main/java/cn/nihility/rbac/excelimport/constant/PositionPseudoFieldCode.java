package cn.nihility.rbac.excelimport.constant;

/**
 * 任职（POSITION）导入专用的固定伪字段标识常量。POSITION 的表单字段定义清单里
 * 没有、也不应该有 {@code userId}/{@code orgId}（它们是选择器，不是可开放配置的
 * 展示字段），但 Excel 导入必须能通过人可读的编码定位到具体的人员和组织，因此在
 * 数据库迁移里预置两条不可删除的固定导入配置行，{@code fieldCode} 取本类定义的
 * 保留标识，不与真实业务字段冲突（design.md Decision 2）。
 */
public final class PositionPseudoFieldCode {

    /**
     * 人员标识伪字段标识，导入时按其取值匹配 {@code tab_user.code}/{@code mobile}/
     * {@code idCard} 三者任一精确相等得到 {@code userId}（编号、手机号、身份证号
     * 中任意一种取值都可以，不要求预先声明具体是哪一种）。
     */
    public static final String USER_CODE = "__userCode";

    /** 组织编码伪字段标识，导入时按其取值匹配 {@code tab_org.code} 得到 {@code orgId}。 */
    public static final String ORG_CODE = "__orgCode";

    /**
     * 工具类不允许实例化。
     */
    private PositionPseudoFieldCode() {
    }
}
