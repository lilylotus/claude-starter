package cn.nihility.rbac.workflow.dslv2.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

/** 审批节点允许的任务动作开关（design.md Decision 3/7）。 */
@Getter
@Setter
public class ActionsConfigDsl {

    /** 是否允许转办。 */
    private Boolean transfer;

    /** 是否允许委派。 */
    private Boolean delegate;

    /** 是否允许退回到该节点；Java {@code return} 为保留字，JSON 字段名固定为
     *  {@code return}，通过 {@link JsonProperty} 映射。 */
    @JsonProperty("return")
    private Boolean returnAllowed;

    /** 是否允许加签（仅对仍活跃的并行会签节点有意义）。 */
    private Boolean addSign;
}
