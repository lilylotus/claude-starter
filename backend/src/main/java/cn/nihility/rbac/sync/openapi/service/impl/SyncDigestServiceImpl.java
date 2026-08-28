package cn.nihility.rbac.sync.openapi.service.impl;

import cn.nihility.rbac.app.entity.AppConfigEntity;
import cn.nihility.rbac.app.mapper.AppConfigMapper;
import cn.nihility.rbac.app.sync.constant.SyncDomain;
import cn.nihility.rbac.app.sync.entity.AppSyncDomainConfigEntity;
import cn.nihility.rbac.app.sync.mapper.AppSyncDomainConfigMapper;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.sync.changelog.mapper.AppDataChangeLogMapper;
import cn.nihility.rbac.sync.openapi.OpenApiCallerContext;
import cn.nihility.rbac.sync.openapi.dto.SyncDigestVO;
import cn.nihility.rbac.sync.openapi.service.SyncDigestService;
import cn.nihility.rbac.sync.openapi.support.SyncDigestCanonicalCodec;
import cn.nihility.rbac.sync.scope.AppSyncOrgScopeResolver;
import cn.nihility.rbac.sync.transform.SyncBizPageQueryResolver;
import cn.nihility.rbac.sync.transform.SyncBizPageRow;
import cn.nihility.rbac.sync.transform.SyncRecordAssembler;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 对账摘要业务逻辑实现（app-sync-changelog-pull change design.md Decision 10）：复用
 * {@code /pull} 现有的"调用方当前可见范围"解析与字段映射逻辑，按 {@code bizId} 升序流式
 * 扫描全部可见记录累加计算 SHA-256 摘要，不整表加载进内存。
 */
@Service
@RequiredArgsConstructor
public class SyncDigestServiceImpl implements SyncDigestService {

    /** 摘要算法名。 */
    private static final String ALGORITHM = "SHA-256";

    /**
     * 摘要规则版本号：标记"按 bizId 升序 + 字段映射后完整输出记录 + canonical JSON + 长度
     * 前缀分隔"这套规则本身的版本，规则调整时升级，新旧版本摘要不可直接比较。
     */
    private static final String DIGEST_VERSION = "v1";

    /** 流式扫描每批查询的记录数。 */
    private static final int DIGEST_BATCH_SIZE = 200;

    /** 应用对外接口配置数据访问接口。 */
    private final AppConfigMapper appConfigMapper;

    /** 应用同步数据域配置数据访问接口。 */
    private final AppSyncDomainConfigMapper appSyncDomainConfigMapper;

    /** 应用同步组织范围解析业务逻辑组件。 */
    private final AppSyncOrgScopeResolver appSyncOrgScopeResolver;

    /** 业务表当前数据分页查询解析器，复用其游标式批量查询能力。 */
    private final SyncBizPageQueryResolver syncBizPageQueryResolver;

    /** 记录组装器，与 {@code /pull} 共用同一份"字段映射后的完整输出记录"组装逻辑。 */
    private final SyncRecordAssembler syncRecordAssembler;

    /** 全局应用数据变更流水数据访问接口，用于查询当前最大 {@code changeSeq}。 */
    private final AppDataChangeLogMapper appDataChangeLogMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    public SyncDigestVO digest(String entityType) {
        assertValidEntityType(entityType);
        Long appRefId = OpenApiCallerContext.getAppRefId();

        AppConfigEntity appConfig = appConfigMapper.selectOne(
                new LambdaQueryWrapper<AppConfigEntity>().eq(AppConfigEntity::getAppRefId, appRefId));
        String configEpoch = String.valueOf(
                appConfig != null && appConfig.getConfigEpoch() != null ? appConfig.getConfigEpoch() : 0L);

        boolean canQuery = appConfig != null && Boolean.TRUE.equals(appConfig.getSyncMasterEnabled());
        if (canQuery) {
            AppSyncDomainConfigEntity domainConfig =
                    appSyncDomainConfigMapper.selectOne(new LambdaQueryWrapper<AppSyncDomainConfigEntity>()
                            .eq(AppSyncDomainConfigEntity::getAppRefId, appRefId)
                            .eq(AppSyncDomainConfigEntity::getSyncDomain, entityType));
            canQuery = domainConfig != null && Boolean.TRUE.equals(domainConfig.getSyncEnabled());
        }

        long recordCount = 0;
        String digestValue;
        try {
            MessageDigest messageDigest = MessageDigest.getInstance(ALGORITHM);
            if (canQuery) {
                Set<Long> allowedOrgIds = resolveAllowedOrgIds(appRefId, entityType);
                recordCount = accumulate(appRefId, entityType, allowedOrgIds, messageDigest);
            }
            digestValue = HexFormat.of().formatHex(messageDigest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("摘要算法不可用：" + ALGORITHM, e);
        }

        Long currentMaxSeq = appDataChangeLogMapper.selectMaxChangeSeq();
        String currentMaxSeqStr = currentMaxSeq != null ? String.valueOf(currentMaxSeq) : "0";

        return SyncDigestVO.builder().entityType(entityType).algorithm(ALGORITHM).digestVersion(DIGEST_VERSION)
                .recordCount(recordCount).digestValue(digestValue).currentMaxSeq(currentMaxSeqStr)
                .configEpoch(configEpoch).build();
    }

    /**
     * 按 {@code bizId} 升序游标式批量扫描全部可见记录，逐条 canonical 编码、长度前缀分隔后
     * 喂给 {@link MessageDigest}，不整表加载进内存（design.md Decision 10）。
     *
     * @param appRefId      应用 id
     * @param entityType    数据类型
     * @param allowedOrgIds 组织范围过滤下推的允许组织 id 全集，{@code null} 表示不限制
     * @param messageDigest 累加计算摘要的 {@link MessageDigest} 实例
     * @return 参与摘要计算的记录条数
     */
    private long accumulate(Long appRefId, String entityType, Set<Long> allowedOrgIds,
            MessageDigest messageDigest) {
        long recordCount = 0;
        Long lastId = null;
        while (true) {
            List<SyncBizPageRow> batch =
                    syncBizPageQueryResolver.queryDigestBatch(entityType, lastId, DIGEST_BATCH_SIZE, allowedOrgIds);
            if (batch.isEmpty()) {
                break;
            }
            for (SyncBizPageRow row : batch) {
                Map<String, Object> record = syncRecordAssembler.assemble(appRefId, entityType, row);
                byte[] encoded = SyncDigestCanonicalCodec.encode(record);
                messageDigest.update(lengthPrefix(encoded.length));
                messageDigest.update(encoded);
                recordCount++;
            }
            lastId = batch.get(batch.size() - 1).getId();
            if (batch.size() < DIGEST_BATCH_SIZE) {
                break;
            }
        }
        return recordCount;
    }

    /**
     * 构造 4 字节大端序长度前缀，用于在摘要计算时分隔相邻两条记录的 canonical JSON 编码，
     * 避免"记录 A 结尾 + 记录 B 开头"与"记录 A'结尾 + 记录 B'开头"因字节拼接巧合产生相同
     * 摘要（design.md Decision 10）。
     *
     * @param length 记录 canonical JSON 编码后的字节长度
     * @return 4 字节大端序长度前缀
     */
    private byte[] lengthPrefix(int length) {
        return new byte[] {(byte) (length >>> 24), (byte) (length >>> 16), (byte) (length >>> 8), (byte) length};
    }

    /**
     * 解析组织范围过滤下推所需的允许组织 id 全集：仅 ORG/USER/POSITION 三个数据域生效，
     * APP/ROLE/DICT 恒返回 {@code null}（不限制）。
     *
     * @param appRefId   应用 id
     * @param entityType 数据类型
     * @return 允许组织 id 全集，{@code null} 表示不限制
     */
    private Set<Long> resolveAllowedOrgIds(Long appRefId, String entityType) {
        if (!SyncDomain.ORG_SCOPE_DOMAINS.contains(entityType)) {
            return null;
        }
        return appSyncOrgScopeResolver.resolveAllowedOrgIds(appRefId, entityType).orElse(null);
    }

    /**
     * 校验 {@code entityType} 是否是对账摘要接口支持的合法取值（ORG/USER/POSITION/APP/ROLE/
     * DICT，复用 {@code /pull} 现有的数据域覆盖范围，比 {@code /changes} 多一个 DICT）。
     *
     * @param entityType 数据类型
     */
    private void assertValidEntityType(String entityType) {
        if (!SyncDomain.SYNC_PULL_DOMAINS.contains(entityType)) {
            throw new BusinessException("非法的数据类型：" + entityType);
        }
    }
}
