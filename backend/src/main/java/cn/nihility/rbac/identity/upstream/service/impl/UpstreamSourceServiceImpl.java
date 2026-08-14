package cn.nihility.rbac.identity.upstream.service.impl;

import cn.nihility.rbac.app.config.AppSecretProperties;
import cn.nihility.rbac.auth.service.CurrentOperatorService;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.common.util.JacksonUtils;
import cn.nihility.rbac.common.util.Sm4JdkUtils;
import cn.nihility.rbac.identity.upstream.constant.UpstreamIntervalUnit;
import cn.nihility.rbac.identity.upstream.constant.UpstreamScheduleType;
import cn.nihility.rbac.identity.upstream.constant.UpstreamSyncType;
import cn.nihility.rbac.identity.upstream.constant.UpstreamTriggerType;
import cn.nihility.rbac.identity.upstream.dto.UpstreamConnectionConfigRequest;
import cn.nihility.rbac.identity.upstream.dto.UpstreamScheduleConfigRequest;
import cn.nihility.rbac.identity.upstream.dto.UpstreamSourceCreateRequest;
import cn.nihility.rbac.identity.upstream.dto.UpstreamSourceUpdateRequest;
import cn.nihility.rbac.identity.upstream.dto.UpstreamSourceVO;
import cn.nihility.rbac.identity.upstream.entity.UpstreamDomainConfigEntity;
import cn.nihility.rbac.identity.upstream.entity.UpstreamFieldMappingEntity;
import cn.nihility.rbac.identity.upstream.entity.UpstreamSourceEntity;
import cn.nihility.rbac.identity.upstream.entity.UpstreamSyncRecordDetailEntity;
import cn.nihility.rbac.identity.upstream.entity.UpstreamSyncRecordEntity;
import cn.nihility.rbac.identity.upstream.mapper.UpstreamDomainConfigMapper;
import cn.nihility.rbac.identity.upstream.mapper.UpstreamFieldMappingMapper;
import cn.nihility.rbac.identity.upstream.mapper.UpstreamSourceMapper;
import cn.nihility.rbac.identity.upstream.mapper.UpstreamSyncRecordDetailMapper;
import cn.nihility.rbac.identity.upstream.mapper.UpstreamSyncRecordMapper;
import cn.nihility.rbac.identity.upstream.mapstruct.UpstreamSourceConvert;
import cn.nihility.rbac.identity.upstream.service.UpstreamDomainConfigService;
import cn.nihility.rbac.identity.upstream.service.UpstreamSourceService;
import cn.nihility.rbac.identity.upstream.support.UpstreamSyncExecutor;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 上游数据源业务逻辑实现。
 */
@Service
@RequiredArgsConstructor
public class UpstreamSourceServiceImpl implements UpstreamSourceService {

    /** 数据源创建时默认的调度间隔（分钟），管理员启用前可通过调度配置接口调整。 */
    private static final int DEFAULT_INTERVAL_MINUTES = 60;

    /** 上游数据源数据访问接口。 */
    private final UpstreamSourceMapper upstreamSourceMapper;

    /** 上游数据源数据域配置数据访问接口，仅用于级联删除。 */
    private final UpstreamDomainConfigMapper upstreamDomainConfigMapper;

    /** 上游字段映射数据访问接口，仅用于级联删除。 */
    private final UpstreamFieldMappingMapper upstreamFieldMappingMapper;

    /** 上游数据同步执行记录数据访问接口，仅用于级联删除。 */
    private final UpstreamSyncRecordMapper upstreamSyncRecordMapper;

    /** 上游数据同步执行记录明细数据访问接口，仅用于级联删除。 */
    private final UpstreamSyncRecordDetailMapper upstreamSyncRecordDetailMapper;

    /** 上游数据源数据域配置业务逻辑接口，创建数据源时同一事务内生成默认数据域配置。 */
    private final UpstreamDomainConfigService upstreamDomainConfigService;

    /** 同步执行引擎，供手动触发同步复用。 */
    private final UpstreamSyncExecutor upstreamSyncExecutor;

    /** SM4 主密钥配置，加解密接口自定义请求头取值、数据库密码。 */
    private final AppSecretProperties appSecretProperties;

    /** 当前登录操作人用户 id 解析服务。 */
    private final CurrentOperatorService currentOperatorService;

    /**
     * {@inheritDoc}
     */
    @Override
    public List<UpstreamSourceVO> list() {
        List<UpstreamSourceEntity> entities = upstreamSourceMapper.selectList(
                new LambdaQueryWrapper<UpstreamSourceEntity>().orderByAsc(UpstreamSourceEntity::getId));
        return entities.stream().map(this::toVO).toList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UpstreamSourceVO getById(Long id) {
        return toVO(getExistingEntity(id));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public UpstreamSourceVO create(UpstreamSourceCreateRequest request) {
        String operator = Objects.toString(currentOperatorService.resolveUserId(), null);
        LocalDateTime now = LocalDateTime.now();
        UpstreamSourceEntity entity = UpstreamSourceEntity.builder()
                .name(request.getName())
                .syncType(request.getSyncType())
                .enabled(false)
                .scheduleType(UpstreamScheduleType.INTERVAL)
                .intervalUnit(UpstreamIntervalUnit.MINUTE)
                .intervalValue(DEFAULT_INTERVAL_MINUTES)
                .createBy(operator)
                .createTime(now)
                .updateBy(operator)
                .updateTime(now)
                .build();
        upstreamSourceMapper.insert(entity);
        upstreamDomainConfigService.createDefaultDomainConfigs(entity.getId(), operator);
        return toVO(entity);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UpstreamSourceVO updateBasicInfo(Long id, UpstreamSourceUpdateRequest request) {
        UpstreamSourceEntity entity = getExistingEntity(id);
        entity.setName(request.getName());
        entity.setSyncType(request.getSyncType());
        touch(entity);
        upstreamSourceMapper.updateById(entity);
        return toVO(entity);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UpstreamSourceVO updateConnectionConfig(Long id, UpstreamConnectionConfigRequest request) {
        UpstreamSourceEntity entity = getExistingEntity(id);
        if (UpstreamSyncType.API.equals(entity.getSyncType())) {
            entity.setApiAuthHeaders(encryptHeaders(request.getApiAuthHeaders()));
        } else {
            assertJdbcUrlValid(request.getDbJdbcUrl());
            entity.setDbJdbcUrl(request.getDbJdbcUrl());
            entity.setDbUsername(request.getDbUsername());
            if (StringUtils.hasText(request.getDbPassword())) {
                entity.setDbPassword(Sm4JdkUtils.encrypt(request.getDbPassword(), appSecretProperties.getSm4Key()));
            }
        }
        touch(entity);
        upstreamSourceMapper.updateById(entity);
        return toVO(entity);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UpstreamSourceVO updateScheduleConfig(Long id, UpstreamScheduleConfigRequest request) {
        UpstreamSourceEntity entity = getExistingEntity(id);
        assertScheduleValid(request);
        entity.setScheduleType(request.getScheduleType());
        if (UpstreamScheduleType.INTERVAL.equals(request.getScheduleType())) {
            entity.setIntervalUnit(request.getIntervalUnit());
            entity.setIntervalValue(request.getIntervalValue());
            entity.setFixedTime(null);
        } else {
            entity.setFixedTime(request.getFixedTime());
            entity.setIntervalUnit(null);
            entity.setIntervalValue(null);
        }
        touch(entity);
        upstreamSourceMapper.updateById(entity);
        return toVO(entity);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UpstreamSourceVO enable(Long id) {
        return changeEnabled(id, true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UpstreamSourceVO disable(Long id) {
        return changeEnabled(id, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void delete(Long id) {
        getExistingEntity(id);
        upstreamFieldMappingMapper.delete(new LambdaQueryWrapper<UpstreamFieldMappingEntity>()
                .eq(UpstreamFieldMappingEntity::getSourceId, id));
        upstreamDomainConfigMapper.delete(new LambdaQueryWrapper<UpstreamDomainConfigEntity>()
                .eq(UpstreamDomainConfigEntity::getSourceId, id));
        upstreamSyncRecordMapper.delete(new LambdaQueryWrapper<UpstreamSyncRecordEntity>()
                .eq(UpstreamSyncRecordEntity::getSourceId, id));
        upstreamSyncRecordDetailMapper.delete(new LambdaQueryWrapper<UpstreamSyncRecordDetailEntity>()
                .eq(UpstreamSyncRecordDetailEntity::getSourceId, id));
        upstreamSourceMapper.deleteById(id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void manualSync(Long id) {
        getExistingEntity(id);
        upstreamSyncExecutor.syncSource(id, UpstreamTriggerType.MANUAL);
    }

    /**
     * 变更数据源启用状态。
     *
     * @param id      上游数据源 id
     * @param enabled 目标启用状态
     * @return 更新后的上游数据源视图对象
     */
    private UpstreamSourceVO changeEnabled(Long id, boolean enabled) {
        UpstreamSourceEntity entity = getExistingEntity(id);
        entity.setEnabled(enabled);
        touch(entity);
        upstreamSourceMapper.updateById(entity);
        return toVO(entity);
    }

    /**
     * 校验调度配置请求：{@code INTERVAL} 要求间隔单位合法、间隔取值为正整数；
     * {@code FIXED_TIME} 要求固定时间点非空（格式已由 DTO 上的正则约束）。
     *
     * @param request 调度配置更新请求
     */
    private void assertScheduleValid(UpstreamScheduleConfigRequest request) {
        if (UpstreamScheduleType.INTERVAL.equals(request.getScheduleType())) {
            if (!UpstreamIntervalUnit.ALL_UNITS.contains(request.getIntervalUnit())) {
                throw new BusinessException("间隔单位不能为空，只能是 MINUTE 或 HOUR");
            }
            if (request.getIntervalValue() == null || request.getIntervalValue() <= 0) {
                throw new BusinessException("间隔取值必须是正整数");
            }
        } else if (!StringUtils.hasText(request.getFixedTime())) {
            throw new BusinessException("固定时间点不能为空");
        }
    }

    /**
     * 校验 JDBC 连接地址仅支持 MySQL（{@code jdbc:mysql://} 前缀，忽略大小写），且不能为空
     * （spec.md Requirement：数据库表方式连接配置）。
     *
     * @param jdbcUrl JDBC 连接地址
     */
    private void assertJdbcUrlValid(String jdbcUrl) {
        if (!StringUtils.hasText(jdbcUrl) || !jdbcUrl.toLowerCase().startsWith("jdbc:mysql://")) {
            throw new BusinessException("JDBC 连接地址仅支持 MySQL，须以 jdbc:mysql:// 开头");
        }
    }

    /**
     * 把明文请求头 Map 逐一用 SM4 主密钥加密后序列化为 JSON 文本，整体替换语义
     * （design.md Decision 1）。
     *
     * @param plainHeaders 明文请求头 Map，允许为 {@code null}/空
     * @return 加密后的 JSON 文本，无请求头时返回空对象 JSON 文本
     */
    private String encryptHeaders(Map<String, String> plainHeaders) {
        Map<String, String> encrypted = new LinkedHashMap<>();
        if (plainHeaders != null) {
            for (Map.Entry<String, String> entry : plainHeaders.entrySet()) {
                if (!StringUtils.hasText(entry.getKey())) {
                    throw new BusinessException("请求头 key 不能为空");
                }
                encrypted.put(entry.getKey(), Sm4JdkUtils.encrypt(
                        entry.getValue() == null ? "" : entry.getValue(), appSecretProperties.getSm4Key()));
            }
        }
        return JacksonUtils.toJson(encrypted);
    }

    /**
     * 更新审计字段（{@code updateBy}/{@code updateTime}）。
     *
     * @param entity 待更新的实体
     */
    private void touch(UpstreamSourceEntity entity) {
        entity.setUpdateBy(Objects.toString(currentOperatorService.resolveUserId(), null));
        entity.setUpdateTime(LocalDateTime.now());
    }

    /**
     * 查询一个上游数据源，不存在时抛出业务异常。
     *
     * @param id 上游数据源 id
     * @return 上游数据源实体
     */
    private UpstreamSourceEntity getExistingEntity(Long id) {
        UpstreamSourceEntity entity = upstreamSourceMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("上游数据源不存在");
        }
        return entity;
    }

    /**
     * 把实体转换为视图对象，敏感字段（{@code apiAuthHeaders}/{@code dbPassword}）不回传
     * 明文，只把请求头 key 列表解析出来。
     *
     * @param entity 上游数据源实体
     * @return 上游数据源视图对象
     */
    private UpstreamSourceVO toVO(UpstreamSourceEntity entity) {
        UpstreamSourceVO vo = UpstreamSourceConvert.INSTANCE.toVO(entity);
        vo.setApiAuthHeaderKeys(resolveHeaderKeys(entity.getApiAuthHeaders()));
        return vo;
    }

    /**
     * 从加密请求头 JSON 文本中解析出 key 列表（不解密取值，接口层不需要）。
     *
     * @param apiAuthHeadersJson 加密请求头 JSON 文本，允许为 {@code null}/空
     * @return 请求头 key 列表，未配置时返回空列表
     */
    private List<String> resolveHeaderKeys(String apiAuthHeadersJson) {
        if (!StringUtils.hasText(apiAuthHeadersJson)) {
            return List.of();
        }
        Map<String, String> headers = JacksonUtils.toObj(apiAuthHeadersJson, JacksonUtils.MAP_STRING_TYPE_REFERENCE);
        return headers == null ? List.of() : List.copyOf(headers.keySet());
    }
}
