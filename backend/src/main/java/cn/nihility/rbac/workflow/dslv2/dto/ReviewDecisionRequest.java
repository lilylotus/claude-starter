package cn.nihility.rbac.workflow.dslv2.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** 审核决策请求体。 */
@Getter
@Setter
public class ReviewDecisionRequest {

    /** 是否通过。 */
    @NotNull
    private Boolean approved;

    /** 审核意见。 */
    private String opinion;
}
