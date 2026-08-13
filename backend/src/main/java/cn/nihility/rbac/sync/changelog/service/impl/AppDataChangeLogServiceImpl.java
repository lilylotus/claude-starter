package cn.nihility.rbac.sync.changelog.service.impl;

import cn.nihility.rbac.common.util.JacksonUtils;
import cn.nihility.rbac.sync.changelog.entity.AppDataChangeLogEntity;
import cn.nihility.rbac.sync.changelog.mapper.AppDataChangeLogMapper;
import cn.nihility.rbac.sync.changelog.service.AppDataChangeLogService;
import cn.nihility.rbac.sync.event.DomainChangeEvent;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 应用数据变更记录业务逻辑实现。
 */
@Service
@RequiredArgsConstructor
public class AppDataChangeLogServiceImpl implements AppDataChangeLogService {

    /** 应用数据变更记录数据访问接口。 */
    private final AppDataChangeLogMapper appDataChangeLogMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    public AppDataChangeLogEntity record(DomainChangeEvent event) {
        LocalDateTime now = LocalDateTime.now();
        AppDataChangeLogEntity entity = AppDataChangeLogEntity.builder()
                .dataType(event.getDataType())
                .bizId(event.getBizId())
                .operationType(event.getOperationType())
                .dataSnapshot(JacksonUtils.toJson(event.getSnapshot()))
                .createBy(event.getOperator())
                .createTime(now)
                .updateBy(event.getOperator())
                .updateTime(now)
                .build();
        appDataChangeLogMapper.insert(entity);
        return entity;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<AppDataChangeLogEntity> selectLatestByBizIds(String dataType, List<Long> bizIds) {
        if (bizIds == null || bizIds.isEmpty()) {
            return List.of();
        }
        return appDataChangeLogMapper.selectLatestByBizIds(dataType, bizIds);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<AppDataChangeLogEntity> selectBySequence(Collection<String> dataTypes, Long fromSequence, int limit) {
        if (dataTypes == null || dataTypes.isEmpty()) {
            return List.of();
        }
        return appDataChangeLogMapper.selectBySequence(dataTypes, fromSequence, limit);
    }
}
