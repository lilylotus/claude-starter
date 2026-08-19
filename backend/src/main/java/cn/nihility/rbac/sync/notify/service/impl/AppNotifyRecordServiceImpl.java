package cn.nihility.rbac.sync.notify.service.impl;

import cn.nihility.rbac.common.result.PageResult;
import cn.nihility.rbac.sync.notify.dto.AppNotifyRecordQueryRequest;
import cn.nihility.rbac.sync.notify.dto.AppNotifyRecordVO;
import cn.nihility.rbac.sync.notify.entity.AppNotifyRecordEntity;
import cn.nihility.rbac.sync.notify.mapper.AppNotifyRecordMapper;
import cn.nihility.rbac.sync.notify.mapstruct.AppNotifyRecordConvert;
import cn.nihility.rbac.sync.notify.service.AppNotifyRecordService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 通知日志查询业务逻辑实现。
 */
@Service
@RequiredArgsConstructor
public class AppNotifyRecordServiceImpl implements AppNotifyRecordService {

    /** 应用通知发送记录数据访问接口。 */
    private final AppNotifyRecordMapper appNotifyRecordMapper;

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
}
