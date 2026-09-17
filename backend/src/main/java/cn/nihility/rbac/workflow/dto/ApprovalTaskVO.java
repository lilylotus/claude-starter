package cn.nihility.rbac.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 审批任务视图对象，"我的待办/已办"查询结果。已办查询（{@code selectDonePage}）直接以本类
 * 作为 MyBatis {@code resultType}（design.md Decision 2），必须显式声明公开无参构造器
 * ——否则只有 {@code @Builder} 生成的全参构造器时，MyBatis 找不到无参构造器会退化为按参数
 * 顺序做"构造器自动映射"，与列名无关，容易把毫不相关的列塞进错位的参数导致类型转换异常。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
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

    /** 已办查询专用：触发这条历史记录的那次审批操作的动作类型（同意/拒绝/转办/委派/加签/
     *  退回等，{@link cn.nihility.rbac.workflow.constant.ApprovalAction} 字面量），"我的
     *  待办"查询结果不携带该字段，恒为空。 */
    private String action;

    /** 已办查询专用：触发这条历史记录的那次审批操作填写的处理意见，未填写时为空，"我的
     *  待办"查询结果不携带该字段，恒为空。 */
    private String remark;

    /** 已办查询专用：该记录关联申请的操作类型（CREATE/UPDATE/ENABLE/DISABLE/DELETE），"我的
     *  待办"查询结果不携带该字段，恒为空。 */
    private String operationType;
}
