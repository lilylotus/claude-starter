package cn.nihility.rbac.sync.notify.service;

import cn.nihility.rbac.common.result.PageResult;
import cn.nihility.rbac.sync.notify.dto.AppNotifyRecordQueryRequest;
import cn.nihility.rbac.sync.notify.dto.AppNotifyRecordVO;

/**
 * 通知日志查询业务逻辑接口：按应用 id 分页查询 {@code tab_app_notify_record}，支持按
 * 时间范围、通知状态过滤（add-app-sync-notify-pull-logs change design.md Decision 3），
 * 只读，不提供写接口——写入只通过 {@code AppNotifyServiceImpl} 在通知发起后完成。
 */
public interface AppNotifyRecordService {

    /**
     * 按应用 id 分页查询通知日志。
     *
     * @param request 分页查询参数，{@code appRefId} 必填
     * @return 通知日志分页结果
     */
    PageResult<AppNotifyRecordVO> page(AppNotifyRecordQueryRequest request);
}
