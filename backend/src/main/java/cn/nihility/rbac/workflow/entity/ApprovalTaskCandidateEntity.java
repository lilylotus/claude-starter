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
 * 审批任务候选人明细持久化实体，对应表 {@code tab_wf_approval_task_candidate}。会签/候选组
 * 节点每个候选人一行，供"待我审批"按角色/按人聚合查询，避免解析 Flowable
 * {@code IdentityLink}。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tab_wf_approval_task_candidate")
public class ApprovalTaskCandidateEntity {

    /** 主键 id。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属审批任务 id，关联 {@code tab_wf_approval_task.id}。 */
    private Long taskId;

    /** 候选人类型：{@code USER}/{@code ROLE}。 */
    private String candidateType;

    /** 候选人取值：{@code USER} 类型为用户 id 文本，{@code ROLE} 类型为角色编码。 */
    private String candidateValue;

    /** 候选人解析依据说明（如"角色 SECURITY_ADMIN 命中管理员 3 人"），供审计与运维排查
     *  （production-approval-lifecycle change tasks.md 5.4）。 */
    private String resolveBasis;

    /** 创建人。 */
    private String createBy;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 更新人。 */
    private String updateBy;

    /** 更新时间。 */
    private LocalDateTime updateTime;
}
