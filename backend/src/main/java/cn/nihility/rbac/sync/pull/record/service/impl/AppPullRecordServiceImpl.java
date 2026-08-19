package cn.nihility.rbac.sync.pull.record.service.impl;

import cn.nihility.rbac.common.result.PageResult;
import cn.nihility.rbac.sync.pull.record.dto.AppPullRecordQueryRequest;
import cn.nihility.rbac.sync.pull.record.dto.AppPullRecordVO;
import cn.nihility.rbac.sync.pull.record.entity.AppPullRecordEntity;
import cn.nihility.rbac.sync.pull.record.mapper.AppPullRecordMapper;
import cn.nihility.rbac.sync.pull.record.mapstruct.AppPullRecordConvert;
import cn.nihility.rbac.sync.pull.record.service.AppPullRecordService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 应用拉取变更数据请求记录业务逻辑实现。
 */
@Service
@RequiredArgsConstructor
public class AppPullRecordServiceImpl implements AppPullRecordService {

    /**
     * 拉取日志写入场景下审计字段的固定操作人标识：由外部应用调用开放接口触发，无管理端登录
     * 态，同 {@code AppNotifyServiceImpl} 固定 "system" 的既有模式，换一个更贴切的标识值
     * 区分"系统内部产生"与"外部应用调用产生"（design.md Decision 2）。
     */
    private static final String OPEN_API_OPERATOR = "open-api";

    /** 应用拉取变更数据请求记录数据访问接口。 */
    private final AppPullRecordMapper appPullRecordMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    public void record(Long appRefId, String pullMode, String dataType, String requestSummary, int resultCount) {
        LocalDateTime now = LocalDateTime.now();
        AppPullRecordEntity entity = AppPullRecordEntity.builder()
                .appRefId(appRefId)
                .pullMode(pullMode)
                .dataType(dataType)
                .requestSummary(requestSummary)
                .resultCount(resultCount)
                .createBy(OPEN_API_OPERATOR)
                .createTime(now)
                .updateBy(OPEN_API_OPERATOR)
                .updateTime(now)
                .build();
        appPullRecordMapper.insert(entity);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResult<AppPullRecordVO> page(AppPullRecordQueryRequest request) {
        Page<AppPullRecordEntity> queryPage = new Page<>(request.getPage(), request.getPageSize());
        IPage<AppPullRecordEntity> resultPage = appPullRecordMapper.selectPullRecordPage(queryPage, request);
        List<AppPullRecordVO> records = AppPullRecordConvert.INSTANCE.toVOList(resultPage.getRecords());
        return PageResult.of(records, resultPage);
    }
}
