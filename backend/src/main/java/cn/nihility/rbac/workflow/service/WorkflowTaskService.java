package cn.nihility.rbac.workflow.service;

import cn.nihility.rbac.common.result.PageResult;
import cn.nihility.rbac.workflow.dto.ApprovalTaskVO;
import cn.nihility.rbac.workflow.dto.ProcessInstanceDetailVO;
import cn.nihility.rbac.workflow.dto.TaskQuery;
import java.util.List;

/**
 * 待办/已办/流程实例详情查询服务，数据来源均为自有业务表（{@code tab_wf_approval_task}/
 * {@code tab_wf_approval_task_candidate}/{@code tab_wf_approval_record}），不直接查询
 * Flowable 的 {@code ACT_RU_TASK}/{@code ACT_HI_TASKINST}
 * （workflow-approval-engine change design.md Requirement "待办与已办查询不依赖 Flowable
 * 运行时表"）。
 */
public interface WorkflowTaskService {

    /**
     * 查询指定用户的"我的待办"：作为指定处理人，或作为候选人明细命中（用户/角色维度）、且
     * 尚未被他人认领的全部待处理任务。
     *
     * @param userId 用户 id
     * @param query  查询条件
     * @return 待办任务列表，按创建时间降序排列
     */
    List<ApprovalTaskVO> findTodoTasks(Long userId, TaskQuery query);

    /**
     * 查询指定用户的"我的已办"：该用户已处理完成的审批记录，每条记录携带触发该记录的那次
     * 审批操作的 {@code action}/{@code remark}（add-approval-history-menu change design.md
     * Decision 1/2）。
     *
     * @param userId 用户 id
     * @param query  查询条件
     * @return 已办任务分页结果，当前页数据按处理时间降序排列
     */
    PageResult<ApprovalTaskVO> findDoneTasks(Long userId, TaskQuery query);

    /**
     * 查询流程实例详情，含完整审批轨迹与完整节点/连线图。仅申请人本人、历史审批轨迹中出现过
     * 的操作人/转办来源人、当前任一开放任务的指定处理人或候选人三者之一可查看，其余用户会被
     * 拒绝（approval-runtime-safety 能力"操作授权和访问控制"需求，add-approval-remark
     * -and-process-flowchart change design.md Decision 3）。
     *
     * @param processInstanceId 流程实例 id（{@code tab_wf_process_instance.id}）
     * @param viewerId          当前查看者用户 id
     * @return 流程实例详情
     */
    ProcessInstanceDetailVO getProcessDetail(Long processInstanceId, Long viewerId);
}
