package cn.nihility.rbac.sync.notify.service.impl;

import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.common.result.PageResult;
import cn.nihility.rbac.sync.notify.constant.NotifyTaskStatus;
import cn.nihility.rbac.sync.notify.dto.AppNotifyRecordQueryRequest;
import cn.nihility.rbac.sync.notify.dto.AppNotifyRecordVO;
import cn.nihility.rbac.sync.notify.entity.AppNotifyRecordEntity;
import cn.nihility.rbac.sync.notify.mapper.AppNotifyRecordMapper;
import cn.nihility.rbac.sync.notify.mapstruct.AppNotifyRecordConvert;
import cn.nihility.rbac.sync.notify.service.AppNotifyRecordService;
import cn.nihility.rbac.sync.notify.service.AppNotifyTaskService;
import cn.nihility.rbac.sync.notify.support.NotifySendCoordinator;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 通知日志查询业务逻辑实现，{@link #retryDeadTask} 额外承担管理端手动重推的编排职责
 * （tasks.md 6.3）。
 */
@Service
@RequiredArgsConstructor
public class AppNotifyRecordServiceImpl implements AppNotifyRecordService {

    /** 应用通知发送记录数据访问接口。 */
    private final AppNotifyRecordMapper appNotifyRecordMapper;

    /** 通知任务落库与状态机流转业务逻辑接口。 */
    private final AppNotifyTaskService appNotifyTaskService;

    /** 通知任务"抢占 + 发送 + 状态流转"编排组件，重置成功后触发一次即时发送优化。 */
    private final NotifySendCoordinator notifySendCoordinator;

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResult<AppNotifyRecordVO> page(AppNotifyRecordQueryRequest request) {
        Page<AppNotifyRecordEntity> queryPage = new Page<>(request.getPage(), request.getPageSize());
        IPage<AppNotifyRecordEntity> resultPage = appNotifyRecordMapper.selectNotifyRecordPage(queryPage, request);
        List<AppNotifyRecordVO> records = AppNotifyRecordConvert.INSTANCE.toVOList(resultPage.getRecords());
        return PageResult.of(records, resultPage);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void retryDeadTask(Long appRefId, Long recordId) {
        AppNotifyRecordEntity record = appNotifyTaskService.getById(recordId);
        if (record == null || !record.getAppRefId().equals(appRefId)) {
            throw new BusinessException("通知记录不存在：id=" + recordId);
        }
        if (!NotifyTaskStatus.DEAD.equals(record.getTaskStatus())) {
            throw new BusinessException("通知记录当前不是死信状态，无法重推：id=" + recordId + ", taskStatus="
                    + record.getTaskStatus());
        }
        if (!appNotifyTaskService.resetDeadToPending(recordId)) {
            throw new BusinessException("通知记录状态已发生变化，重推失败，请刷新后重试：id=" + recordId);
        }
        AppNotifyRecordEntity resetRecord = appNotifyTaskService.getById(recordId);
        if (resetRecord != null) {
            notifySendCoordinator.submitImmediateSend(resetRecord);
        }
    }
}
