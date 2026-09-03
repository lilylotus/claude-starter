package cn.nihility.rbac.workflow.dslv2.compiler;

import cn.nihility.rbac.workflow.dslv2.constant.ConditionLogic;
import cn.nihility.rbac.workflow.dslv2.dto.ConditionAstDsl;
import cn.nihility.rbac.workflow.dslv2.dto.ConditionItemDsl;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;

/**
 * 条件 AST 解释执行器：不依赖部署/引擎，直接对模拟表单值求值，供"快速预演"静态解释流程路径
 * 使用（design.md Decision 4"快速预演给路径和人员解析解释"）。求值语义须与
 * {@link ConditionAstCompiler} 编译出的 UEL 表达式在真实引擎里的行为保持一致：字符串不隐式
 * 转数字，数值比较统一按 {@link BigDecimal} 精确计算避免浮点误差。
 */
public final class ConditionAstEvaluator {

    /** 工具类不允许实例化。 */
    private ConditionAstEvaluator() {
    }

    /**
     * 对给定字段值上下文求值条件 AST。
     *
     * @param ast     条件 AST，非空
     * @param context 字段值上下文（模拟表单值）
     * @return 是否命中
     */
    public static boolean evaluate(ConditionAstDsl ast, Map<String, Object> context) {
        boolean and = ast.getLogic() != ConditionLogic.OR;
        for (ConditionItemDsl item : ast.getItems()) {
            boolean hit = evaluateItem(item, context.get(item.getField()));
            if (and && !hit) {
                return false;
            }
            if (!and && hit) {
                return true;
            }
        }
        return and;
    }

    /**
     * 对单条条件项求值。
     */
    private static boolean evaluateItem(ConditionItemDsl item, Object actual) {
        return switch (item.getOp()) {
            case IS_NULL -> actual == null || "".equals(actual);
            case EQ -> Objects.equals(normalize(actual), normalize(item.getValue()));
            case NE -> !Objects.equals(normalize(actual), normalize(item.getValue()));
            case GT -> compare(actual, item.getValue()) > 0;
            case GE -> compare(actual, item.getValue()) >= 0;
            case LT -> compare(actual, item.getValue()) < 0;
            case LE -> compare(actual, item.getValue()) <= 0;
            case IN -> item.getValue() instanceof Collection<?> collection
                    && collection.stream().anyMatch(candidate -> Objects.equals(normalize(actual), normalize(candidate)));
        };
    }

    /**
     * 归一化用于相等性比较的值：数字统一转 {@link BigDecimal} 字符串形式，避免
     * {@code Integer(1)} 与 {@code Double(1.0)} 因类型不同判定不相等；其余类型按
     * {@code String} 比较。
     */
    private static Object normalize(Object value) {
        if (value instanceof Number number) {
            return new BigDecimal(number.toString()).stripTrailingZeros().toPlainString();
        }
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 数值/日期字段比较，统一转 {@link BigDecimal} 精确计算；两侧均无法解析为数字时视为不可比较，
     * 返回 0（既不大于也不小于），避免因脏数据抛异常中断预演。
     */
    private static int compare(Object actual, Object expected) {
        BigDecimal left = toDecimal(actual);
        BigDecimal right = toDecimal(expected);
        if (left == null || right == null) {
            return 0;
        }
        return left.compareTo(right);
    }

    /**
     * 尝试把值解析为 {@link BigDecimal}，无法解析返回 {@code null}。
     */
    private static BigDecimal toDecimal(Object value) {
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        if (value instanceof String text) {
            try {
                return new BigDecimal(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
