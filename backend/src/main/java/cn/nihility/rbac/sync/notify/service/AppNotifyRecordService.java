package cn.nihility.rbac.sync.notify.service;

import cn.nihility.rbac.common.result.PageResult;
import cn.nihility.rbac.sync.notify.dto.AppNotifyRecordQueryRequest;
import cn.nihility.rbac.sync.notify.dto.AppNotifyRecordVO;

/**
 * 通知日志查询业务逻辑接口：按应用 id 分页查询 {@code tab_app_notify_record}，支持按
 * 时间范围、通知状态过滤（add-app-sync-notify-pull-logs change design.md Decision 3）；
 * {@link #retryDeadTask} 是唯一的写操作，供管理端对一条 {@code DEAD} 记录发起手动重推
 * （app-sync-changelog-pull change design.md Decision 6，tasks.md 6.3）。
 */
public interface AppNotifyRecordService {

    /**
     * 按应用 id 分页查询通知日志。
     *
     * @param request 分页查询参数，{@code appRefId} 必填
     * @return 通知日志分页结果
     */
    PageResult<AppNotifyRecordVO> page(AppNotifyRecordQueryRequest request);

    /**
     * 管理端手动重推：把一条属于指定应用、当前处于 {@code DEAD} 状态的通知任务原子重置为
     * {@code PENDING}（清空租约/下次重试时间/已失败次数），随后触发一次即时发送优化；
     * 记录不属于该应用、不存在、或当前不是 {@code DEAD} 状态时抛出 {@link
     * cn.nihility.rbac.common.exception.BusinessException}。
     *
     * @param appRefId 应用 id（{@code tab_app.id}），用于校验记录归属，防止跨应用误操作
     * @param recordId 通知记录主键 id
     */
    void retryDeadTask(Long appRefId, Long recordId);
}
