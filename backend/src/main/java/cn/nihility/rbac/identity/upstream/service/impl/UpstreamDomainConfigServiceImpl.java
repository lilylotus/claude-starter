package cn.nihility.rbac.identity.upstream.service.impl;

import cn.nihility.rbac.auth.service.CurrentOperatorService;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.identity.upstream.constant.UpstreamDataType;
import cn.nihility.rbac.identity.upstream.constant.UpstreamSyncType;
import cn.nihility.rbac.identity.upstream.dto.UpstreamDomainConfigUpdateRequest;
import cn.nihility.rbac.identity.upstream.dto.UpstreamDomainConfigVO;
import cn.nihility.rbac.identity.upstream.entity.UpstreamDomainConfigEntity;
import cn.nihility.rbac.identity.upstream.entity.UpstreamSourceEntity;
import cn.nihility.rbac.identity.upstream.mapper.UpstreamDomainConfigMapper;
import cn.nihility.rbac.identity.upstream.mapper.UpstreamSourceMapper;
import cn.nihility.rbac.identity.upstream.mapstruct.UpstreamDomainConfigConvert;
import cn.nihility.rbac.identity.upstream.service.UpstreamDomainConfigService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 上游数据源数据域配置业务逻辑实现。
 */
@Service
@RequiredArgsConstructor
public class UpstreamDomainConfigServiceImpl implements UpstreamDomainConfigService {

    /** 上游数据源数据域配置数据访问接口。 */
    private final UpstreamDomainConfigMapper upstreamDomainConfigMapper;

    /** 上游数据源数据访问接口，仅用于只读查询数据源当前 {@code syncType} 做条件校验。 */
    private final UpstreamSourceMapper upstreamSourceMapper;

    /** 当前登录操作人用户 id 解析服务。 */
    private final CurrentOperatorService currentOperatorService;

    /**
     * {@inheritDoc}
     */
    @Override
    public void createDefaultDomainConfigs(Long sourceId, String operator) {
        LocalDateTime now = LocalDateTime.now();
        for (String dataType : UpstreamDataType.SYNC_ORDER) {
            UpstreamDomainConfigEntity entity = UpstreamDomainConfigEntity.builder()
                    .sourceId(sourceId)
                    .dataType(dataType)
                    .enabled(false)
                    .createBy(operator)
                    .createTime(now)
                    .updateBy(operator)
                    .updateTime(now)
                    .build();
            upstreamDomainConfigMapper.insert(entity);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<UpstreamDomainConfigVO> listBySource(Long sourceId) {
        List<UpstreamDomainConfigEntity> entities = upstreamDomainConfigMapper.selectList(
                new LambdaQueryWrapper<UpstreamDomainConfigEntity>()
                        .eq(UpstreamDomainConfigEntity::getSourceId, sourceId));
        entities.sort(Comparator.comparingInt(entity -> UpstreamDataType.SYNC_ORDER.indexOf(entity.getDataType())));
        return UpstreamDomainConfigConvert.INSTANCE.toVOList(entities);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UpstreamDomainConfigVO update(Long sourceId, String dataType, UpstreamDomainConfigUpdateRequest request) {
        if (!UpstreamDataType.ALL_TYPES.contains(dataType)) {
            throw new BusinessException("非法的数据域：" + dataType);
        }
        UpstreamSourceEntity source = upstreamSourceMapper.selectById(sourceId);
        if (source == null) {
            throw new BusinessException("上游数据源不存在");
        }
        assertRequestValid(source.getSyncType(), request);

        UpstreamDomainConfigEntity entity = findDomainConfig(sourceId, dataType);
        entity.setEnabled(request.getEnabled());
        entity.setApiUrl(request.getApiUrl());
        entity.setApiMethod(request.getApiMethod());
        entity.setDbSql(request.getDbSql());
        entity.setUpdateBy(Objects.toString(currentOperatorService.resolveUserId(), null));
        entity.setUpdateTime(LocalDateTime.now());
        upstreamDomainConfigMapper.updateById(entity);

        return UpstreamDomainConfigConvert.INSTANCE.toVO(entity);
    }

    /**
     * 按数据源当前同步方式校验数据域配置请求：{@code API} 模式下启用时请求地址不能为空；
     * {@code DB_TABLE} 模式下查询 SQL 非空时须以 {@code SELECT}/{@code WITH} 开头（忽略
     * 大小写与首尾空白），启用时不能为空（spec.md Requirement：数据域配置）。
     *
     * @param syncType 数据源当前同步方式
     * @param request  数据域配置修改请求
     */
    private void assertRequestValid(String syncType, UpstreamDomainConfigUpdateRequest request) {
        if (UpstreamSyncType.API.equals(syncType)) {
            if (Boolean.TRUE.equals(request.getEnabled()) && !StringUtils.hasText(request.getApiUrl())) {
                throw new BusinessException("启用该数据域前，请求地址不能为空");
            }
            return;
        }
        String sql = request.getDbSql();
        if (StringUtils.hasText(sql)) {
            String trimmedUpper = sql.trim().toUpperCase();
            if (!trimmedUpper.startsWith("SELECT") && !trimmedUpper.startsWith("WITH")) {
                throw new BusinessException("查询 SQL 只支持以 SELECT 或 WITH 开头的只读查询");
            }
        } else if (Boolean.TRUE.equals(request.getEnabled())) {
            throw new BusinessException("启用该数据域前，查询 SQL 不能为空");
        }
    }

    /**
     * 按 {@code (sourceId, dataType)} 查询数据域配置，理论上因
     * {@code createDefaultDomainConfigs} 已预置 3 行必然存在，仍做防御性判空。
     *
     * @param sourceId 上游数据源 id
     * @param dataType 数据域
     * @return 数据域配置实体
     */
    private UpstreamDomainConfigEntity findDomainConfig(Long sourceId, String dataType) {
        UpstreamDomainConfigEntity entity = upstreamDomainConfigMapper.selectOne(
                new LambdaQueryWrapper<UpstreamDomainConfigEntity>()
                        .eq(UpstreamDomainConfigEntity::getSourceId, sourceId)
                        .eq(UpstreamDomainConfigEntity::getDataType, dataType));
        if (entity == null) {
            throw new BusinessException("上游数据源不存在或数据域配置缺失");
        }
        return entity;
    }
}
