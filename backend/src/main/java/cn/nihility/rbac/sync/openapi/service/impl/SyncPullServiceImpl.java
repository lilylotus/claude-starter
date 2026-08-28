package cn.nihility.rbac.sync.openapi.service.impl;

import cn.nihility.rbac.app.entity.AppConfigEntity;
import cn.nihility.rbac.app.mapper.AppConfigMapper;
import cn.nihility.rbac.app.sync.constant.SyncDomain;
import cn.nihility.rbac.app.sync.entity.AppSyncDomainConfigEntity;
import cn.nihility.rbac.app.sync.mapper.AppSyncDomainConfigMapper;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.sync.openapi.OpenApiCallerContext;
import cn.nihility.rbac.sync.openapi.dto.SyncPullPageVO;
import cn.nihility.rbac.sync.openapi.dto.SyncPullRequest;
import cn.nihility.rbac.sync.openapi.service.SyncPullService;
import cn.nihility.rbac.sync.pull.record.service.AppPullRecordService;
import cn.nihility.rbac.sync.scope.AppSyncOrgScopeResolver;
import cn.nihility.rbac.sync.transform.SyncBizPageQuery;
import cn.nihility.rbac.sync.transform.SyncBizPageQueryResolver;
import cn.nihility.rbac.sync.transform.SyncBizPageRow;
import cn.nihility.rbac.sync.transform.SyncRecordAssembler;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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

    /** 未指定 {@code pageSize}、且数据域配置的 {@code pageSize} 也异常缺失时兜底使用的分页大小。 */
    private static final int DEFAULT_PAGE_SIZE = 20;

    /** 应用同步数据域配置数据访问接口。 */
    private final AppSyncDomainConfigMapper appSyncDomainConfigMapper;

    /**
     * 应用对外接口配置数据访问接口，用于判断应用同步总开关是否开启
     * （app-sync-master-switch change design.md Decision 2）。
     */
    private final AppConfigMapper appConfigMapper;

    /** 应用同步组织范围解析业务逻辑组件，供 ORG/USER/POSITION 三个数据域下推组织范围过滤。 */
    private final AppSyncOrgScopeResolver appSyncOrgScopeResolver;

    /** 业务表当前数据分页查询解析器。 */
    private final SyncBizPageQueryResolver syncBizPageQueryResolver;

    /** 记录组装器，与对账摘要接口共用同一份"字段映射后的完整输出记录"组装逻辑。 */
    private final SyncRecordAssembler syncRecordAssembler;

    /** 拉取日志写入业务逻辑接口，记录外部应用调用拉取接口的请求，仅用于问题排查/展示。 */
    private final AppPullRecordService appPullRecordService;

    /**
     * {@inheritDoc}
     */
    @Override
    public SyncPullPageVO pull(SyncPullRequest request) {
        assertValidDataType(request.getDataType());
        Long appRefId = OpenApiCallerContext.getAppRefId();
        String dataType = request.getDataType();
        int page = request.getPage() != null && request.getPage() > 0 ? request.getPage() : 1;

        AppSyncDomainConfigEntity domainConfig = null;
        boolean canQuery = isSyncMasterEnabled(appRefId);
        if (canQuery) {
            domainConfig = findDomainConfig(appRefId, dataType);
            canQuery = domainConfig != null && Boolean.TRUE.equals(domainConfig.getSyncEnabled());
        }
        // page/pageSize 无论本次是否实际查询都要计算出"实际使用的值"，供响应顶层回显
        // （app-sync-drop-changelog change design.md Decision 2 修订版）。
        int effectivePageSize =
                effectivePageSize(request.getPageSize(), domainConfig != null ? domainConfig.getPageSize() : null);

        List<Map<String, Object>> records = List.of();
        // 排序后最后一条查询结果行的 updateTime 即本页最大更新时间，rows 为空时保持 null
        // （app-sync-drop-changelog change design.md Decision 9，四次实现后修正）。
        LocalDateTime latestUpdateTime = null;
        if (canQuery) {
            SyncBizPageQuery query = SyncBizPageQuery.builder()
                    .page(page)
                    .pageSize(effectivePageSize)
                    .updateTimeFrom(request.getUpdateTimeFrom())
                    .updateTimeTo(request.getUpdateTimeTo())
                    .ids(request.getIds())
                    .codes(request.getCodes())
                    .mobile(request.getMobile())
                    .allowedOrgIds(resolveAllowedOrgIds(appRefId, dataType))
                    .build();
            List<SyncBizPageRow> rows = syncBizPageQueryResolver.query(dataType, query);
            records = rows.stream().map(row -> syncRecordAssembler.assemble(appRefId, dataType, row)).toList();
            if (!rows.isEmpty()) {
                latestUpdateTime = rows.get(rows.size() - 1).getUpdateTime();
            }
        }

        recordPull(appRefId, dataType, buildRequestSummary(request, page, effectivePageSize), records.size());
        return SyncPullPageVO.builder().dataType(dataType).page(page).pageSize(effectivePageSize)
                .dataSize(records.size()).latestUpdateTime(latestUpdateTime).records(records).build();
    }

    /**
     * 写入一条拉取日志，写入异常只记 WARN 日志，不影响本次拉取请求的响应结果
     * （add-app-sync-notify-pull-logs change spec"日志写入失败不影响拉取主流程"场景）。
     *
     * @param appRefId       调用方应用 id
     * @param dataType       请求的数据类型
     * @param requestSummary 请求参数摘要
     * @param resultCount    本次返回的记录条数
     */
    private void recordPull(Long appRefId, String dataType, String requestSummary, int resultCount) {
        try {
            appPullRecordService.record(appRefId, dataType, requestSummary, resultCount);
        } catch (Exception e) {
            log.warn("写入拉取日志失败：appRefId={}, dataType={}", appRefId, dataType, e);
        }
    }

    /**
     * 校验请求的数据类型是否是同步能力支持的合法取值（ORG/USER/POSITION/APP/ROLE/DICT），
     * 非法取值直接拒绝（区别于"合法但未开通"场景的返回空结果）（app-sync-drop-changelog
     * change design.md Decision 7，三次实现后修正：DICT 纳入合法拉取数据类型）。
     *
     * @param dataType 数据类型
     */
    private void assertValidDataType(String dataType) {
        if (!SyncDomain.SYNC_PULL_DOMAINS.contains(dataType)) {
            throw new BusinessException("非法的数据类型：" + dataType);
        }
    }

    /**
     * 判断给定应用当前同步总开关是否开启。应用对外接口配置理论上因一对一不变式必然存在，
     * 查不到时按防御性写法保守返回 {@code false}（视同关闭，不放行拉取）（app-sync-master-switch
     * change design.md Decision 2）。
     *
     * @param appRefId 应用 id
     * @return 同步总开关是否开启
     */
    private boolean isSyncMasterEnabled(Long appRefId) {
        AppConfigEntity config = appConfigMapper.selectOne(
                new LambdaQueryWrapper<AppConfigEntity>().eq(AppConfigEntity::getAppRefId, appRefId));
        return config != null && Boolean.TRUE.equals(config.getSyncMasterEnabled());
    }

    /**
     * 查询给定应用给定数据域的同步配置。
     *
     * @param appRefId 应用 id
     * @param dataType 数据类型
     * @return 数据域配置，查不到时返回 {@code null}
     */
    private AppSyncDomainConfigEntity findDomainConfig(Long appRefId, String dataType) {
        return appSyncDomainConfigMapper.selectOne(new LambdaQueryWrapper<AppSyncDomainConfigEntity>()
                .eq(AppSyncDomainConfigEntity::getAppRefId, appRefId)
                .eq(AppSyncDomainConfigEntity::getSyncDomain, dataType));
    }

    /**
     * 解析组织范围过滤下推所需的允许组织 id 全集：仅 ORG/USER/POSITION 三个数据域生效，
     * APP/ROLE 恒返回 {@code null}（不限制）（app-sync-drop-changelog change design.md
     * Decision 4）。
     *
     * @param appRefId 应用 id
     * @param dataType 数据类型
     * @return 允许组织 id 全集，{@code null} 表示不限制
     */
    private Set<Long> resolveAllowedOrgIds(Long appRefId, String dataType) {
        if (!SyncDomain.ORG_SCOPE_DOMAINS.contains(dataType)) {
            return null;
        }
        return appSyncOrgScopeResolver.resolveAllowedOrgIds(appRefId, dataType).orElse(null);
    }

    /**
     * 计算本次拉取的有效 {@code pageSize}：调用方显式指定且为正数时优先，否则回退到该数据域
     * 配置的 {@code pageSize}，配置值也异常缺失时再回退到 {@link #DEFAULT_PAGE_SIZE}
     * （app-sync-drop-changelog change design.md Decision 5）。
     *
     * @param requestedPageSize 调用方请求的 {@code pageSize}，可为空
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
     * 构造拉取日志的请求参数摘要：页码、每页大小，以及本次实际传入的过滤条件概要
     * （app-sync-drop-changelog change tasks.md 6.3）。
     *
     * @param request           原始请求参数
     * @param page              本次实际使用的页码
     * @param effectivePageSize 本次实际使用的每页大小
     * @return 请求参数摘要文本
     */
    private String buildRequestSummary(SyncPullRequest request, int page, int effectivePageSize) {
        StringBuilder summary = new StringBuilder();
        summary.append("page=").append(page);
        summary.append(", pageSize=").append(effectivePageSize);
        if (request.getUpdateTimeFrom() != null) {
            summary.append(", updateTimeFrom=").append(request.getUpdateTimeFrom());
        }
        if (request.getUpdateTimeTo() != null) {
            summary.append(", updateTimeTo=").append(request.getUpdateTimeTo());
        }
        if (request.getIds() != null && !request.getIds().isEmpty()) {
            summary.append(", ids=").append(request.getIds().size()).append("个");
        }
        if (request.getCodes() != null && !request.getCodes().isEmpty()) {
            summary.append(", codes=").append(request.getCodes().size()).append("个");
        }
        if (StringUtils.hasText(request.getMobile())) {
            summary.append(", mobile=").append(request.getMobile());
        }
        return summary.toString();
    }
}
