package cn.nihility.rbac.approval.service;

import cn.nihility.rbac.approval.dto.ApprovalProcessInstance;

/**
 * Flowable 审批流程操作封装，隔离审批业务与流程引擎 API。
 */
public interface ApprovalProcessService {

    /**
     * 启动主数据审批流程，并查询流程创建的当前用户任务。
     *
     * @param requestId 审批申请 id
     * @return 流程实例与用户任务标识
     */
    ApprovalProcessInstance start(Long requestId);

    /**
     * 完成审批用户任务。
     *
     * @param taskId     Flowable 用户任务 id
     * @param approverId 审批人用户 id
     * @param approved   是否通过
     */
    void complete(String taskId, Long approverId, boolean approved);

    /**
     * 终止尚未结束的审批流程实例。
     *
     * @param processInstanceId Flowable 流程实例 id
     */
    void terminate(String processInstanceId);
}
