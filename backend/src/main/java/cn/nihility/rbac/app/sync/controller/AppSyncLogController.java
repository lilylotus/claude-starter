package cn.nihility.rbac.app.sync.controller;

import cn.nihility.rbac.common.result.PageResult;
import cn.nihility.rbac.sync.notify.dto.AppNotifyRecordQueryRequest;
import cn.nihility.rbac.sync.notify.dto.AppNotifyRecordVO;
import cn.nihility.rbac.sync.notify.service.AppNotifyRecordService;
import cn.nihility.rbac.sync.pull.record.dto.AppPullRecordQueryRequest;
import cn.nihility.rbac.sync.pull.record.dto.AppPullRecordVO;
import cn.nihility.rbac.sync.pull.record.service.AppPullRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 应用同步日志查询接口，提供通知日志、拉取日志两个只读分页查询接口
 * （add-app-sync-notify-pull-logs change design.md Decision 3）。复用
 * {@code AppManagement:app:config} 页面级权限，接口层不新增权限校验注解（前端路由/按钮层面
 * 控制，与 {@code AppSyncConfigController} 现状一致）。
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "应用同步日志", description = "通知日志、拉取日志分页查询接口，只读，用于排查应用数据同步问题")
public class AppSyncLogController {

    /** 通知日志查询业务逻辑接口。 */
    private final AppNotifyRecordService appNotifyRecordService;

    /** 拉取日志查询业务逻辑接口。 */
    private final AppPullRecordService appPullRecordService;

    /**
     * 分页查询指定应用的通知日志，全部筛选参数可选，按通知发起时间降序排列。
     *
     * @param id           应用 id（{@code tab_app.id}）
     * @param notifyStatus 通知状态：1=成功，2=失败，精确匹配，可选
     * @param startTime    通知发起时间范围起点（含），可选
     * @param endTime      通知发起时间范围终点（含），可选
     * @param page         页码，默认第 1 页
     * @param pageSize     每页条数，默认 10 条
     * @return 通知日志的分页结果
     */
    @Operation(summary = "分页查询通知日志", description = "支持按通知状态、通知发起时间范围筛选，均可选，按时间降序排列")
    @GetMapping("/api/apps/{id}/config/sync/notify-records")
    public PageResult<AppNotifyRecordVO> pageNotifyRecords(@PathVariable Long id,
            @Parameter(description = "通知状态：1=成功，2=失败")
            @RequestParam(required = false) Integer notifyStatus,
            @Parameter(description = "通知发起时间范围起点（含），如 2026-07-01T00:00:00")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @Parameter(description = "通知发起时间范围终点（含），如 2026-07-31T23:59:59")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @Parameter(description = "页码，默认第 1 页")
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @Parameter(description = "每页条数，默认 10 条")
            @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        AppNotifyRecordQueryRequest request = new AppNotifyRecordQueryRequest();
        request.setAppRefId(id);
        request.setNotifyStatus(notifyStatus);
        request.setStartTime(startTime);
        request.setEndTime(endTime);
        request.setPage(page);
        request.setPageSize(pageSize);
        return appNotifyRecordService.page(request);
    }

    /**
     * 分页查询指定应用的拉取日志，全部筛选参数可选，按拉取发起时间降序排列。
     *
     * @param id        应用 id（{@code tab_app.id}）
     * @param startTime 拉取发起时间范围起点（含），可选
     * @param endTime   拉取发起时间范围终点（含），可选
     * @param page      页码，默认第 1 页
     * @param pageSize  每页条数，默认 10 条
     * @return 拉取日志的分页结果
     */
    @Operation(summary = "分页查询拉取日志", description = "支持按拉取发起时间范围筛选，可选，按时间降序排列")
    @GetMapping("/api/apps/{id}/config/sync/pull-records")
    public PageResult<AppPullRecordVO> pagePullRecords(@PathVariable Long id,
            @Parameter(description = "拉取发起时间范围起点（含），如 2026-07-01T00:00:00")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @Parameter(description = "拉取发起时间范围终点（含），如 2026-07-31T23:59:59")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @Parameter(description = "页码，默认第 1 页")
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @Parameter(description = "每页条数，默认 10 条")
            @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        AppPullRecordQueryRequest request = new AppPullRecordQueryRequest();
        request.setAppRefId(id);
        request.setStartTime(startTime);
        request.setEndTime(endTime);
        request.setPage(page);
        request.setPageSize(pageSize);
        return appPullRecordService.page(request);
    }
}
