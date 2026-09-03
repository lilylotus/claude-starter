package cn.nihility.rbac.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * 审批任务视图对象，"我的待办/已办"查询结果。
 */
@Getter
@Setter
@Builder
@Schema(description = "审批任务")
public class ApprovalTaskVO {

    /** 审批任务 id（{@code tab_wf_approval_task.id}）。 */
    private Long id;

    /** 所属流程实例 id。 */
    private Long processInstanceId;

    /** 业务对象类型。 */
    private String businessType;

    /** 业务对象 id。 */
    private Long businessId;

    /** 流程标题。 */
    private String title;

    /** 节点 id。 */
    private String nodeId;

    /** 节点名称。 */
    private String nodeName;

    /** 指定处理人用户 id，候选组任务未认领时为空。 */
    private Long assigneeId;

    /** 指定处理人展示名称。 */
    private String assigneeName;

    /** 任务状态。 */
    private String status;

    /** 发起人用户 id。 */
    private Long applicantId;

    /** 发起人展示名称。 */
    private String applicantName;

    /** 任务创建时间。 */
    private LocalDateTime createTime;

    /** 任务完成时间，未完成为空。 */
    private LocalDateTime finishedTime;
}
