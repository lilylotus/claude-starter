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

    /** 反对票处理策略：{@code VETO}（一票否决）/{@code THRESHOLD}（阈值制），仅会签节点
     *  （{@code approvalMode} 为 {@code AND}/{@code OR}/{@code PERCENT}）使用，DSL v2 专用，
     *  v1 编译器恒传 {@code null}——{@code FlowableWorkflowService} 据此区分一个会签任务是
     *  走 v1 遗留的"任一驳回即终止"判定还是 v2 的 N/A/R/U 计票判定
     *  （production-approval-lifecycle change design.md 第7节，tasks.md 6.3）。 */
    private String rejectPolicy;

    /** 空审批人处理策略：{@code TO_WORKFLOW_ADMIN}/{@code AUTO_SKIP}/{@code REJECT}/
     *  {@code BLOCK}/{@code FALLBACK_ROLE}（后两者 DSL v2 专用）。 */
    private String emptyAssigneeStrategy;

    /** 兜底角色编码，仅 {@code emptyAssigneeStrategy=FALLBACK_ROLE} 时使用（DSL v2 专用）。 */
    private String fallbackRoleCode;

    /** 节点字段权限快照（JSON：字段标识 -> {@code HIDDEN}/{@code READ}/{@code WRITE_REQUIRED}/
     *  {@code WRITE_OPTIONAL}），DSL v2 专用，v1 恒为空。 */
    private String fieldPermissionsJson;

    /** 组织负责人类来源解析组织的方式：{@code APPLICANT_SNAPSHOT}（默认，取申请人快照组织）/
     *  {@code FIXED_ORG}（取 {@link #targetOrgId} 指定的固定组织），仅 {@code assigneeType=
     *  ORG_LEADER} 时使用，其余类型恒为空（DSL v2 专用）。 */
    private String assigneeOrgSource;

    /** {@code assigneeOrgSource=FIXED_ORG} 时的固定目标组织 id，关联 {@code tab_org.id}。 */
    private Long targetOrgId;

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
