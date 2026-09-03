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
 * 审批任务持久化实体，对应表 {@code tab_wf_approval_task}。"我的待办"查询以本表
 * {@code assigneeId} 等于当前用户或候选人明细命中、{@code status=PENDING/CLAIMED} 为准，
 * 不直接查询 Flowable 的 {@code ACT_RU_TASK}。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tab_wf_approval_task")
public class ApprovalTaskEntity {

    /** 主键 id。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** Flowable 用户任务 id。 */
    private String flowableTaskId;

    /** 所属流程实例 id，关联 {@code tab_wf_process_instance.id}。 */
    private Long processInstanceId;

    /** 节点 id。 */
    private String nodeId;

    /** 节点名称。 */
    private String nodeName;

    /** 指定处理人用户 id，单人节点或候选组任务被认领后回填；候选组任务未认领时为空。 */
    private Long assigneeId;

    /** 候选人类型：{@code USER}/{@code ROLE}，会签/候选组场景为空则查
     *  {@code tab_wf_approval_task_candidate} 明细。 */
    private String candidateType;

    /** 状态：{@code PENDING}/{@code CLAIMED}/{@code COMPLETED}/{@code TRANSFERRED}/{@code RETURNED}。 */
    private String status;

    /** 完成时间，未完成为空。 */
    private LocalDateTime finishedTime;

    /** 创建人。 */
    private String createBy;

    /** 创建时间，即任务创建时间。 */
    private LocalDateTime createTime;

    /** 更新人。 */
    private String updateBy;

    /** 更新时间。 */
    private LocalDateTime updateTime;
}
