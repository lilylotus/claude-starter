package cn.nihility.rbac.sync.event.support;

import cn.nihility.rbac.sync.changelog.entity.AppDataChangeLogEntity;
import cn.nihility.rbac.sync.changelog.service.AppDataChangeLogService;
import cn.nihility.rbac.sync.event.DomainChangeEvent;
import cn.nihility.rbac.sync.notify.entity.AppNotifyRecordEntity;
import cn.nihility.rbac.sync.notify.service.AppNotifyTaskService;
import cn.nihility.rbac.sync.notify.support.NotifyCandidateResolver;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 领域变更事件的"落库"半段：在一个本地数据库事务内完成"写入一条全局变更流水 + 判定候选
 * 应用 + 为全部候选应用创建/复用 PENDING 通知任务"（app-sync-changelog-pull change
 * design.md Decision 6），候选解析或任一任务插入失败均会整体回滚，不允许留下部分通知任务。
 * 事务边界由本类的 {@link #record} 方法声明；{@link AppDataChangeLogService#append} 与
 * {@link AppNotifyTaskService#enqueueTask} 均不再单独声明事务，调用时自然并入本方法的事务。
 * 实际 HTTP 发送与状态机流转（{@code NotifySendCoordinator}）在事务提交之后独立执行，
 * 不卷入本类的事务边界。
 */
@Component
@RequiredArgsConstructor
public class DomainChangeRecorder {

    /** 全局应用数据变更流水业务逻辑接口。 */
    private final AppDataChangeLogService appDataChangeLogService;

    /** 通知候选应用判定组件。 */
    private final NotifyCandidateResolver notifyCandidateResolver;

    /** 通知任务落库与状态机流转业务逻辑接口。 */
    private final AppNotifyTaskService appNotifyTaskService;

    /**
     * 在一个本地事务内写入一条全局变更流水，并为全部候选应用创建/复用 PENDING 通知任务。
     *
     * @param event 领域变更事件
     * @return 已写入的流水与候选应用通知任务列表
     */
    @Transactional
    public DomainChangeRecordResult record(DomainChangeEvent event) {
        AppDataChangeLogEntity changeLog = appDataChangeLogService.append(event);
        List<Long> candidateAppRefIds = notifyCandidateResolver.resolveCandidateAppRefIds(event);
        List<AppNotifyRecordEntity> tasks = new ArrayList<>(candidateAppRefIds.size());
        for (Long appRefId : candidateAppRefIds) {
            AppNotifyRecordEntity task = appNotifyTaskService.enqueueTask(event, changeLog, appRefId);
            if (task != null) {
                tasks.add(task);
            }
        }
        return new DomainChangeRecordResult(changeLog, tasks);
    }
}
