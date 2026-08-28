package cn.nihility.rbac.sync.notify.service;

import cn.nihility.rbac.sync.notify.dto.NotifyAttemptOutcome;
import cn.nihility.rbac.sync.notify.entity.AppNotifyRecordEntity;

/**
 * 应用通知实际发送业务逻辑接口：给定一条已落库、已被状态机抢占为 {@code PROCESSING} 的
 * 通知任务，发起一次真正的 HTTP 请求，只负责"这一次请求打没打通、结果如何"，不修改任务
 * 状态、不做候选应用判定、不构造请求体（这些分别是 {@code NotifyCandidateResolver}/
 * {@code AppNotifyTaskService} 的职责），由调用方（{@code NotifySendCoordinator}）依据
 * 返回结果驱动状态机流转（app-sync-changelog-pull change design.md Decision 6）。
 */
public interface AppNotifyService {

    /**
     * 使用任务快照的回调地址与请求体，结合目标应用当前签名配置计算签名头，发起一次实际 HTTP
     * 请求。
     *
     * @param task 已落库的通知任务，须携带 {@code appRefId}/{@code notifyUrl}/
     *             {@code requestBody}
     * @return 本次请求结果
     */
    NotifyAttemptOutcome sendOnce(AppNotifyRecordEntity task);
}
