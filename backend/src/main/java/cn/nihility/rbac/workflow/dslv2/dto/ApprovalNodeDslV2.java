package cn.nihility.rbac.workflow.dslv2.dto;

import cn.nihility.rbac.workflow.dslv2.constant.EmptyPolicy;
import cn.nihility.rbac.workflow.dslv2.constant.SelfPolicy;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/**
 * "审批"节点 v2，字段对应 design.md Decision 3 DSL v2 示例：审批人来源、会签投票配置、
 * 空人/自审策略、允许的任务动作、超时提醒、节点字段权限。
 */
@Getter
@Setter
public class ApprovalNodeDslV2 extends ProcessNodeDslV2 {

    /** 审批人来源配置。 */
    private AssigneeConfigDsl assignee;

    /** 会签投票配置；单人审批时 {@code mode} 可省略，按单人语义编译（不生成多实例）。 */
    private VoteConfigDsl vote;

    /** 空审批人处理策略，默认 {@link EmptyPolicy#BLOCK}。 */
    private EmptyPolicy emptyPolicy;

    /** 兜底角色编码，仅 {@code emptyPolicy=FALLBACK_ROLE} 时使用。 */
    private String fallbackRoleCode;

    /** 自审处理策略，默认 {@link SelfPolicy#EXCLUDE}。 */
    private SelfPolicy selfPolicy;

    /** 允许的任务动作开关。 */
    private ActionsConfigDsl actions;

    /** 超时提醒配置，为空表示不启用超时提醒。 */
    private TimeoutConfigDsl timeout;

    /** 节点字段权限：字段标识 → {@code HIDDEN}/{@code READ}/{@code WRITE_REQUIRED}/
     *  {@code WRITE_OPTIONAL}，后端与前端 UI 同口径，隐藏字段从响应中移除。 */
    private Map<String, String> fieldPermissions;
}
