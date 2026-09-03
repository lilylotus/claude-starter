package cn.nihility.rbac.workflow.dslv2.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 流程级策略配置（design.md Decision 3 DSL v2 示例 {@code policies} 字段）。 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicyConfigDsl {

    /** 撤回策略，首轮固定 {@code BEFORE_FIRST_DECISION}：仅在没有任何一级
     *  APPROVE/DISAGREE/REJECT 决策前允许撤回。 */
    private String withdraw;
}
