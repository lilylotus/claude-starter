package cn.nihility.rbac.formfield.constant;

/**
 * 表单字段定义体系覆盖的业务对象类型常量，供元数据字段配置模块
 * （{@code cn.nihility.rbac.metadata}）与表单字段定义模块共用。取值直接对应
 * {@code tab_metadata_field.biz_type}/{@code tab_form_field_definition.biz_type}
 * 列的存储值，也是前端路由/接口参数里直接传递的字符串，比数字码更可读、可调试。
 */
public final class FormFieldBizType {

    /** 组织。 */
    public static final String ORG = "ORG";

    /** 人员（用户）。 */
    public static final String USER = "USER";

    /** 任职。 */
    public static final String POSITION = "POSITION";

    /** 应用。 */
    public static final String APP = "APP";

    /**
     * 工具类不允许实例化。
     */
    private FormFieldBizType() {
    }
}
