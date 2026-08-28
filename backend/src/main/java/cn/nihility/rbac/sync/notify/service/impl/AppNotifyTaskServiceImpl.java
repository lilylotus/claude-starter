package cn.nihility.rbac.sync.notify.service.impl;

import cn.nihility.rbac.app.entity.AppConfigEntity;
import cn.nihility.rbac.app.mapper.AppConfigMapper;
import cn.nihility.rbac.common.util.JacksonUtils;
import cn.nihility.rbac.sync.changelog.entity.AppDataChangeLogEntity;
import cn.nihility.rbac.sync.constant.SyncOperationType;
import cn.nihility.rbac.sync.event.DomainChangeEvent;
import cn.nihility.rbac.sync.notify.constant.NotifyStatus;
import cn.nihility.rbac.sync.notify.constant.NotifyTaskStatus;
import cn.nihility.rbac.sync.notify.dto.NotifyPayload;
import cn.nihility.rbac.sync.notify.entity.AppNotifyRecordEntity;
import cn.nihility.rbac.sync.notify.mapper.AppNotifyRecordMapper;
import cn.nihility.rbac.sync.notify.service.AppNotifyTaskService;
import cn.nihility.rbac.sync.notify.support.NotifyRetryScheduleCalculator;
import cn.nihility.rbac.sync.transform.BizSnapshotResolver;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 通知任务落库与状态机流转业务逻辑实现（app-sync-changelog-pull change design.md
 * Decision 6）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppNotifyTaskServiceImpl implements AppNotifyTaskService {

    /** 通知/落库场景下审计字段的固定操作人标识：变更事件消费发生在后台线程，无登录用户上下文。 */
    private static final String SYSTEM_OPERATOR = "system";

    /** {@code tab_app_notify_record.notify_url} 列的长度上限。 */
    private static final int NOTIFY_URL_MAX_LENGTH = 255;

    /** {@code tab_app_notify_record.error_msg} 列的长度上限。 */
    private static final int ERROR_MSG_MAX_LENGTH = 500;

    /** 应用通知发送记录数据访问接口。 */
    private final AppNotifyRecordMapper appNotifyRecordMapper;

    /** 应用对外接口凭证配置数据访问接口，按 {@code appRefId} 查询目标应用当前的通知地址与自定义参数。 */
    private final AppConfigMapper appConfigMapper;

    /** 业务对象当前快照解析器，用于现查被变更对象的业务编码字段（{@code bizCode}）。 */
    private final BizSnapshotResolver bizSnapshotResolver;

    /** 重试退避时间计算器。 */
    private final NotifyRetryScheduleCalculator notifyRetryScheduleCalculator;

    /**
     * {@inheritDoc}
     */
    @Override
    public AppNotifyRecordEntity enqueueTask(DomainChangeEvent event, AppDataChangeLogEntity changeLog,
            Long appRefId) {
        AppNotifyRecordEntity existing = findExisting(appRefId, event.getEventId());
        if (existing != null) {
            // 同一事件同一应用的重试复用同一行，不新插入（design.md Decision 6）。
            return existing;
        }
        AppConfigEntity target = appConfigMapper.selectOne(
                new LambdaQueryWrapper<AppConfigEntity>().eq(AppConfigEntity::getAppRefId, appRefId));
        if (target == null) {
            log.warn("候选应用[{}]的对外接口配置已不存在，跳过本次通知任务落库：eventId={}", appRefId, event.getEventId());
            return null;
        }

        AppNotifyRecordEntity task = buildPendingTask(event, changeLog, appRefId, target);
        try {
            appNotifyRecordMapper.insert(task);
        } catch (DuplicateKeyException e) {
            log.warn("通知任务并发插入冲突，复用已存在的行：appRefId={}, eventId={}", appRefId, event.getEventId());
            return findExisting(appRefId, event.getEventId());
        }
        return task;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<AppNotifyRecordEntity> scanDueTasks(LocalDateTime now, int batchSize) {
        return appNotifyRecordMapper.selectDueTasks(now, batchSize);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean claim(Long id, String expectedStatus, LocalDateTime now, LocalDateTime leaseUntil) {
        LambdaUpdateWrapper<AppNotifyRecordEntity> wrapper = new LambdaUpdateWrapper<AppNotifyRecordEntity>()
                .eq(AppNotifyRecordEntity::getId, id)
                .eq(AppNotifyRecordEntity::getTaskStatus, expectedStatus)
                .set(AppNotifyRecordEntity::getTaskStatus, NotifyTaskStatus.PROCESSING)
                .set(AppNotifyRecordEntity::getLeaseUntil, leaseUntil)
                .set(AppNotifyRecordEntity::getUpdateBy, SYSTEM_OPERATOR)
                .set(AppNotifyRecordEntity::getUpdateTime, now);
        if (NotifyTaskStatus.PROCESSING.equals(expectedStatus)) {
            // 抢占租约超时的 PROCESSING 任务：额外要求 lease_until < now，避免把一个仍在
            // 正常处理中的任务错误地重复抢占。
            wrapper.lt(AppNotifyRecordEntity::getLeaseUntil, now);
        }
        return appNotifyRecordMapper.update(null, wrapper) == 1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void markSuccess(Long id, Integer httpStatus) {
        LocalDateTime now = LocalDateTime.now();
        appNotifyRecordMapper.update(null, new LambdaUpdateWrapper<AppNotifyRecordEntity>()
                .eq(AppNotifyRecordEntity::getId, id)
                .set(AppNotifyRecordEntity::getTaskStatus, NotifyTaskStatus.SUCCESS)
                .set(AppNotifyRecordEntity::getNotifyStatus, NotifyStatus.SUCCESS)
                .set(AppNotifyRecordEntity::getHttpStatus, httpStatus)
                .set(AppNotifyRecordEntity::getErrorMsg, null)
                .set(AppNotifyRecordEntity::getNextRetryTime, null)
                .set(AppNotifyRecordEntity::getLeaseUntil, null)
                .set(AppNotifyRecordEntity::getUpdateBy, SYSTEM_OPERATOR)
                .set(AppNotifyRecordEntity::getUpdateTime, now));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void recordAttemptFailure(Long id, int retryCountBeforeAttempt, boolean retryableByType,
            Integer httpStatus, String errorMsg) {
        LocalDateTime now = LocalDateTime.now();
        String truncatedErrorMsg = truncate(errorMsg, ERROR_MSG_MAX_LENGTH);
        if (!retryableByType) {
            updateDead(id, retryCountBeforeAttempt + 1, httpStatus, truncatedErrorMsg, now);
            return;
        }
        NotifyRetryScheduleCalculator.RetryDecision decision =
                notifyRetryScheduleCalculator.decide(retryCountBeforeAttempt, now);
        if (decision.dead()) {
            updateDead(id, decision.retryCount(), httpStatus, truncatedErrorMsg, now);
        } else {
            updateRetry(id, decision.retryCount(), decision.nextRetryTime(), httpStatus, truncatedErrorMsg, now);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AppNotifyRecordEntity getById(Long id) {
        return appNotifyRecordMapper.selectById(id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean resetDeadToPending(Long id) {
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<AppNotifyRecordEntity> wrapper = new LambdaUpdateWrapper<AppNotifyRecordEntity>()
                .eq(AppNotifyRecordEntity::getId, id)
                .eq(AppNotifyRecordEntity::getTaskStatus, NotifyTaskStatus.DEAD)
                .set(AppNotifyRecordEntity::getTaskStatus, NotifyTaskStatus.PENDING)
                .set(AppNotifyRecordEntity::getRetryCount, 0)
                .set(AppNotifyRecordEntity::getLeaseUntil, null)
                .set(AppNotifyRecordEntity::getNextRetryTime, null)
                .set(AppNotifyRecordEntity::getUpdateBy, SYSTEM_OPERATOR)
                .set(AppNotifyRecordEntity::getUpdateTime, now);
        return appNotifyRecordMapper.update(null, wrapper) == 1;
    }

    /**
     * 把一条任务标记为死信（终态）。
     *
     * @param id         任务主键 id
     * @param retryCount 本次失败后的累计已失败次数
     * @param httpStatus 外部接口返回的状态码，网络异常等未收到响应时为 {@code null}
     * @param errorMsg   失败原因摘要（已截断）
     * @param now        当前时刻
     */
    private void updateDead(Long id, int retryCount, Integer httpStatus, String errorMsg, LocalDateTime now) {
        appNotifyRecordMapper.update(null, new LambdaUpdateWrapper<AppNotifyRecordEntity>()
                .eq(AppNotifyRecordEntity::getId, id)
                .set(AppNotifyRecordEntity::getTaskStatus, NotifyTaskStatus.DEAD)
                .set(AppNotifyRecordEntity::getNotifyStatus, NotifyStatus.FAILURE)
                .set(AppNotifyRecordEntity::getRetryCount, retryCount)
                .set(AppNotifyRecordEntity::getHttpStatus, httpStatus)
                .set(AppNotifyRecordEntity::getErrorMsg, errorMsg)
                .set(AppNotifyRecordEntity::getNextRetryTime, null)
                .set(AppNotifyRecordEntity::getLeaseUntil, null)
                .set(AppNotifyRecordEntity::getUpdateBy, SYSTEM_OPERATOR)
                .set(AppNotifyRecordEntity::getUpdateTime, now));
    }

    /**
     * 把一条任务标记为待重试。
     *
     * @param id            任务主键 id
     * @param retryCount    本次失败后的累计已失败次数
     * @param nextRetryTime 下一次允许重试的时间
     * @param httpStatus    外部接口返回的状态码，网络异常等未收到响应时为 {@code null}
     * @param errorMsg      失败原因摘要（已截断）
     * @param now           当前时刻
     */
    private void updateRetry(Long id, int retryCount, LocalDateTime nextRetryTime, Integer httpStatus,
            String errorMsg, LocalDateTime now) {
        appNotifyRecordMapper.update(null, new LambdaUpdateWrapper<AppNotifyRecordEntity>()
                .eq(AppNotifyRecordEntity::getId, id)
                .set(AppNotifyRecordEntity::getTaskStatus, NotifyTaskStatus.RETRY)
                .set(AppNotifyRecordEntity::getNotifyStatus, NotifyStatus.FAILURE)
                .set(AppNotifyRecordEntity::getRetryCount, retryCount)
                .set(AppNotifyRecordEntity::getHttpStatus, httpStatus)
                .set(AppNotifyRecordEntity::getErrorMsg, errorMsg)
                .set(AppNotifyRecordEntity::getNextRetryTime, nextRetryTime)
                .set(AppNotifyRecordEntity::getLeaseUntil, null)
                .set(AppNotifyRecordEntity::getUpdateBy, SYSTEM_OPERATOR)
                .set(AppNotifyRecordEntity::getUpdateTime, now));
    }

    /**
     * 按 {@code (appRefId, eventId)} 唯一键查询是否已存在任务行。
     *
     * @param appRefId 应用 id
     * @param eventId  雪花事件标识
     * @return 已存在的任务行，不存在时返回 {@code null}
     */
    private AppNotifyRecordEntity findExisting(Long appRefId, Long eventId) {
        return appNotifyRecordMapper.selectOne(new LambdaQueryWrapper<AppNotifyRecordEntity>()
                .eq(AppNotifyRecordEntity::getAppRefId, appRefId)
                .eq(AppNotifyRecordEntity::getEventId, eventId));
    }

    /**
     * 构造一条待插入的 {@code PENDING} 任务记录，其中 {@code requestBody}/{@code notifyUrl}
     * 均为一次性生成的快照，此后重试不再重新计算（design.md Decision 6）。
     *
     * @param event     领域变更事件
     * @param changeLog 本次事件对应的全局变更流水
     * @param appRefId  应用 id
     * @param target    目标应用对外接口凭证配置
     * @return 待插入的任务记录
     */
    private AppNotifyRecordEntity buildPendingTask(DomainChangeEvent event, AppDataChangeLogEntity changeLog,
            Long appRefId, AppConfigEntity target) {
        String bizCode = bizSnapshotResolver.resolveBizCode(event.getDataType(), event.getBizId());
        NotifyPayload payload = NotifyPayload.builder()
                .eventId(Objects.toString(event.getEventId(), null))
                .changeSeq(Objects.toString(changeLog.getChangeSeq(), null))
                .entityVersion(Objects.toString(event.getEntityVersion(), null))
                .dataType(event.getDataType())
                .operationType(SyncOperationType.code(event.getOperationType()))
                .bizId(Objects.toString(event.getBizId(), null))
                .bizCode(bizCode)
                .occurredAt(event.getOccurredAt())
                .extra(parseNotifyParams(target.getNotifyParams()))
                .build();
        String requestBody = JacksonUtils.toJson(payload);

        LocalDateTime now = LocalDateTime.now();
        return AppNotifyRecordEntity.builder()
                .appRefId(appRefId)
                .eventId(event.getEventId())
                .changeSeq(changeLog.getChangeSeq())
                .entityVersion(event.getEntityVersion())
                .dataType(event.getDataType())
                .bizId(event.getBizId())
                .notifyUrl(truncate(target.getNotifyUrl(), NOTIFY_URL_MAX_LENGTH))
                .requestBody(requestBody)
                .taskStatus(NotifyTaskStatus.PENDING)
                .retryCount(0)
                .createBy(SYSTEM_OPERATOR)
                .createTime(now)
                .updateBy(SYSTEM_OPERATOR)
                .updateTime(now)
                .build();
    }

    /**
     * 把应用配置的通知自定义参数原始 JSON 文本解析为 {@code Map<String,String>}。
     *
     * @param notifyParams 原始 JSON 文本，可能为空
     * @return 解析后的自定义参数，解析失败或为空时返回空 Map
     */
    private Map<String, String> parseNotifyParams(String notifyParams) {
        if (!StringUtils.hasText(notifyParams)) {
            return Map.of();
        }
        try {
            return JacksonUtils.toObj(notifyParams, JacksonUtils.MAP_STRING_TYPE_REFERENCE);
        } catch (Exception e) {
            return Map.of();
        }
    }

    /**
     * 把文本截断到给定长度上限，防止超出对应数据库列的长度限制。
     *
     * @param text      原始文本
     * @param maxLength 长度上限
     * @return 截断后的文本
     */
    private String truncate(String text, int maxLength) {
        if (text == null) {
            return null;
        }
        return text.length() > maxLength ? text.substring(0, maxLength) : text;
    }
}
