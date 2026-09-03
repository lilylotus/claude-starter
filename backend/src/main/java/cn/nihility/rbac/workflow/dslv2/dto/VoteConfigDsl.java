package cn.nihility.rbac.workflow.dslv2.dto;

import cn.nihility.rbac.workflow.dslv2.constant.RejectPolicy;
import cn.nihility.rbac.workflow.dslv2.constant.VoteExecution;
import cn.nihility.rbac.workflow.dslv2.constant.VoteMode;
import lombok.Getter;
import lombok.Setter;

/** 审批节点会签投票配置（design.md Decision 3/7）。 */
@Getter
@Setter
public class VoteConfigDsl {

    /** 投票规则：全部/任一/比例。 */
    private VoteMode mode;

    /** 执行方式：并行/串行。 */
    private VoteExecution execution;

    /** 通过比例（1~100 的整数），仅 {@code mode=PERCENT} 使用。 */
    private Integer percent;

    /** 反对票处理策略：一票否决/阈值制。 */
    private RejectPolicy rejectPolicy;
}
