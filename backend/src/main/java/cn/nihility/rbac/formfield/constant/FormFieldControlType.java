package cn.nihility.rbac.formfield.constant;

import java.util.Set;

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

    /** 日期，不依赖字典类型。 */
    public static final int DATE = 4;

    /** 多选字典下拉，与 {@link #DICT} 同样依赖 {@code dictTypeId}，区别仅在于前端渲染为多选控件。 */
    public static final int MULTI_DICT = 5;

    /** 依赖字典类型（要求 {@code dictTypeId} 必填）的控件类型集合，包含 {@link #DICT}、{@link #MULTI_DICT}。 */
    public static final Set<Integer> DICT_TYPES = Set.of(DICT, MULTI_DICT);

    /**
     * 工具类不允许实例化。
     */
    private FormFieldControlType() {
    }
}
