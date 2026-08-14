package cn.nihility.rbac.identity.upstream.support;

import cn.nihility.rbac.app.config.AppSecretProperties;
import cn.nihility.rbac.auth.context.CurrentUserContext;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.common.util.JacksonUtils;
import cn.nihility.rbac.common.util.Sm4JdkUtils;
import cn.nihility.rbac.identity.upstream.constant.UpstreamDataType;
import cn.nihility.rbac.identity.upstream.constant.UpstreamSyncRecordDetailStatus;
import cn.nihility.rbac.identity.upstream.constant.UpstreamSyncStatus;
import cn.nihility.rbac.identity.upstream.constant.UpstreamSyncType;
import cn.nihility.rbac.identity.upstream.dto.UpstreamFieldMappingRow;
import cn.nihility.rbac.identity.upstream.entity.UpstreamDomainConfigEntity;
import cn.nihility.rbac.identity.upstream.entity.UpstreamSourceEntity;
import cn.nihility.rbac.identity.upstream.entity.UpstreamSyncRecordDetailEntity;
import cn.nihility.rbac.identity.upstream.entity.UpstreamSyncRecordEntity;
import cn.nihility.rbac.identity.upstream.mapper.UpstreamDomainConfigMapper;
import cn.nihility.rbac.identity.upstream.mapper.UpstreamFieldMappingMapper;
import cn.nihility.rbac.identity.upstream.mapper.UpstreamSourceMapper;
import cn.nihility.rbac.identity.upstream.mapper.UpstreamSyncRecordDetailMapper;
import cn.nihility.rbac.identity.upstream.mapper.UpstreamSyncRecordMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 上游数据同步执行引擎（design.md Decision 3）：按 {@link UpstreamDataType#SYNC_ORDER}
 * （组织→用户→任职）固定顺序处理数据源下已启用的数据域，每个数据域"取数→字段映射转换→
 * 逐行落库→汇总写入一条同步记录（含每行明细）"。轮询 tick 与"立即同步一次"手动触发
 * 共用同一个入口，只是 {@code triggerType} 不同（design.md Decision 2）。取数成功但本轮
 * 结果为空时不写执行记录，避免高频轮询产生大量"空跑"记录（upstream-sync-record-improvements
 * change design.md Decision 1）。
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

    /** 保留哨兵用户 id，代表"系统/后台同步"这一操作人（fix-upstream-sync-scheduled-operator-context
     * change design.md Decision 1）：{@code tab_admin}/{@code tab_user} 用
     * {@code IdType.AUTO} 自增主键，从 1 开始，0 永远不会是真实用户 id；且
     * {@code tab_admin_org_scope} 里查不到 id=0 的任何管辖范围行，
     * {@code OrgScopeServiceImpl#resolveAllowedOrgIds} 会将其解释为"不受限"，不会误伤
     * 组织范围校验。定时轮询触发同步的线程不在任何 HTTP 请求上下文中，
     * {@link CurrentUserContext} 恒为空，{@code OrgService}/{@code UserService}/
     * {@code PositionService} 的 create/update 却会调用
     * {@code CurrentOperatorService#resolveUserId} 填充 {@code createBy}/{@code updateBy}，
     * 脱离登录上下文时按其既有设计直接抛异常——本引擎在调用它们之前，把当前线程临时标记为
     * 这个保留哨兵 id，使其行为与"已登录"等价，而不改动 {@code CurrentOperatorService}
     * 本身"不做静默兜底"的既有契约。 */
    private static final Long SYSTEM_USER_ID = 0L;

    /** 上游数据源数据访问接口。 */
    private final UpstreamSourceMapper upstreamSourceMapper;

    /** 上游数据源数据域配置数据访问接口。 */
    private final UpstreamDomainConfigMapper upstreamDomainConfigMapper;

    /** 上游字段映射数据访问接口。 */
    private final UpstreamFieldMappingMapper upstreamFieldMappingMapper;

    /** 上游数据同步执行记录数据访问接口。 */
    private final UpstreamSyncRecordMapper upstreamSyncRecordMapper;

    /** 上游数据同步执行记录明细数据访问接口。 */
    private final UpstreamSyncRecordDetailMapper upstreamSyncRecordDetailMapper;

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
     * 执行一次数据源同步：按组织→用户→任职固定顺序处理已启用的数据域。方法体最外层把
     * {@link CurrentUserContext} 临时标记为保留哨兵用户 id {@link #SYSTEM_USER_ID}
     * （不区分定时/手动触发，统一处理，见 fix-upstream-sync-scheduled-operator-context
     * change design.md Decision 3），使下游 {@code OrgService}/{@code UserService}/
     * {@code PositionService} 的 create/update 在定时轮询这类无 HTTP 请求上下文的后台
     * 线程上也能正常解析出一个"操作人"；处理结束后（无论成功/异常）恢复进入前的原值——
     * 已认证 HTTP 请求线程（手动触发）恢复为真实登录用户，避免影响调用方后续逻辑；
     * 后台调度线程（定时触发）恢复为空，避免线程池复用时把该标记残留给下一次不相关的
     * 调度（design.md Decision 2）。
     *
     * @param sourceId    上游数据源 id
     * @param triggerType 触发方式：SCHEDULE/MANUAL
     */
    public void syncSource(Long sourceId, String triggerType) {
        Long previousUserId = CurrentUserContext.getUserId();
        CurrentUserContext.setUserId(SYSTEM_USER_ID);
        try {
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
        } finally {
            if (previousUserId != null) {
                CurrentUserContext.setUserId(previousUserId);
            } else {
                CurrentUserContext.clear();
            }
        }
    }

    /**
     * 同步一个数据域：先校验字段映射中是否配置了至少一个"主键标识"字段，未配置时直接
     * 判定失败并返回，不发起取数请求（design.md Decision 4：兜底本次改动上线前保存、
     * 默认零主键字段的历史配置，避免用空匹配条件误伤本地数据）；配置了主键字段时才
     * 取数→字段映射转换→逐行落库→汇总写入一条同步记录。取数阶段异常直接记一条
     * {@code FAILED} 记录，不影响其余数据域继续处理（design.md Decision 5 Scenario：
     * 取数阶段异常记录为 FAILED）。取数成功但本轮结果为空时，只更新"上次同步时间"、
     * 不写执行记录，避免高频轮询场景下产生大量无信息量的"空跑"记录
     * （upstream-sync-record-improvements change design.md Decision 1）。
     *
     * @param source       上游数据源
     * @param domainConfig 数据域配置
     * @param triggerType  触发方式
     */
    private void syncDomain(UpstreamSourceEntity source, UpstreamDomainConfigEntity domainConfig,
            String triggerType) {
        LocalDateTime startTime = LocalDateTime.now();
        List<UpstreamFieldMappingRow> mappings = upstreamFieldMappingMapper.selectBySourceIdAndDataType(
                source.getId(), domainConfig.getDataType());
        List<String> primaryKeyFieldCodes = mappings.stream()
                .filter(mapping -> Boolean.TRUE.equals(mapping.getIsPrimaryKey()))
                .map(UpstreamFieldMappingRow::getFieldCode)
                .collect(Collectors.toList());
        if (primaryKeyFieldCodes.isEmpty()) {
            log.warn("上游数据源[{}]数据域[{}]未配置主键字段，跳过本次同步", source.getId(), domainConfig.getDataType());
            saveSyncRecord(source.getId(), domainConfig.getDataType(), triggerType, startTime, LocalDateTime.now(),
                    UpstreamSyncStatus.FAILED, 0, 0, 0,
                    "该数据域尚未在字段映射中标记主键字段，无法判断新增/更新，请先在字段映射里标记至少一个主键字段后再同步",
                    List.of());
            return;
        }

        List<Map<String, Object>> rawRows;
        try {
            rawRows = fetchRawRows(source, domainConfig);
        } catch (Exception e) {
            log.warn("上游数据源[{}]数据域[{}]取数失败", source.getId(), domainConfig.getDataType(), e);
            saveSyncRecord(source.getId(), domainConfig.getDataType(), triggerType, startTime, LocalDateTime.now(),
                    UpstreamSyncStatus.FAILED, 0, 0, 0, truncate("取数失败：" + e.getMessage()), List.of());
            return;
        }

        if (rawRows.isEmpty()) {
            domainConfig.setLastSyncTime(LocalDateTime.now());
            upstreamDomainConfigMapper.updateById(domainConfig);
            return;
        }

        int total = rawRows.size();
        int success = 0;
        List<String> failMessages = new ArrayList<>();
        List<UpstreamSyncRecordDetailEntity> details = new ArrayList<>(total);
        int rowNo = 1;
        for (Map<String, Object> rawRow : rawRows) {
            String rowData = JacksonUtils.toJson(rawRow);
            try {
                Map<String, Object> transformedRow = upstreamFieldMappingTransformer.transform(mappings, rawRow);
                upstreamRowUpserter.upsertRow(domainConfig.getDataType(), transformedRow, rawRow,
                        primaryKeyFieldCodes);
                success++;
                details.add(UpstreamSyncRecordDetailEntity.builder()
                        .rowNo(rowNo)
                        .rowData(rowData)
                        .status(UpstreamSyncRecordDetailStatus.SUCCESS)
                        .build());
            } catch (Exception e) {
                String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                if (failMessages.size() < FAIL_SUMMARY_MAX_ITEMS) {
                    failMessages.add(message);
                }
                details.add(UpstreamSyncRecordDetailEntity.builder()
                        .rowNo(rowNo)
                        .rowData(rowData)
                        .status(UpstreamSyncRecordDetailStatus.FAILED)
                        .failReason(message)
                        .build());
            }
            rowNo++;
        }
        int fail = total - success;
        String status = resolveStatus(total, success);
        saveSyncRecord(source.getId(), domainConfig.getDataType(), triggerType, startTime, LocalDateTime.now(),
                status, total, success, fail, truncate(String.join("；", failMessages)), details);

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
     * 写入一条同步执行记录，随后把该次执行收集到的行明细逐行写入明细表（沿用
     * {@code UpstreamFieldMappingServiceImpl.replace} 已有的逐行 {@code insert} 风格，
     * 不引入批量插入框架，见 upstream-sync-record-improvements change design.md
     * Decision 3）。明细实体只在调用方收集时填充了 {@code rowNo}/{@code rowData}/
     * {@code status}/{@code failReason}，{@code syncRecordId}/{@code sourceId}/审计字段
     * 由本方法统一补全。
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
     * @param details     本次执行的行明细列表，取数前置校验/取数异常两类场景传空列表
     */
    private void saveSyncRecord(Long sourceId, String dataType, String triggerType, LocalDateTime startTime,
            LocalDateTime endTime, String status, int total, int success, int fail, String failSummary,
            List<UpstreamSyncRecordDetailEntity> details) {
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

        for (UpstreamSyncRecordDetailEntity detail : details) {
            detail.setSyncRecordId(record.getId());
            detail.setSourceId(sourceId);
            detail.setCreateBy(SYSTEM_OPERATOR);
            detail.setCreateTime(now);
            detail.setUpdateBy(SYSTEM_OPERATOR);
            detail.setUpdateTime(now);
            upstreamSyncRecordDetailMapper.insert(detail);
        }
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
