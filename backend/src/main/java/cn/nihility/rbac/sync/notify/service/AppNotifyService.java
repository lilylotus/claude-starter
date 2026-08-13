package cn.nihility.rbac.sync.notify.service;

import cn.nihility.rbac.sync.changelog.entity.AppDataChangeLogEntity;

/**
 * 应用通知发送业务逻辑接口：给定一条已落库的变更记录，向所有匹配（{@code syncMode=NOTIFY}
 * 且该数据域 {@code syncEnabled=true}）的应用分别独立发起一次通知请求，并把结果写入
 * {@code tab_app_notify_record}（app-sync-notify-pull-api change design.md Decision 3/8）。
 */
public interface AppNotifyService {

    /**
     * 向所有匹配给定变更记录数据类型的通知目标应用逐个发起通知，单个应用通知失败不影响
     * 其余应用。
     *
     * @param changeLog 已落库的变更记录
     */
    void notifyMatchedApps(AppDataChangeLogEntity changeLog);
}
