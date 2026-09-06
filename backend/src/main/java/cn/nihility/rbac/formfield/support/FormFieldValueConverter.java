package cn.nihility.rbac.formfield.support;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 按表单字段控件类型转换原始提交值的通用工具：数字框（{@code controlType=2}）转
 * {@link BigDecimal}（避免浮点比较误差），日期（{@code controlType=4}）转
 * {@link LocalDate#toEpochDay()}（{@code long}，UEL/JUEL 没有日期字面量语法，统一转成可比较
 * 的整数消解跨类型比较问题）。流程条件表达式编译（{@code WorkflowModelCompilerImpl}）与审批
 * 提交发起流程实例时构建 Flowable 流程变量（{@code ApprovalProcessServiceImpl}）共用同一套
 * 转换口径，避免两处实现逐渐漂移（workflow-condition-payload-fields change design.md
 * Decision 3）。
 */
public final class FormFieldValueConverter {

    /** 日期字段提交值的统一格式：ISO 日期字符串 {@code yyyy-MM-dd}。 */
    private static final DateTimeFormatter ISO_DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    /**
     * 工具类不允许实例化。
     */
    private FormFieldValueConverter() {
    }

    /**
     * 把数字框字段的原始值转换为 {@link BigDecimal}。
     *
     * @param value 原始值（数字或数字文本）
     * @return 转换后的 {@link BigDecimal}
     */
    public static BigDecimal toBigDecimal(Object value) {
        return new BigDecimal(String.valueOf(value));
    }

    /**
     * 把日期字段的原始值（ISO 日期字符串 {@code yyyy-MM-dd}）转换为 epoch day。
     *
     * @param value 原始值
     * @return 自 1970-01-01 起的天数
     */
    public static long toEpochDay(Object value) {
        return LocalDate.parse(String.valueOf(value), ISO_DATE_FORMATTER).toEpochDay();
    }
}
