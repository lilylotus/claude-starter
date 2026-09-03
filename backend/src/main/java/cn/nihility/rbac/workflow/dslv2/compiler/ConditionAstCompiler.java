package cn.nihility.rbac.workflow.dslv2.compiler;

import cn.nihility.rbac.workflow.dslv2.constant.ConditionLogic;
import cn.nihility.rbac.workflow.dslv2.dto.ConditionAstDsl;
import cn.nihility.rbac.workflow.dslv2.dto.ConditionItemDsl;
import java.util.Collection;

/**
 * 条件 AST → UEL 表达式编译器：表达式只由本类固定拼装，不接受用户输入的自由表达式字符串，
 * 字段/比较符均已在 {@link ProcessModelDslV2Validator} 校验为白名单内取值（design.md
 * Decision 3）。{@code IN} 展开为 {@code ==} 的 {@code ||} 链，UEL 本身没有直接的集合成员
 * 运算符；{@code IS_NULL} 编译为 UEL {@code empty} 运算符（同时覆盖 null 与空字符串）。
 */
public final class ConditionAstCompiler {

    /** 工具类不允许实例化。 */
    private ConditionAstCompiler() {
    }

    /**
     * 编译条件 AST 为 UEL 表达式字符串（含 {@code ${...}} 包裹）。
     *
     * @param ast 条件 AST，非空
     * @return UEL 表达式
     */
    public static String compile(ConditionAstDsl ast) {
        return "${" + compileGroup(ast) + "}";
    }

    /**
     * 编译条件组（不含 {@code ${}} 包裹），供多层调用复用。
     */
    private static String compileGroup(ConditionAstDsl ast) {
        String joiner = ast.getLogic() == ConditionLogic.OR ? " || " : " && ";
        return ast.getItems().stream()
                .map(ConditionAstCompiler::compileItem)
                .reduce((a, b) -> a + joiner + b)
                .map(expr -> "(" + expr + ")")
                .orElse("true");
    }

    /**
     * 编译单条条件项。
     */
    private static String compileItem(ConditionItemDsl item) {
        String field = item.getField();
        return switch (item.getOp()) {
            case EQ -> "(" + field + " == " + formatValue(item.getValue()) + ")";
            case NE -> "(" + field + " != " + formatValue(item.getValue()) + ")";
            case GT -> "(" + field + " > " + formatValue(item.getValue()) + ")";
            case GE -> "(" + field + " >= " + formatValue(item.getValue()) + ")";
            case LT -> "(" + field + " < " + formatValue(item.getValue()) + ")";
            case LE -> "(" + field + " <= " + formatValue(item.getValue()) + ")";
            case IS_NULL -> "(empty " + field + ")";
            case IN -> compileIn(field, item.getValue());
        };
    }

    /**
     * {@code IN} 展开为 {@code ==} 的 {@code ||} 链。
     */
    private static String compileIn(String field, Object value) {
        if (!(value instanceof Collection<?> collection) || collection.isEmpty()) {
            return "false";
        }
        return collection.stream()
                .map(item -> "(" + field + " == " + formatValue(item) + ")")
                .reduce((a, b) -> a + " || " + b)
                .map(expr -> "(" + expr + ")")
                .orElse("false");
    }

    /**
     * 格式化比较值字面量：字符串加单引号（不隐式转数字），数字/布尔值原样输出。
     */
    private static String formatValue(Object value) {
        if (value instanceof Boolean || value instanceof Number) {
            return value.toString();
        }
        return "'" + String.valueOf(value).replace("'", "\\'") + "'";
    }
}
