package cn.nihility.rbac.sync.openapi.service.impl;

import cn.nihility.rbac.app.sync.constant.SyncDomain;
import cn.nihility.rbac.app.sync.entity.AppSyncDomainConfigEntity;
import cn.nihility.rbac.app.sync.mapper.AppSyncDomainConfigMapper;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.sync.changelog.entity.AppDataChangeLogEntity;
import cn.nihility.rbac.sync.changelog.service.AppDataChangeLogService;
import cn.nihility.rbac.sync.constant.SyncOperationType;
import cn.nihility.rbac.sync.openapi.OpenApiCallerContext;
import cn.nihility.rbac.sync.openapi.dto.SyncPullRecordVO;
import cn.nihility.rbac.sync.openapi.service.SyncPullService;
import cn.nihility.rbac.sync.transform.BizSnapshotResolver;
import cn.nihility.rbac.sync.transform.FieldMappingTransformer;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 对外拉取业务逻辑实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SyncPullServiceImpl implements SyncPullService {

    /** 未指定 {@code limit}/未指定数据域配置时兜底使用的分页大小。 */
    private static final int DEFAULT_LIMIT = 20;

    /** 应用同步数据域配置数据访问接口。 */
    private final AppSyncDomainConfigMapper appSyncDomainConfigMapper;

    /** 应用数据变更记录业务逻辑接口。 */
    private final AppDataChangeLogService appDataChangeLogService;

    /** 字段映射转换执行器。 */
    private final FieldMappingTransformer fieldMappingTransformer;

    /** 业务对象当前快照解析器，按数据域现查组织/用户/任职/应用/角色业务表当前数据。 */
    private final BizSnapshotResolver bizSnapshotResolver;

    /**
     * {@inheritDoc}
     */
    @Override
    public List<SyncPullRecordVO> pullByBizIds(String dataType, List<Long> bizIds) {
        assertValidDataType(dataType);
        Long appRefId = OpenApiCallerContext.getAppRefId();
        if (bizIds == null || bizIds.isEmpty() || !isDomainEnabled(appRefId, dataType)) {
            return List.of();
        }

        List<AppDataChangeLogEntity> logs = appDataChangeLogService.selectLatestByBizIds(dataType, bizIds);
        return logs.stream().map(changeLog -> toVO(appRefId, changeLog)).filter(Optional::isPresent)
                .map(Optional::get).toList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<SyncPullRecordVO> pullBySequence(String dataType, Long fromSequence, Integer limit) {
        Long appRefId = OpenApiCallerContext.getAppRefId();
        List<AppSyncDomainConfigEntity> enabledConfigs = listEnabledDomainConfigs(appRefId);

        if (StringUtils.hasText(dataType)) {
            assertValidDataType(dataType);
            AppSyncDomainConfigEntity domainConfig = enabledConfigs.stream()
                    .filter(config -> dataType.equals(config.getSyncDomain()))
                    .findFirst()
                    .orElse(null);
            if (domainConfig == null) {
                return List.of();
            }
            int effectiveLimit = effectiveLimit(limit, domainConfig.getPageSize());
            List<AppDataChangeLogEntity> logs = appDataChangeLogService.selectBySequence(List.of(dataType),
                    fromSequence, effectiveLimit);
            return logs.stream().map(changeLog -> toVO(appRefId, changeLog)).filter(Optional::isPresent)
                    .map(Optional::get).toList();
        }

        if (enabledConfigs.isEmpty()) {
            return List.of();
        }
        int minPageSize = enabledConfigs.stream()
                .map(AppSyncDomainConfigEntity::getPageSize)
                .min(Integer::compareTo)
                .orElse(DEFAULT_LIMIT);
        int effectiveLimit = effectiveLimit(limit, minPageSize);
        Set<String> domains = new LinkedHashSet<>();
        enabledConfigs.forEach(config -> domains.add(config.getSyncDomain()));
        List<AppDataChangeLogEntity> logs = appDataChangeLogService.selectBySequence(domains, fromSequence,
                effectiveLimit);
        return logs.stream().map(changeLog -> toVO(appRefId, changeLog)).filter(Optional::isPresent)
                .map(Optional::get).toList();
    }

    /**
     * 校验请求的数据类型是否是同步能力支持的合法取值（ORG/USER/POSITION/APP/ROLE，不含
     * DICT），非法取值直接拒绝（区别于"合法但未开通"场景的返回空结果，见 design.md
     * Decision 9）。
     *
     * @param dataType 数据类型
     */
    private void assertValidDataType(String dataType) {
        if (!SyncDomain.CHANGE_LOG_DOMAINS.contains(dataType)) {
            throw new BusinessException("非法的数据类型：" + dataType);
        }
    }

    /**
     * 判断给定应用的给定数据域当前是否允许同步。
     *
     * @param appRefId 应用 id
     * @param dataType 数据类型
     * @return 是否允许同步
     */
    private boolean isDomainEnabled(Long appRefId, String dataType) {
        AppSyncDomainConfigEntity config = appSyncDomainConfigMapper.selectOne(
                new LambdaQueryWrapper<AppSyncDomainConfigEntity>()
                        .eq(AppSyncDomainConfigEntity::getAppRefId, appRefId)
                        .eq(AppSyncDomainConfigEntity::getSyncDomain, dataType));
        return config != null && Boolean.TRUE.equals(config.getSyncEnabled());
    }

    /**
     * 查询给定应用当前允许同步（{@code syncEnabled=true}）的全部数据域配置。
     *
     * @param appRefId 应用 id
     * @return 允许同步的数据域配置列表
     */
    private List<AppSyncDomainConfigEntity> listEnabledDomainConfigs(Long appRefId) {
        return appSyncDomainConfigMapper.selectList(new LambdaQueryWrapper<AppSyncDomainConfigEntity>()
                .eq(AppSyncDomainConfigEntity::getAppRefId, appRefId)
                .eq(AppSyncDomainConfigEntity::getSyncEnabled, true));
    }

    /**
     * 计算本次拉取的有效 {@code limit}：调用方显式指定且为正数时优先，否则回退到给定默认值。
     *
     * @param requestedLimit 调用方请求的 {@code limit}，可为空
     * @param fallback       回退默认值
     * @return 有效 {@code limit}
     */
    private int effectiveLimit(Integer requestedLimit, int fallback) {
        return requestedLimit != null && requestedLimit > 0 ? requestedLimit : fallback;
    }

    /**
     * 把一条变更记录转换为拉取接口的返回视图对象：{@code sequence}/{@code dataType}/
     * {@code operationType}/{@code bizId}/{@code occurredAt} 等元信息仍取自变更记录本身，
     * {@code data} 改为按 {@code dataType}+{@code bizId} 现查业务表当前数据（而不是
     * 变更发生那一刻冻结的历史快照），再按该应用该数据域的字段映射配置转换
     * （fix-app-sync-pull-live-data change design.md Decision 2）。业务表已查不到该行
     * （理论上不会发生，仅作防御性兜底）时返回 {@link Optional#empty()}，调用方需要
     * 过滤掉这类记录，不能让调用方拿到一条 {@code data} 缺失的残缺记录。
     *
     * @param appRefId  应用 id
     * @param changeLog 变更记录
     * @return 拉取接口返回视图对象，业务表查不到对应行时为空
     */
    private Optional<SyncPullRecordVO> toVO(Long appRefId, AppDataChangeLogEntity changeLog) {
        Map<String, Object> snapshot = bizSnapshotResolver.resolve(changeLog.getDataType(), changeLog.getBizId());
        if (snapshot == null) {
            log.warn("业务表查不到对应行，跳过该条拉取记录：changeLogId={}, dataType={}, bizId={}", changeLog.getId(),
                    changeLog.getDataType(), changeLog.getBizId());
            return Optional.empty();
        }
        Map<String, Object> data = fieldMappingTransformer.transform(appRefId, changeLog.getDataType(), snapshot);
        return Optional.of(SyncPullRecordVO.builder()
                .sequence(changeLog.getId())
                .dataType(changeLog.getDataType())
                .operationType(SyncOperationType.code(changeLog.getOperationType()))
                .bizId(changeLog.getBizId())
                .occurredAt(changeLog.getCreateTime())
                .data(data)
                .build());
    }
}
