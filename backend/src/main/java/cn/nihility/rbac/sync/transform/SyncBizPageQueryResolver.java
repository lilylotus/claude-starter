package cn.nihility.rbac.sync.transform;

import cn.nihility.rbac.app.entity.AppEntity;
import cn.nihility.rbac.app.mapper.AppMapper;
import cn.nihility.rbac.app.sync.constant.SyncDomain;
import cn.nihility.rbac.dict.entity.DictItemEntity;
import cn.nihility.rbac.dict.entity.DictTypeEntity;
import cn.nihility.rbac.dict.mapper.DictItemMapper;
import cn.nihility.rbac.dict.mapper.DictTypeMapper;
import cn.nihility.rbac.org.entity.OrgEntity;
import cn.nihility.rbac.org.mapper.OrgMapper;
import cn.nihility.rbac.role.entity.RoleEntity;
import cn.nihility.rbac.role.mapper.RoleMapper;
import cn.nihility.rbac.sync.event.DomainSnapshotSupport;
import cn.nihility.rbac.user.entity.UserEntity;
import cn.nihility.rbac.user.entity.UserPositionEntity;
import cn.nihility.rbac.user.mapper.UserMapper;
import cn.nihility.rbac.user.mapper.UserPositionMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 业务表当前数据分页查询解析器（app-sync-drop-changelog change design.md Decision 2/3/4/5）：
 * 按 {@code dataType} 分发到组织/用户/任职/应用/角色/字典对应业务表的分页查询，不过滤
 * {@code status}（停用/已删除记录原样返回），组织范围过滤下推到 SQL {@code WHERE} 条件
 * （见各自 Mapper XML），不在 Java 侧查出全量再过滤。与 {@link BizSnapshotResolver}
 * （单 id 现查）同级、职责相邻但功能不同：本组件面向"条件化分页查询"。任职数据域批量回填关联
 * 用户编码（{@code userCode}）与关联组织编码（{@code orgCode}）、字典数据域批量回填所属字典
 * 类型编码（{@code dictTypeCode}），均一次批量查询，不逐行单独查询（design.md Decision 7/8，
 * 五次实现后修正）。
 */
@Component
@RequiredArgsConstructor
public class SyncBizPageQueryResolver {

    /** 组织业务表数据访问接口。 */
    private final OrgMapper orgMapper;

    /** 用户业务表数据访问接口。 */
    private final UserMapper userMapper;

    /** 用户任职记录业务表数据访问接口。 */
    private final UserPositionMapper userPositionMapper;

    /** 应用业务表数据访问接口。 */
    private final AppMapper appMapper;

    /** 角色业务表数据访问接口。 */
    private final RoleMapper roleMapper;

    /** 字典项业务表数据访问接口。 */
    private final DictItemMapper dictItemMapper;

    /** 字典类型业务表数据访问接口，供批量回填字典项所属字典类型编码使用。 */
    private final DictTypeMapper dictTypeMapper;

    /**
     * 按数据域分页查询业务表当前数据。
     *
     * @param dataType 数据域，取值范围为 {@link SyncDomain#SYNC_PULL_DOMAINS}，由调用方保证
     *                 合法，{@code default} 分支仅作穷尽性兜底，不会在正常业务路径上被触发
     * @param query    分页查询参数
     * @return 查询结果行列表，按更新时间升序、id 升序排列
     */
    public List<SyncBizPageRow> query(String dataType, SyncBizPageQuery query) {
        int offset = (query.getPage() - 1) * query.getPageSize();
        return switch (dataType) {
            case SyncDomain.ORG -> orgMapper.selectSyncPullPage(offset, query.getPageSize(),
                    query.getUpdateTimeFrom(), query.getUpdateTimeTo(), query.getIds(), query.getCodes(),
                    query.getAllowedOrgIds()).stream().map(this::toRow).toList();
            case SyncDomain.USER -> userMapper.selectSyncPullPage(offset, query.getPageSize(),
                    query.getUpdateTimeFrom(), query.getUpdateTimeTo(), query.getIds(), query.getCodes(),
                    query.getMobile(), query.getAllowedOrgIds()).stream().map(this::toRow).toList();
            case SyncDomain.POSITION -> {
                List<UserPositionEntity> entities = userPositionMapper.selectSyncPullPage(offset, query.getPageSize(),
                        query.getUpdateTimeFrom(), query.getUpdateTimeTo(), query.getIds(),
                        query.getAllowedOrgIds());
                Map<Long, String> userCodeById = resolveUserCodes(entities);
                Map<Long, String> orgCodeById = resolveOrgCodes(entities);
                yield entities.stream().map(entity -> toRow(entity, userCodeById, orgCodeById)).toList();
            }
            case SyncDomain.APP -> appMapper.selectSyncPullPage(offset, query.getPageSize(),
                    query.getUpdateTimeFrom(), query.getUpdateTimeTo(), query.getIds(), query.getCodes())
                    .stream().map(this::toRow).toList();
            case SyncDomain.ROLE -> roleMapper.selectSyncPullPage(offset, query.getPageSize(),
                    query.getUpdateTimeFrom(), query.getUpdateTimeTo(), query.getIds(), query.getCodes())
                    .stream().map(this::toRow).toList();
            case SyncDomain.DICT -> {
                List<DictItemEntity> entities = dictItemMapper.selectSyncPullPage(offset, query.getPageSize(),
                        query.getUpdateTimeFrom(), query.getUpdateTimeTo(), query.getIds(), query.getCodes());
                Map<Long, String> dictTypeCodeById = resolveDictTypeCodes(entities);
                yield entities.stream().map(entity -> toRow(entity, dictTypeCodeById)).toList();
            }
            default -> List.of();
        };
    }

    /**
     * 按数据域游标式批量查询业务表当前数据，供对账摘要接口流式扫描全部可见记录使用
     * （app-sync-changelog-pull change design.md Decision 10——"流式计算，避免大数据量内存
     * 问题"）：按 {@code id} 升序、{@code id > lastId} 游标翻页（而非 {@code OFFSET}），
     * 保证扫描顺序稳定、不因翻页期间的写入产生重复/漏读；不支持 {@code updateTimeFrom}/
     * {@code updateTimeTo}/{@code ids}/{@code codes}/{@code mobile} 过滤，语义就是"该应用
     * 该数据域当前可见的全部记录"。
     *
     * @param dataType      数据域，取值范围为 {@link SyncDomain#SYNC_PULL_DOMAINS}
     * @param lastId        上一批最后一条记录的 id，{@code null} 表示从头开始
     * @param batchSize     本批最多查询的记录数
     * @param allowedOrgIds 组织范围过滤下推的允许组织 id 全集，仅 ORG/USER/POSITION 使用，
     *                      {@code null} 表示不限制
     * @return 本批查询结果，按 id 升序排列，空列表表示已扫描到末尾
     */
    public List<SyncBizPageRow> queryDigestBatch(String dataType, Long lastId, int batchSize,
            Set<Long> allowedOrgIds) {
        return switch (dataType) {
            case SyncDomain.ORG -> orgMapper.selectDigestBatch(lastId, batchSize, allowedOrgIds).stream()
                    .map(this::toRow).toList();
            case SyncDomain.USER -> userMapper.selectDigestBatch(lastId, batchSize, allowedOrgIds).stream()
                    .map(this::toRow).toList();
            case SyncDomain.POSITION -> {
                List<UserPositionEntity> entities =
                        userPositionMapper.selectDigestBatch(lastId, batchSize, allowedOrgIds);
                Map<Long, String> userCodeById = resolveUserCodes(entities);
                Map<Long, String> orgCodeById = resolveOrgCodes(entities);
                yield entities.stream().map(entity -> toRow(entity, userCodeById, orgCodeById)).toList();
            }
            case SyncDomain.APP -> appMapper.selectDigestBatch(lastId, batchSize).stream().map(this::toRow).toList();
            case SyncDomain.ROLE ->
                    roleMapper.selectDigestBatch(lastId, batchSize).stream().map(this::toRow).toList();
            case SyncDomain.DICT -> {
                List<DictItemEntity> entities = dictItemMapper.selectDigestBatch(lastId, batchSize);
                Map<Long, String> dictTypeCodeById = resolveDictTypeCodes(entities);
                yield entities.stream().map(entity -> toRow(entity, dictTypeCodeById)).toList();
            }
            default -> List.of();
        };
    }

    /**
     * 批量查询本页任职记录关联用户的业务编码，一次 {@code selectByIds} 完成，避免逐行
     * 单独查询导致的 N+1（app-sync-drop-changelog change design.md Decision 8，三次实现后
     * 修正）。
     *
     * @param entities 本页任职记录实体列表
     * @return {@code userId -> code} 映射，本页没有任何有效 {@code userId} 时返回空 Map
     */
    private Map<Long, String> resolveUserCodes(List<UserPositionEntity> entities) {
        Set<Long> userIds = entities.stream().map(UserPositionEntity::getUserId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        // 用可变 HashMap 承接结果（而非 Map.of()），保证对没有 userId 的记录调用
        // Map#get(null) 时安全返回 null 而不是抛出 NullPointerException。
        if (userIds.isEmpty()) {
            return new HashMap<>();
        }
        return userMapper.selectByIds(userIds).stream()
                .collect(Collectors.toMap(UserEntity::getId, UserEntity::getCode, (a, b) -> a, HashMap::new));
    }

    /**
     * 批量查询本页任职记录关联组织的业务编码，一次 {@code selectByIds} 完成，避免逐行
     * 单独查询导致的 N+1（app-sync-drop-changelog change design.md Decision 8，五次实现后
     * 修正）。
     *
     * @param entities 本页任职记录实体列表
     * @return {@code orgId -> code} 映射，本页没有任何有效 {@code orgId} 时返回空 Map
     */
    private Map<Long, String> resolveOrgCodes(List<UserPositionEntity> entities) {
        Set<Long> orgIds = entities.stream().map(UserPositionEntity::getOrgId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        // 用可变 HashMap 承接结果（而非 Map.of()），保证对没有 orgId 的记录调用
        // Map#get(null) 时安全返回 null 而不是抛出 NullPointerException。
        if (orgIds.isEmpty()) {
            return new HashMap<>();
        }
        return orgMapper.selectByIds(orgIds).stream()
                .collect(Collectors.toMap(OrgEntity::getId, OrgEntity::getCode, (a, b) -> a, HashMap::new));
    }

    /**
     * 批量查询本页字典项所属字典类型的编码，一次 {@code selectByIds} 完成，避免逐行单独
     * 查询导致的 N+1（app-sync-drop-changelog change design.md Decision 7，三次实现后修正）。
     *
     * @param entities 本页字典项实体列表
     * @return {@code dictTypeId -> code} 映射，本页没有任何有效 {@code dictTypeId} 时返回空 Map
     */
    private Map<Long, String> resolveDictTypeCodes(List<DictItemEntity> entities) {
        Set<Long> dictTypeIds = entities.stream().map(DictItemEntity::getDictTypeId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        // 用可变 HashMap 承接结果（而非 Map.of()），保证对没有 dictTypeId 的记录调用
        // Map#get(null) 时安全返回 null 而不是抛出 NullPointerException。
        if (dictTypeIds.isEmpty()) {
            return new HashMap<>();
        }
        return dictTypeMapper.selectByIds(dictTypeIds).stream()
                .collect(Collectors.toMap(DictTypeEntity::getId, DictTypeEntity::getCode, (a, b) -> a, HashMap::new));
    }

    /**
     * 组织实体转换为查询结果行。
     *
     * @param entity 组织实体
     * @return 查询结果行
     */
    private SyncBizPageRow toRow(OrgEntity entity) {
        return SyncBizPageRow.builder().id(entity.getId()).code(entity.getCode()).updateTime(entity.getUpdateTime())
                .status(entity.getStatus()).version(entity.getVersion())
                .data(DomainSnapshotSupport.snapshot(entity)).build();
    }

    /**
     * 用户实体转换为查询结果行。
     *
     * @param entity 用户实体
     * @return 查询结果行
     */
    private SyncBizPageRow toRow(UserEntity entity) {
        return SyncBizPageRow.builder().id(entity.getId()).code(entity.getCode()).updateTime(entity.getUpdateTime())
                .status(entity.getStatus()).version(entity.getVersion())
                .data(DomainSnapshotSupport.snapshot(entity)).build();
    }

    /**
     * 任职实体转换为查询结果行，没有业务编码字段，{@code code} 恒为 {@code null}；额外合并
     * 关联用户的业务编码 {@code userCode} 与关联组织的业务编码 {@code orgCode}
     * （design.md Decision 8，五次实现后修正）。
     *
     * @param entity       任职实体
     * @param userCodeById 本页批量查询得到的 {@code userId -> code} 映射
     * @param orgCodeById  本页批量查询得到的 {@code orgId -> code} 映射
     * @return 查询结果行
     */
    private SyncBizPageRow toRow(UserPositionEntity entity, Map<Long, String> userCodeById,
            Map<Long, String> orgCodeById) {
        return SyncBizPageRow.builder().id(entity.getId()).code(null).updateTime(entity.getUpdateTime())
                .status(entity.getStatus()).version(entity.getVersion()).userCode(userCodeById.get(entity.getUserId()))
                .orgCode(orgCodeById.get(entity.getOrgId())).data(DomainSnapshotSupport.snapshot(entity)).build();
    }

    /**
     * 应用实体转换为查询结果行。
     *
     * @param entity 应用实体
     * @return 查询结果行
     */
    private SyncBizPageRow toRow(AppEntity entity) {
        return SyncBizPageRow.builder().id(entity.getId()).code(entity.getCode()).updateTime(entity.getUpdateTime())
                .status(entity.getStatus()).version(entity.getVersion())
                .data(DomainSnapshotSupport.snapshot(entity)).build();
    }

    /**
     * 角色实体转换为查询结果行。
     *
     * @param entity 角色实体
     * @return 查询结果行
     */
    private SyncBizPageRow toRow(RoleEntity entity) {
        return SyncBizPageRow.builder().id(entity.getId()).code(entity.getCode()).updateTime(entity.getUpdateTime())
                .status(entity.getStatus()).version(entity.getVersion())
                .data(DomainSnapshotSupport.snapshot(entity)).build();
    }

    /**
     * 字典项实体转换为查询结果行，{@code code} 取字典项自身编码；额外合并所属字典类型的编码
     * {@code dictTypeCode}（design.md Decision 7，三次实现后修正）。
     *
     * @param entity            字典项实体
     * @param dictTypeCodeById  本页批量查询得到的 {@code dictTypeId -> code} 映射
     * @return 查询结果行
     */
    private SyncBizPageRow toRow(DictItemEntity entity, Map<Long, String> dictTypeCodeById) {
        return SyncBizPageRow.builder().id(entity.getId()).code(entity.getCode()).updateTime(entity.getUpdateTime())
                .status(entity.getStatus()).version(entity.getVersion())
                .dictTypeCode(dictTypeCodeById.get(entity.getDictTypeId()))
                .data(DomainSnapshotSupport.snapshot(entity)).build();
    }
}
