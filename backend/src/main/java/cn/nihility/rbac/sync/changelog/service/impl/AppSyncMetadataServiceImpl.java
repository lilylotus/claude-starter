package cn.nihility.rbac.sync.changelog.service.impl;

import cn.nihility.rbac.sync.changelog.entity.AppSyncMetadataEntity;
import cn.nihility.rbac.sync.changelog.mapper.AppSyncMetadataMapper;
import cn.nihility.rbac.sync.changelog.service.AppSyncMetadataService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 应用同步全局元数据业务逻辑实现。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppSyncMetadataServiceImpl implements AppSyncMetadataService {

    /** 应用同步全局元数据数据访问接口。 */
    private final AppSyncMetadataMapper mapper;

    /**
     * {@inheritDoc}
     */
    @Override
    public long getRetentionFloorSeq() {
        AppSyncMetadataEntity entity = mapper.selectById(CHANGE_LOG_RETENTION_FLOOR_SEQ_KEY);
        if (entity == null || entity.getMetadataValue() == null) {
            log.warn("未查询到保留窗口下界游标元数据行，防御性返回 0：key={}", CHANGE_LOG_RETENTION_FLOOR_SEQ_KEY);
            return 0L;
        }
        try {
            return Long.parseLong(entity.getMetadataValue());
        } catch (NumberFormatException e) {
            log.warn("保留窗口下界游标元数据值不是合法数字，防御性返回 0：value={}", entity.getMetadataValue(), e);
            return 0L;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void advanceRetentionFloorSeq(long newFloorSeq) {
        int affected = mapper.advanceRetentionFloorSeqIfGreater(CHANGE_LOG_RETENTION_FLOOR_SEQ_KEY,
                String.valueOf(newFloorSeq), LocalDateTime.now());
        if (affected == 0) {
            log.warn("推进保留窗口下界游标未影响任何行，元数据键可能已被误删：key={}, newFloorSeq={}",
                    CHANGE_LOG_RETENTION_FLOOR_SEQ_KEY, newFloorSeq);
        }
    }
}
