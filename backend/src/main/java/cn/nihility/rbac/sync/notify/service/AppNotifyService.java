package cn.nihility.rbac.sync.notify.service;

import cn.nihility.rbac.sync.event.DomainChangeEvent;

/**
 * 应用通知发送业务逻辑接口：给定一条领域变更事件与一个已判定匹配的目标应用 id，若该应用
 * 当前同步方式为 {@code NOTIFY} 则发起一次通知请求，并把结果写入
 * {@code tab_app_notify_record}。候选应用判定（数据域启用+总开关开启+组织范围匹配+同步
 * 方式为通知）由调用方（{@code NotifyCandidateResolver}）完成，本接口不再需要重新判定
 * （app-sync-drop-changelog change design.md Decision 6：不再依赖已落库的变更记录实体，
 * 直接从事件本身取值）。
 */
public interface AppNotifyService {

    /**
     * 若目标应用当前同步方式为 {@code NOTIFY}，则向其发起一次通知；否则不做任何事。
     *
     * @param event    领域变更事件
     * @param appRefId 目标应用 id（{@code tab_app.id}）
     */
    void notifyIfConfigured(DomainChangeEvent event, Long appRefId);
}
