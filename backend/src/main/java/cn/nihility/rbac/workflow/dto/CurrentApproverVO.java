package cn.nihility.rbac.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * 当前节点（{@code status=CURRENT}）的候选审批人/已认领处理人视图对象
 * （add-approval-remark-and-process-flowchart change design.md Decision 7）。已认领/单人
 * 节点直接指定处理人时 {@code assigned=true}，候选组/会签节点尚未认领时按候选人明细逐条展示
 * {@code assigned=false}；不透出候选人解析依据说明（{@code resolveBasis}），也不展开角色
 * 候选人背后的具体人员列表。
 */
@Getter
@Setter
@Builder
@Schema(description = "当前节点候选审批人/处理人")
public class CurrentApproverVO {

    /** 用户 id，已认领处理人或 {@code USER} 类型候选人时非空。 */
    private Long userId;

    /** 用户展示名，已认领处理人或 {@code USER} 类型候选人时非空。 */
    private String userName;

    /** 角色编码，{@code ROLE} 类型候选人时非空。 */
    private String roleCode;

    /** 角色名称，{@code ROLE} 类型候选人时非空。 */
    private String roleName;

    /** {@code true} 表示已认领的指定处理人，{@code false} 表示尚未认领的候选人。 */
    private boolean assigned;
}
