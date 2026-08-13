package cn.nihility.rbac.sync.notify.service;

import cn.nihility.rbac.sync.changelog.entity.AppDataChangeLogEntity;

/**
 * 应用通知发送业务逻辑接口：给定一条已落库、归属明确（{@code appRefId} 已知）的变更记录，
 * 若该应用当前同步方式为 {@code NOTIFY} 则发起一次通知请求，并把结果写入
 * {@code tab_app_notify_record}。变更记录落库时已经完成"这条记录该不该给这个应用看"的判定
 * （见 {@code AppDataChangeLogService#record}），本接口不再需要重新查询匹配应用列表
 * （app-sync-org-scope-and-app-change-log change design.md Decision 6，原
 * {@code notifyMatchedApps} 语义变更）。
 */
public interface AppNotifyService {

    /**
     * 若该记录归属的应用当前同步方式为 {@code NOTIFY}，则向其发起一次通知；否则不做任何事。
     *
     * @param changeLog 已落库的变更记录
     */
    void notifyIfConfigured(AppDataChangeLogEntity changeLog);
}
