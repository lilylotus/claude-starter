package cn.nihility.rbac.formfield.constant;

/**
 * 表单字段定义的控件类型常量，对应 {@code tab_form_field_definition.control_type} 列。
 */
public final class FormFieldControlType {

    /** 文本输入框。 */
    public static final int TEXT = 1;

    /** 数字输入框。 */
    public static final int NUMBER = 2;

    /** 下拉单选字典。 */
    public static final int DICT = 3;

    /**
     * 工具类不允许实例化。
     */
    private FormFieldControlType() {
    }
}
