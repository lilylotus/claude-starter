package cn.nihility.rbac.workflow.dslv2.dto;

import cn.nihility.rbac.workflow.dslv2.constant.AssigneeTypeV2;
import lombok.Getter;
import lombok.Setter;

/** 审批人来源配置（design.md Decision 3 DSL v2 示例 {@code assignee} 字段）。 */
@Getter
@Setter
public class AssigneeConfigDsl {

    /** 来源类型。 */
    private AssigneeTypeV2 type;

    /** 来源取值，按 {@code type} 解释。 */
    private String value;

    /** 组织负责人类来源解析组织的方式：{@code APPLICANT_SNAPSHOT}（申请人提交时快照组织）；
     *  当前仅此一种取值，预留字段避免后续扩展需要新增顶层字段。 */
    private String orgSource;

    /** {@code PREVIOUS_APPROVER} 且并行汇合后存在多个来源时，必须显式指定来源节点 id，
     *  不允许系统猜一个。 */
    private String sourceNodeId;
}
