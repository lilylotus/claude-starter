package cn.nihility.rbac.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 节点审批人规则持久化实体，对应表 {@code tab_wf_node_assignee_rule}。是"可配置多级审批"
 * 的核心表：BPMN 只声明节点顺序与网关，节点的审批人规则、会签模式、允许的操作均在本表配置，
 * 避免为每个新业务类型重新写 Java 分支。关联不可变的 {@link ProcessDefinitionEntity} 主键，
 * 同一流程编码发布新版本时会插入新的一批规则行，旧版本规则行原样保留不动。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tab_wf_node_assignee_rule")
public class NodeAssigneeRuleEntity {

    /** 主键 id。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属流程定义版本 id，关联 {@code tab_wf_process_definition.id}。 */
    private Long processDefinitionId;

    /** BPMN 用户任务节点 id（{@code userTask} 的 {@code id}）。 */
    private String nodeId;

    /** 节点名称，供"当前在哪一级"展示使用。 */
    private String nodeName;

    /** 节点顺序，用于展示"第几级审批"。 */
    private Integer nodeOrder;

    /** 审批人来源类型，{@link cn.nihility.rbac.workflow.constant.AssigneeType} 字面量。 */
    private String assigneeType;

    /** 审批人来源取值，按 {@code assigneeType} 解释（角色编码/用户 id 等）。 */
    private String assigneeValue;

    /** 审批模式：{@code SINGLE}/{@code AND}/{@code OR}/{@code PERCENT}。 */
    private String approvalMode;

    /** 会签通过比例（百分比整数），仅 {@code approvalMode=PERCENT} 时使用。 */
    private Integer approvalPercent;

    /** 空审批人处理策略：{@code TO_WORKFLOW_ADMIN}/{@code AUTO_SKIP}/{@code REJECT}/
     *  {@code BLOCK}/{@code FALLBACK_ROLE}（后两者 DSL v2 专用）。 */
    private String emptyAssigneeStrategy;

    /** 兜底角色编码，仅 {@code emptyAssigneeStrategy=FALLBACK_ROLE} 时使用（DSL v2 专用）。 */
    private String fallbackRoleCode;

    /** 是否允许审批人为发起人本人时仍保留候选人（自审）。 */
    private Boolean allowSelfApproval;

    /** 是否允许转办。 */
    private Boolean allowTransfer;

    /** 是否允许委派。 */
    private Boolean allowDelegate;

    /** 是否允许加签。 */
    private Boolean allowAddSign;

    /** 是否允许退回到该节点。 */
    private Boolean allowReturn;

    /** 创建人。 */
    private String createBy;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 更新人。 */
    private String updateBy;

    /** 更新时间。 */
    private LocalDateTime updateTime;
}
