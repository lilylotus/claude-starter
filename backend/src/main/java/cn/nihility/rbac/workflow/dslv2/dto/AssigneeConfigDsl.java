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

    /** 组织负责人类来源解析组织的方式：{@code APPLICANT_SNAPSHOT}（默认，申请人提交时快照
     *  组织）/{@code FIXED_ORG}（固定目标组织，取 {@link #orgId}，供"指定固定组织管理员
     *  审批"场景使用，仅 {@code type=ORG_LEADER} 时生效），未配置时按
     *  {@code APPLICANT_SNAPSHOT} 处理，保持既有行为不变。 */
    private String orgSource;

    /** {@code orgSource=FIXED_ORG} 时的固定目标组织 id，须为 {@code tab_org} 中真实存在且
     *  启用的组织，发布校验拒绝未配置/指向不存在或未启用组织的情形。 */
    private Long orgId;

    /** {@code PREVIOUS_APPROVER} 且并行汇合后存在多个来源时，必须显式指定来源节点 id，
     *  不允许系统猜一个。 */
    private String sourceNodeId;
}
