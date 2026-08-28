package cn.nihility.rbac.sync.changelog.service.impl;

import cn.nihility.rbac.sync.changelog.entity.AppDataChangeLogEntity;
import cn.nihility.rbac.sync.changelog.mapper.AppDataChangeLogMapper;
import cn.nihility.rbac.sync.changelog.service.AppDataChangeLogService;
import cn.nihility.rbac.sync.changelog.service.AppSyncMetadataService;
import cn.nihility.rbac.sync.event.DomainChangeEvent;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 全局应用数据变更流水服务实现。 */
@Service
@RequiredArgsConstructor
public class AppDataChangeLogServiceImpl implements AppDataChangeLogService {

    private final AppDataChangeLogMapper mapper;

    /** 应用同步全局元数据业务逻辑接口，用于原子推进保留窗口下界游标。 */
    private final AppSyncMetadataService appSyncMetadataService;

    /**
     * {@inheritDoc}
     * <p>
     * 独立声明事务边界：{@link cn.nihility.rbac.sync.event.support.DomainChangeRecorder#record}
     * 跨 bean 调用本方法时会并入其外层事务，其余调用方（如本方法自身单独被调用）则各自
     * 独立提交/回滚（app-sync-changelog-pull change design.md Decision 6）。
     */
    @Override
    @Transactional
    public AppDataChangeLogEntity append(DomainChangeEvent event) {
        LocalDateTime now = LocalDateTime.now();
        AppDataChangeLogEntity entity = AppDataChangeLogEntity.builder()
                .eventId(event.getEventId())
                .entityType(event.getDataType())
                .entityId(event.getBizId())
                .operationType(operationTypeCode(event.getOperationType()))
                .entityVersion(event.getEntityVersion())
                .orgScopePathBefore(event.getOrgScopePathBefore())
                .orgScopePathAfter(event.getOrgScopePathAfter())
                .changeTime(event.getOccurredAt())
                .createBy(event.getOperator())
                .createTime(now)
                .updateBy(event.getOperator())
                .updateTime(now)
                .build();
        mapper.insert(entity);
        return entity;
    }

    /**
     * {@inheritDoc}
     * <p>
     * 先按 {@code change_time} 索引取一批过期 {@code change_seq}，用这批实际取到的主键值
     * 删除（而不是"按 change_time 删除 + 另行 MAX(change_seq)"两步），保证保留窗口下界
     * 游标推进的值恰好等于本批真正删除的最大主键；删除与推进游标在同一个事务内完成，任一步
     * 失败均整体回滚，floor 不会因为部分失败而提前推进（app-sync-changelog-pull change
     * design.md Decision 8）。
     */
    @Override
    @Transactional
    public int cleanupExpiredBatch(LocalDateTime cutoff, int batchSize) {
        List<Long> expiredChangeSeqBatch = mapper.selectExpiredChangeSeqBatch(cutoff, batchSize);
        if (expiredChangeSeqBatch.isEmpty()) {
            return 0;
        }
        int deleted = mapper.delete(new LambdaQueryWrapper<AppDataChangeLogEntity>()
                .in(AppDataChangeLogEntity::getChangeSeq, expiredChangeSeqBatch));
        long maxSeqInBatch = Collections.max(expiredChangeSeqBatch);
        appSyncMetadataService.advanceRetentionFloorSeq(maxSeqInBatch);
        return deleted;
    }

    private String operationTypeCode(int operationType) {
        return switch (operationType) {
            case 1 -> "CREATE";
            case 2 -> "UPDATE";
            case 3 -> "ENABLE";
            case 4 -> "DISABLE";
            case 5 -> "DELETE";
            default -> throw new IllegalArgumentException("不支持的操作类型: " + operationType);
        };
    }
}
