package cn.nihility.rbac.workflow.service;

/**
 * 业务活动申请锁服务：保证同一业务目标（{@code bizType + targetKey}）同一时间只有一条运行中
 * 的审批申请占用，发起申请时获取，申请到达终态（通过/拒绝/撤回/系统终止）时释放
 * （production-approval-lifecycle change design.md 第8节，tasks.md 6.2）。固定加锁顺序中
 * 位于最外层："业务活动锁 → 实例行 → 任务行 → 节点轮次"，须在流程实例创建之前获取。
 */
public interface BusinessLockService {

    /**
     * 在当前事务内为给定业务目标获取活动锁：锁行不存在则新建并占用；锁行存在且当前空闲
     * （{@code activeRequestId} 为空）则复用占用；锁行存在且已被其他申请占用则拒绝。调用方
     * 须已处于数据库事务上下文中，加锁与后续流程实例创建须在同一事务内一起提交或一起回滚。
     *
     * @param bizType    业务对象类型：ORG/USER/POSITION/APP
     * @param targetKey  业务目标标识（如目标记录 id 文本，CREATE 场景可用申请自身临时键，
     *                   天然不会与其他申请冲突）
     * @param requestId  本次申请 {@code tab_approval_request.id}
     * @param operatorId 发起人用户 id，用于审计
     * @throws cn.nihility.rbac.common.exception.BusinessException 该目标已存在运行中的审批
     */
    void acquire(String bizType, String targetKey, Long requestId, Long operatorId);

    /**
     * 释放指定业务目标的活动锁：仅当当前占用者恰为 {@code requestId} 时才清空
     * {@code activeRequestId}，避免误释放其他申请持有的锁；锁行不存在或已被其他申请占用时
     * 静默跳过（幂等，允许重复调用）。
     *
     * @param bizType    业务对象类型
     * @param targetKey  业务目标标识
     * @param requestId  本次申请 {@code tab_approval_request.id}
     * @param operatorId 操作人用户 id，用于审计
     */
    void release(String bizType, String targetKey, Long requestId, Long operatorId);
}
