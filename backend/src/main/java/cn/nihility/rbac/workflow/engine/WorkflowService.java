package cn.nihility.rbac.workflow.engine;

import cn.nihility.rbac.workflow.dto.AddSignCommand;
import cn.nihility.rbac.workflow.dto.ApproveCommand;
import cn.nihility.rbac.workflow.dto.ApprovalTaskVO;
import cn.nihility.rbac.workflow.dto.DelegateCommand;
import cn.nihility.rbac.workflow.dto.DisagreeCommand;
import cn.nihility.rbac.workflow.dto.ProcessInstanceDetailVO;
import cn.nihility.rbac.workflow.dto.RejectCommand;
import cn.nihility.rbac.workflow.dto.ReturnTaskCommand;
import cn.nihility.rbac.workflow.dto.StartProcessCommand;
import cn.nihility.rbac.workflow.dto.TaskQuery;
import cn.nihility.rbac.workflow.dto.TransferCommand;
import cn.nihility.rbac.workflow.dto.WithdrawCommand;
import cn.nihility.rbac.workflow.dto.WorkflowInstanceResult;
import java.util.List;

/**
 * 通用多级审批引擎抽象接口，业务代码接入审批流程的唯一入口。业务层 Controller 与 Service
 * SHALL NOT 直接注入 Flowable 的 {@code RuntimeService}/{@code TaskService}/
 * {@code RepositoryService}/{@code HistoryService}，底层 Flowable 引擎调用全部封装在本接口
 * 的实现（{@code FlowableWorkflowService}）内部（workflow-approval-engine change design.md
 * Decision 1/2）。
 */
public interface WorkflowService {

    /**
     * 启动一个流程实例。
     *
     * @param command 启动流程命令
     * @return 启动结果
     */
    WorkflowInstanceResult start(StartProcessCommand command);

    /**
     * 审批通过当前任务。
     *
     * @param command 审批通过命令
     */
    void approve(ApproveCommand command);

    /**
     * 驳回当前任务，直接终止流程实例，进入拒绝结束事件。
     *
     * @param command 驳回命令
     */
    void reject(RejectCommand command);

    /**
     * 反对（阈值制会签节点专用的反对票）：只计入反对票数，不立即终止流程实例，只有当反对票
     * 数使得该会签节点已不可能达到通过阈值时才会触发流程终止（production-approval-lifecycle
     * change design.md 第7节，tasks.md 6.3）。用在非阈值制会签节点上会被拒绝。
     *
     * @param command 反对命令
     */
    void disagree(DisagreeCommand command);

    /**
     * 将流程状态退回到指定的历史节点。
     *
     * @param command 退回命令
     */
    void returnTask(ReturnTaskCommand command);

    /**
     * 撤回流程实例。
     *
     * @param command 撤回命令
     */
    void withdraw(WithdrawCommand command);

    /**
     * 转办当前任务给指定处理人。
     *
     * @param command 转办命令
     */
    void transfer(TransferCommand command);

    /**
     * 委派当前任务给指定受托人处理。
     *
     * @param command 委派命令
     */
    void delegate(DelegateCommand command);

    /**
     * 为会签节点动态加签。
     *
     * @param command 加签命令
     */
    void addSign(AddSignCommand command);

    /**
     * 查询指定用户的"我的待办"。
     *
     * @param userId 用户 id
     * @param query  查询条件
     * @return 待办任务列表
     */
    List<ApprovalTaskVO> findTodoTasks(Long userId, TaskQuery query);

    /**
     * 查询指定用户的"我的已办"。
     *
     * @param userId 用户 id
     * @param query  查询条件
     * @return 已办任务列表
     */
    List<ApprovalTaskVO> findDoneTasks(Long userId, TaskQuery query);

    /**
     * 查询流程实例详情，含完整审批轨迹。
     *
     * @param processInstanceId 流程实例 id（{@code tab_wf_process_instance.id}）
     * @return 流程实例详情
     */
    ProcessInstanceDetailVO getProcessDetail(Long processInstanceId);
}
