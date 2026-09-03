package cn.nihility.rbac.workflow.dslv2.dto;

import cn.nihility.rbac.workflow.dslv2.constant.ConditionLogic;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 条件表达式 AST 根节点：{@code {logic:"AND",items:[{field,op,value}]}}（design.md Decision
 * 3）。表达式只由编译器固定生成，不接受 {@code ${...}} 字符串或用户拼接的 Bean 调用；条件
 * 编译为内部规则 ID，调用受限 evaluator，不把用户值直接拼进 UEL 自由文本。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConditionAstDsl {

    /** 组内逻辑连接符。 */
    private ConditionLogic logic;

    /** 条件项列表，非空。 */
    private List<ConditionItemDsl> items;
}
