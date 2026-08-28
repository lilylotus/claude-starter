package cn.nihility.rbac.sync.openapi.service.impl;

import cn.nihility.rbac.app.entity.AppConfigEntity;
import cn.nihility.rbac.app.mapper.AppConfigMapper;
import cn.nihility.rbac.app.sync.constant.SyncDomain;
import cn.nihility.rbac.app.sync.entity.AppSyncDomainConfigEntity;
import cn.nihility.rbac.app.sync.mapper.AppSyncDomainConfigMapper;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.sync.changelog.entity.AppDataChangeLogEntity;
import cn.nihility.rbac.sync.changelog.mapper.AppDataChangeLogMapper;
import cn.nihility.rbac.sync.changelog.service.AppSyncMetadataService;
import cn.nihility.rbac.sync.cursor.service.AppSyncCursorService;
import cn.nihility.rbac.sync.openapi.OpenApiCallerContext;
import cn.nihility.rbac.sync.openapi.dto.SyncChangePointerVO;
import cn.nihility.rbac.sync.openapi.dto.SyncChangesPageVO;
import cn.nihility.rbac.sync.openapi.dto.SyncChangesRequest;
import cn.nihility.rbac.sync.openapi.service.SyncChangesService;
import cn.nihility.rbac.sync.scope.AppSyncOrgScopeResolver;
import cn.nihility.rbac.sync.scope.ScopePrefix;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 增量游标拉取变更指针业务逻辑实现（app-sync-changelog-pull change design.md Decision
 * 4/9/10/11）。
 */
@Service
@RequiredArgsConstructor
public class SyncChangesServiceImpl implements SyncChangesService {

    /** 未指定 {@code pageSize}、且数据域配置的 {@code pageSize} 也异常缺失时兜底使用的分页大小。 */
    private static final int DEFAULT_PAGE_SIZE = 20;

    /**
     * {@code sinceSeq} 早于变更流水保留窗口下界时的业务错误码，语义类比 HTTP 410 Gone
     * （游标指向的历史数据已被永久清理）；本项目"业务错误一律 HTTP 200 + {@code Result.code}"
     * 的既有约定下，仍是业务码而非真实 HTTP 状态码。
     */
    private static final int CURSOR_EXPIRED_CODE = 410;

    /** 应用对外接口配置数据访问接口，用于判断应用同步总开关是否开启、读取 {@code configEpoch}。 */
    private final AppConfigMapper appConfigMapper;

    /** 应用同步数据域配置数据访问接口。 */
    private final AppSyncDomainConfigMapper appSyncDomainConfigMapper;

    /** 应用同步组织范围解析业务逻辑组件。 */
    private final AppSyncOrgScopeResolver appSyncOrgScopeResolver;

    /** 全局应用数据变更流水数据访问接口。 */
    private final AppDataChangeLogMapper appDataChangeLogMapper;

    /** 应用同步全局元数据业务逻辑接口，用于判断 {@code sinceSeq} 是否已过期。 */
    private final AppSyncMetadataService appSyncMetadataService;

    /** 应用同步服务端投递水位业务逻辑接口。 */
    private final AppSyncCursorService appSyncCursorService;

    /**
     * {@inheritDoc}
     */
    @Override
    public SyncChangesPageVO changes(SyncChangesRequest request) {
        assertValidEntityType(request.getEntityType());
        String entityType = request.getEntityType();
        Long appRefId = OpenApiCallerContext.getAppRefId();
        long sinceSeq = parseSeq(request.getSinceSeq());

        AppConfigEntity appConfig = appConfigMapper.selectOne(
                new LambdaQueryWrapper<AppConfigEntity>().eq(AppConfigEntity::getAppRefId, appRefId));
        String configEpoch = String.valueOf(
                appConfig != null && appConfig.getConfigEpoch() != null ? appConfig.getConfigEpoch() : 0L);

        boolean canQuery = appConfig != null && Boolean.TRUE.equals(appConfig.getSyncMasterEnabled());
        AppSyncDomainConfigEntity domainConfig = null;
        if (canQuery) {
            domainConfig = appSyncDomainConfigMapper.selectOne(new LambdaQueryWrapper<AppSyncDomainConfigEntity>()
                    .eq(AppSyncDomainConfigEntity::getAppRefId, appRefId)
                    .eq(AppSyncDomainConfigEntity::getSyncDomain, entityType));
            canQuery = domainConfig != null && Boolean.TRUE.equals(domainConfig.getSyncEnabled());
        }
        int effectivePageSize =
                effectivePageSize(request.getPageSize(), domainConfig != null ? domainConfig.getPageSize() : null);

        // 同步总开关关闭/数据域未开通同步：对齐 /pull 既有约定，返回空结果而不是报错
        // （design.md Decision 4，tasks.md 4.3），不做保留窗口过期校验、不推进投递水位。
        if (!canQuery) {
            return emptyResult(entityType, sinceSeq, sinceSeq, false, configEpoch);
        }

        long floor = appSyncMetadataService.getRetentionFloorSeq();
        if (sinceSeq < floor) {
            throw new BusinessException(CURSOR_EXPIRED_CODE,
                    "sinceSeq 已早于变更流水保留窗口下界，历史记录已被清理，请改走全量拉取 GET /open/api/sync/pull "
                            + "重建，并从 GET /open/api/sync/digest 返回的 currentMaxSeq 重新开始增量拉取");
        }

        List<ScopePrefix> prefixes = resolveScopePrefixesIfApplicable(appRefId, entityType);
        boolean isUserDomain = SyncDomain.USER.equals(entityType);

        List<AppDataChangeLogEntity> visible = new ArrayList<>();
        long lastScannedSeq = sinceSeq;
        boolean underlyingExhausted = false;
        while (visible.size() < effectivePageSize && !underlyingExhausted) {
            int remaining = effectivePageSize - visible.size();
            List<AppDataChangeLogEntity> batch =
                    appDataChangeLogMapper.selectChanges(entityType, lastScannedSeq, remaining, prefixes);
            if (batch.isEmpty()) {
                underlyingExhausted = true;
                break;
            }
            lastScannedSeq = batch.get(batch.size() - 1).getChangeSeq();
            List<AppDataChangeLogEntity> filtered = isUserDomain ? filterUserVisible(appRefId, batch) : batch;
            visible.addAll(filtered);
            if (batch.size() < remaining) {
                underlyingExhausted = true;
            }
        }

        List<SyncChangePointerVO> records = visible.stream().map(this::toPointer).toList();
        boolean hasMore = !underlyingExhausted;
        String nextSeq = String.valueOf(lastScannedSeq);

        appSyncCursorService.advance(appRefId, entityType, lastScannedSeq);

        return SyncChangesPageVO.builder().entityType(entityType).sinceSeq(String.valueOf(sinceSeq))
                .nextSeq(nextSeq).hasMore(hasMore).configEpoch(configEpoch).records(records).build();
    }

    /**
     * 校验 {@code entityType} 是否是 {@code /changes} 接口支持的合法取值（ORG/USER/
     * POSITION/APP/ROLE，不含 DICT，design.md Decision 4）。
     *
     * @param entityType 数据类型
     */
    private void assertValidEntityType(String entityType) {
        if (!SyncDomain.CHANGES_ENTITY_TYPES.contains(entityType)) {
            throw new BusinessException("非法的数据类型：" + entityType);
        }
    }

    /**
     * 把十进制字符串形式的游标解析为 {@code long}，未传入时视为 {@code "0"}（从头开始）
     * （app-sync-changelog-pull change design.md Decision 11）。
     *
     * @param sinceSeq 十进制字符串形式的起始游标，可为空
     * @return 解析后的 {@code long}
     * @throws BusinessException 格式非法或为负数时抛出
     */
    private long parseSeq(String sinceSeq) {
        if (!StringUtils.hasText(sinceSeq)) {
            return 0L;
        }
        try {
            long value = Long.parseLong(sinceSeq.trim());
            if (value < 0) {
                throw new NumberFormatException("sinceSeq 不能为负数：" + sinceSeq);
            }
            return value;
        } catch (NumberFormatException e) {
            throw new BusinessException("sinceSeq 参数格式不正确，应为非负整数的十进制字符串：" + sinceSeq);
        }
    }

    /**
     * 仅 ORG/POSITION 两个数据域解析组织范围前缀，供底层流水查询下推路径过滤；USER/APP/ROLE
     * 恒返回 {@code null}（不限制），USER 数据域改用 {@link #filterUserVisible} 在应用层
     * 批量过滤（design.md Decision 4——USER 数据域不适用路径前缀）。
     *
     * @param appRefId   应用 id
     * @param entityType 数据类型
     * @return 组织范围前缀列表，{@code null} 表示不限制（含"该数据域未配置组织范围限制"的
     *         零行场景，与"该数据域本就不支持组织范围"场景统一处理）
     */
    private List<ScopePrefix> resolveScopePrefixesIfApplicable(Long appRefId, String entityType) {
        if (!SyncDomain.ORG.equals(entityType) && !SyncDomain.POSITION.equals(entityType)) {
            return null;
        }
        List<ScopePrefix> prefixes = appSyncOrgScopeResolver.resolveScopePrefixes(appRefId, entityType);
        return prefixes.isEmpty() ? null : prefixes;
    }

    /**
     * 批量过滤 USER 数据域候选变更记录：一次批量查询候选用户的当前任职，避免逐用户 N+1
     * （design.md Decision 4）。
     *
     * @param appRefId 应用 id
     * @param batch    候选变更记录（{@code entityId} 即用户 id）
     * @return 落在该应用 USER 数据域允许组织范围内的变更记录子集
     */
    private List<AppDataChangeLogEntity> filterUserVisible(Long appRefId, List<AppDataChangeLogEntity> batch) {
        Set<Long> candidateUserIds = batch.stream().map(AppDataChangeLogEntity::getEntityId)
                .collect(Collectors.toSet());
        Set<Long> matchedUserIds = appSyncOrgScopeResolver.filterUsersWithinScope(appRefId, candidateUserIds);
        return batch.stream().filter(entity -> matchedUserIds.contains(entity.getEntityId())).toList();
    }

    /**
     * 计算本次拉取的有效 {@code pageSize}：调用方显式指定且为正数时优先，否则回退到该数据域
     * 配置的 {@code pageSize}，配置值也异常缺失时再回退到 {@link #DEFAULT_PAGE_SIZE}
     * （风格对齐 {@code SyncPullServiceImpl#effectivePageSize}）。
     *
     * @param requestedPageSize  调用方请求的 {@code pageSize}，可为空
     * @param configuredPageSize 该数据域配置的 {@code pageSize}
     * @return 有效 {@code pageSize}
     */
    private int effectivePageSize(Integer requestedPageSize, Integer configuredPageSize) {
        if (requestedPageSize != null && requestedPageSize > 0) {
            return requestedPageSize;
        }
        return configuredPageSize != null && configuredPageSize > 0 ? configuredPageSize : DEFAULT_PAGE_SIZE;
    }

    /**
     * 构造一个 {@code records} 为空的响应，供同步总开关关闭/数据域未开通场景使用。
     *
     * @param entityType  数据类型
     * @param sinceSeq    本次请求实际使用的起始游标
     * @param nextSeq     响应的 {@code nextSeq}
     * @param hasMore     响应的 {@code hasMore}
     * @param configEpoch 响应的 {@code configEpoch}
     * @return 空结果响应
     */
    private SyncChangesPageVO emptyResult(String entityType, long sinceSeq, long nextSeq, boolean hasMore,
            String configEpoch) {
        return SyncChangesPageVO.builder().entityType(entityType).sinceSeq(String.valueOf(sinceSeq))
                .nextSeq(String.valueOf(nextSeq)).hasMore(hasMore).configEpoch(configEpoch).records(List.of())
                .build();
    }

    /**
     * 把一条底层变更流水实体转换为对外返回的变更指针，BIGINT 字段均转换为十进制字符串
     * （design.md Decision 11）。
     *
     * @param entity 底层变更流水实体
     * @return 变更指针
     */
    private SyncChangePointerVO toPointer(AppDataChangeLogEntity entity) {
        return SyncChangePointerVO.builder().eventId(String.valueOf(entity.getEventId()))
                .entityType(entity.getEntityType()).entityId(String.valueOf(entity.getEntityId()))
                .operationType(entity.getOperationType()).entityVersion(String.valueOf(entity.getEntityVersion()))
                .changeSeq(String.valueOf(entity.getChangeSeq())).changeTime(entity.getChangeTime()).build();
    }
}
