package cn.nihility.rbac.identity.upstream.service.impl;

import cn.nihility.rbac.identity.upstream.dto.UpstreamSyncRecordVO;
import cn.nihility.rbac.identity.upstream.entity.UpstreamSyncRecordEntity;
import cn.nihility.rbac.identity.upstream.mapper.UpstreamSyncRecordMapper;
import cn.nihility.rbac.identity.upstream.service.UpstreamSyncRecordService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 上游数据同步执行记录业务逻辑实现。字段名与实体完全一致，未接入 MapStruct（记录仅供
 * 只读展示，无需实体/DTO 双向转换，直接手工搬运更简单）。
 */
@Service
@RequiredArgsConstructor
public class UpstreamSyncRecordServiceImpl implements UpstreamSyncRecordService {

    /** 上游数据同步执行记录数据访问接口。 */
    private final UpstreamSyncRecordMapper upstreamSyncRecordMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    public List<UpstreamSyncRecordVO> listBySource(Long sourceId) {
        List<UpstreamSyncRecordEntity> entities = upstreamSyncRecordMapper.selectList(
                new LambdaQueryWrapper<UpstreamSyncRecordEntity>()
                        .eq(UpstreamSyncRecordEntity::getSourceId, sourceId)
                        .orderByDesc(UpstreamSyncRecordEntity::getId));
        return entities.stream().map(this::toVO).toList();
    }

    /**
     * 把同步执行记录实体转换为视图对象。
     *
     * @param entity 同步执行记录实体
     * @return 同步执行记录视图对象
     */
    private UpstreamSyncRecordVO toVO(UpstreamSyncRecordEntity entity) {
        return UpstreamSyncRecordVO.builder()
                .id(entity.getId())
                .sourceId(entity.getSourceId())
                .dataType(entity.getDataType())
                .triggerType(entity.getTriggerType())
                .startTime(entity.getStartTime())
                .endTime(entity.getEndTime())
                .status(entity.getStatus())
                .totalCount(entity.getTotalCount())
                .successCount(entity.getSuccessCount())
                .failCount(entity.getFailCount())
                .failSummary(entity.getFailSummary())
                .build();
    }
}
