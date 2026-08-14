package cn.nihility.rbac.identity.upstream.service.impl;

import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.common.result.PageResult;
import cn.nihility.rbac.identity.upstream.dto.UpstreamSyncRecordDetailVO;
import cn.nihility.rbac.identity.upstream.dto.UpstreamSyncRecordVO;
import cn.nihility.rbac.identity.upstream.entity.UpstreamSyncRecordDetailEntity;
import cn.nihility.rbac.identity.upstream.entity.UpstreamSyncRecordEntity;
import cn.nihility.rbac.identity.upstream.mapper.UpstreamSyncRecordDetailMapper;
import cn.nihility.rbac.identity.upstream.mapper.UpstreamSyncRecordMapper;
import cn.nihility.rbac.identity.upstream.service.UpstreamSyncRecordService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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

    /** 上游数据同步执行记录明细数据访问接口。 */
    private final UpstreamSyncRecordDetailMapper upstreamSyncRecordDetailMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResult<UpstreamSyncRecordVO> listBySource(Long sourceId, Integer page, Integer pageSize) {
        Page<UpstreamSyncRecordEntity> queryPage = new Page<>(page, pageSize);
        Page<UpstreamSyncRecordEntity> resultPage = upstreamSyncRecordMapper.selectPage(queryPage,
                new LambdaQueryWrapper<UpstreamSyncRecordEntity>()
                        .eq(UpstreamSyncRecordEntity::getSourceId, sourceId)
                        .orderByDesc(UpstreamSyncRecordEntity::getId));
        return PageResult.of(resultPage.getRecords().stream().map(this::toVO).toList(), resultPage);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResult<UpstreamSyncRecordDetailVO> listDetailsByRecord(Long sourceId, Long recordId, Integer page,
            Integer pageSize) {
        UpstreamSyncRecordEntity record = upstreamSyncRecordMapper.selectOne(
                new LambdaQueryWrapper<UpstreamSyncRecordEntity>()
                        .eq(UpstreamSyncRecordEntity::getId, recordId)
                        .eq(UpstreamSyncRecordEntity::getSourceId, sourceId));
        if (record == null) {
            throw new BusinessException("同步执行记录不存在");
        }
        Page<UpstreamSyncRecordDetailEntity> queryPage = new Page<>(page, pageSize);
        Page<UpstreamSyncRecordDetailEntity> resultPage = upstreamSyncRecordDetailMapper.selectPage(queryPage,
                new LambdaQueryWrapper<UpstreamSyncRecordDetailEntity>()
                        .eq(UpstreamSyncRecordDetailEntity::getSyncRecordId, recordId)
                        .orderByAsc(UpstreamSyncRecordDetailEntity::getRowNo));
        return PageResult.of(resultPage.getRecords().stream().map(this::toVO).toList(), resultPage);
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

    /**
     * 把同步执行记录明细实体转换为视图对象。
     *
     * @param entity 同步执行记录明细实体
     * @return 同步执行记录明细视图对象
     */
    private UpstreamSyncRecordDetailVO toVO(UpstreamSyncRecordDetailEntity entity) {
        return UpstreamSyncRecordDetailVO.builder()
                .id(entity.getId())
                .rowNo(entity.getRowNo())
                .rowData(entity.getRowData())
                .status(entity.getStatus())
                .failReason(entity.getFailReason())
                .build();
    }
}
