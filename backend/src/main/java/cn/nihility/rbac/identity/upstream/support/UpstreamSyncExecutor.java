package cn.nihility.rbac.identity.upstream.support;

import cn.nihility.rbac.app.config.AppSecretProperties;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.common.util.JacksonUtils;
import cn.nihility.rbac.common.util.Sm4JdkUtils;
import cn.nihility.rbac.identity.upstream.constant.UpstreamDataType;
import cn.nihility.rbac.identity.upstream.constant.UpstreamSyncStatus;
import cn.nihility.rbac.identity.upstream.constant.UpstreamSyncType;
import cn.nihility.rbac.identity.upstream.dto.UpstreamFieldMappingRow;
import cn.nihility.rbac.identity.upstream.entity.UpstreamDomainConfigEntity;
import cn.nihility.rbac.identity.upstream.entity.UpstreamSourceEntity;
import cn.nihility.rbac.identity.upstream.entity.UpstreamSyncRecordEntity;
import cn.nihility.rbac.identity.upstream.mapper.UpstreamDomainConfigMapper;
import cn.nihility.rbac.identity.upstream.mapper.UpstreamFieldMappingMapper;
import cn.nihility.rbac.identity.upstream.mapper.UpstreamSourceMapper;
import cn.nihility.rbac.identity.upstream.mapper.UpstreamSyncRecordMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 上游数据同步执行引擎（design.md Decision 3）：按 {@link UpstreamDataType#SYNC_ORDER}
 * （组织→用户→任职）固定顺序处理数据源下已启用的数据域，每个数据域"取数→字段映射转换→
 * 逐行落库→汇总写入一条同步记录"。轮询 tick 与"立即同步一次"手动触发共用同一个入口，
 * 只是 {@code triggerType} 不同（design.md Decision 2）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UpstreamSyncExecutor {

    /** 失败摘要最多汇总的失败原因条数，避免无限增长。 */
    private static final int FAIL_SUMMARY_MAX_ITEMS = 5;

    /** 失败摘要文本最大长度，对齐 {@code tab_app_notify_record.error_msg} 的量级。 */
    private static final int FAIL_SUMMARY_MAX_LENGTH = 500;

    /** 同步记录审计字段固定填充值：本引擎既可能由定时轮询线程触发，也可能由管理员手动
     * 触发，统一标注为系统写入，不依赖当前线程是否处于已认证的登录会话上下文。 */
    private static final String SYSTEM_OPERATOR = "SYSTEM";

    /** 上游数据源数据访问接口。 */
    private final UpstreamSourceMapper upstreamSourceMapper;

    /** 上游数据源数据域配置数据访问接口。 */
    private final UpstreamDomainConfigMapper upstreamDomainConfigMapper;

    /** 上游字段映射数据访问接口。 */
    private final UpstreamFieldMappingMapper upstreamFieldMappingMapper;

    /** 上游数据同步执行记录数据访问接口。 */
    private final UpstreamSyncRecordMapper upstreamSyncRecordMapper;

    /** 接口方式取数组件。 */
    private final UpstreamHttpFetcher upstreamHttpFetcher;

    /** 数据库表方式取数组件。 */
    private final UpstreamJdbcFetcher upstreamJdbcFetcher;

    /** 字段映射转换执行器。 */
    private final UpstreamFieldMappingTransformer upstreamFieldMappingTransformer;

    /** 单行落库处理器。 */
    private final UpstreamRowUpserter upstreamRowUpserter;

    /** SM4 主密钥配置，解密接口自定义请求头取值、数据库密码。 */
    private final AppSecretProperties appSecretProperties;

    /**
     * 执行一次数据源同步：按组织→用户→任职固定顺序处理已启用的数据域。
     *
     * @param sourceId    上游数据源 id
     * @param triggerType 触发方式：SCHEDULE/MANUAL
     */
    public void syncSource(Long sourceId, String triggerType) {
        UpstreamSourceEntity source = upstreamSourceMapper.selectById(sourceId);
        if (source == null) {
            throw new BusinessException("上游数据源不存在");
        }
        for (String dataType : UpstreamDataType.SYNC_ORDER) {
            UpstreamDomainConfigEntity domainConfig = upstreamDomainConfigMapper.selectOne(
                    new LambdaQueryWrapper<UpstreamDomainConfigEntity>()
                            .eq(UpstreamDomainConfigEntity::getSourceId, sourceId)
                            .eq(UpstreamDomainConfigEntity::getDataType, dataType));
            if (domainConfig == null || !Boolean.TRUE.equals(domainConfig.getEnabled())) {
                continue;
            }
            syncDomain(source, domainConfig, triggerType);
        }
    }

    /**
     * 同步一个数据域：取数→字段映射转换→逐行落库→汇总写入一条同步记录。取数阶段异常
     * 直接记一条 {@code FAILED} 记录，不影响其余数据域继续处理（design.md Decision 5
     * Scenario：取数阶段异常记录为 FAILED）。
     *
     * @param source       上游数据源
     * @param domainConfig 数据域配置
     * @param triggerType  触发方式
     */
    private void syncDomain(UpstreamSourceEntity source, UpstreamDomainConfigEntity domainConfig,
            String triggerType) {
        LocalDateTime startTime = LocalDateTime.now();
        List<Map<String, Object>> rawRows;
        try {
            rawRows = fetchRawRows(source, domainConfig);
        } catch (Exception e) {
            log.warn("上游数据源[{}]数据域[{}]取数失败", source.getId(), domainConfig.getDataType(), e);
            saveSyncRecord(source.getId(), domainConfig.getDataType(), triggerType, startTime, LocalDateTime.now(),
                    UpstreamSyncStatus.FAILED, 0, 0, 0, truncate("取数失败：" + e.getMessage()));
            return;
        }

        List<UpstreamFieldMappingRow> mappings = upstreamFieldMappingMapper.selectBySourceIdAndDataType(
                source.getId(), domainConfig.getDataType());

        int total = rawRows.size();
        int success = 0;
        List<String> failMessages = new ArrayList<>();
        for (Map<String, Object> rawRow : rawRows) {
            try {
                Map<String, Object> transformedRow = upstreamFieldMappingTransformer.transform(mappings, rawRow);
                upstreamRowUpserter.upsertRow(domainConfig.getDataType(), transformedRow, rawRow);
                success++;
            } catch (Exception e) {
                String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                if (failMessages.size() < FAIL_SUMMARY_MAX_ITEMS) {
                    failMessages.add(message);
                }
            }
        }
        int fail = total - success;
        String status = resolveStatus(total, success);
        saveSyncRecord(source.getId(), domainConfig.getDataType(), triggerType, startTime, LocalDateTime.now(),
                status, total, success, fail, truncate(String.join("；", failMessages)));

        domainConfig.setLastSyncTime(LocalDateTime.now());
        upstreamDomainConfigMapper.updateById(domainConfig);
    }

    /**
     * 按数据源同步方式拉取一个数据域的原始数据。
     *
     * @param source       上游数据源
     * @param domainConfig 数据域配置
     * @return 原始行列表
     */
    private List<Map<String, Object>> fetchRawRows(UpstreamSourceEntity source,
            UpstreamDomainConfigEntity domainConfig) {
        if (UpstreamSyncType.API.equals(source.getSyncType())) {
            return upstreamHttpFetcher.fetch(domainConfig.getApiUrl(), domainConfig.getApiMethod(),
                    decryptHeaders(source.getApiAuthHeaders()));
        }
        String password = StringUtils.hasText(source.getDbPassword())
                ? Sm4JdkUtils.decrypt(source.getDbPassword(), appSecretProperties.getSm4Key()) : null;
        return upstreamJdbcFetcher.fetch(source.getDbJdbcUrl(), source.getDbUsername(), password,
                domainConfig.getDbSql());
    }

    /**
     * 解密接口方式数据源落库的自定义请求头。
     *
     * @param apiAuthHeadersJson 加密请求头 JSON 文本，允许为 {@code null}/空
     * @return 解密后的明文请求头 Map
     */
    private Map<String, String> decryptHeaders(String apiAuthHeadersJson) {
        if (!StringUtils.hasText(apiAuthHeadersJson)) {
            return Map.of();
        }
        Map<String, String> encrypted = JacksonUtils.toObj(apiAuthHeadersJson, JacksonUtils.MAP_STRING_TYPE_REFERENCE);
        if (encrypted == null || encrypted.isEmpty()) {
            return Map.of();
        }
        Map<String, String> decrypted = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : encrypted.entrySet()) {
            decrypted.put(entry.getKey(), Sm4JdkUtils.decrypt(entry.getValue(), appSecretProperties.getSm4Key()));
        }
        return decrypted;
    }

    /**
     * 按处理总行数/成功行数判定同步状态：全部成功=SUCCESS（含总行数为 0）、部分失败=PARTIAL、
     * 全部失败=FAILED（design.md Decision 4）。
     *
     * @param total   处理总行数
     * @param success 成功行数
     * @return 执行状态
     */
    private String resolveStatus(int total, int success) {
        if (total == 0 || success == total) {
            return UpstreamSyncStatus.SUCCESS;
        }
        if (success == 0) {
            return UpstreamSyncStatus.FAILED;
        }
        return UpstreamSyncStatus.PARTIAL;
    }

    /**
     * 写入一条同步执行记录。
     *
     * @param sourceId    上游数据源 id
     * @param dataType    数据域
     * @param triggerType 触发方式
     * @param startTime   开始时间
     * @param endTime     结束时间
     * @param status      执行状态
     * @param total       处理总行数
     * @param success     成功行数
     * @param fail        失败行数
     * @param failSummary 失败摘要
     */
    private void saveSyncRecord(Long sourceId, String dataType, String triggerType, LocalDateTime startTime,
            LocalDateTime endTime, String status, int total, int success, int fail, String failSummary) {
        LocalDateTime now = LocalDateTime.now();
        UpstreamSyncRecordEntity record = UpstreamSyncRecordEntity.builder()
                .sourceId(sourceId)
                .dataType(dataType)
                .triggerType(triggerType)
                .startTime(startTime)
                .endTime(endTime)
                .status(status)
                .totalCount(total)
                .successCount(success)
                .failCount(fail)
                .failSummary(StringUtils.hasText(failSummary) ? failSummary : null)
                .createBy(SYSTEM_OPERATOR)
                .createTime(now)
                .updateBy(SYSTEM_OPERATOR)
                .updateTime(now)
                .build();
        upstreamSyncRecordMapper.insert(record);
    }

    /**
     * 把失败摘要截断到 {@link #FAIL_SUMMARY_MAX_LENGTH} 长度，避免无限增长。
     *
     * @param text 原始文本
     * @return 截断后的文本
     */
    private String truncate(String text) {
        if (text == null || text.length() <= FAIL_SUMMARY_MAX_LENGTH) {
            return text;
        }
        return text.substring(0, FAIL_SUMMARY_MAX_LENGTH);
    }
}
