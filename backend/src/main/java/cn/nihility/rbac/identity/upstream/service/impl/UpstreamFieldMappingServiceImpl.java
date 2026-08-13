package cn.nihility.rbac.identity.upstream.service.impl;

import cn.nihility.rbac.app.sync.constant.TransformType;
import cn.nihility.rbac.app.sync.support.TransformScriptValidator;
import cn.nihility.rbac.auth.service.CurrentOperatorService;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.identity.upstream.constant.UpstreamDataType;
import cn.nihility.rbac.identity.upstream.dto.UpstreamFieldMappingSaveRequest;
import cn.nihility.rbac.identity.upstream.dto.UpstreamFieldMappingVO;
import cn.nihility.rbac.identity.upstream.entity.UpstreamFieldMappingEntity;
import cn.nihility.rbac.identity.upstream.mapper.UpstreamFieldMappingMapper;
import cn.nihility.rbac.identity.upstream.mapper.UpstreamSourceMapper;
import cn.nihility.rbac.identity.upstream.mapstruct.UpstreamFieldMappingConvert;
import cn.nihility.rbac.identity.upstream.service.UpstreamFieldMappingService;
import cn.nihility.rbac.metadata.constant.MetadataFieldStatus;
import cn.nihility.rbac.metadata.entity.MetadataFieldEntity;
import cn.nihility.rbac.metadata.mapper.MetadataFieldMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 上游字段映射业务逻辑实现，整体结构比照 {@code AppSyncConfigServiceImpl} 的字段映射
 * 部分，方向相反（design.md Decision 5）。
 */
@Service
@RequiredArgsConstructor
public class UpstreamFieldMappingServiceImpl implements UpstreamFieldMappingService {

    /** 上游字段映射数据访问接口。 */
    private final UpstreamFieldMappingMapper upstreamFieldMappingMapper;

    /** 上游数据源数据访问接口，仅用于校验数据源存在性。 */
    private final UpstreamSourceMapper upstreamSourceMapper;

    /** 元数据字段数据访问接口，校验字段映射请求中 {@code metadataFieldId} 的合法性。 */
    private final MetadataFieldMapper metadataFieldMapper;

    /** 当前登录操作人用户 id 解析服务。 */
    private final CurrentOperatorService currentOperatorService;

    /**
     * {@inheritDoc}
     */
    @Override
    public List<UpstreamFieldMappingVO> list(Long sourceId, String dataType) {
        return UpstreamFieldMappingConvert.INSTANCE.toVOList(
                upstreamFieldMappingMapper.selectBySourceIdAndDataType(sourceId, dataType));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public List<UpstreamFieldMappingVO> replace(Long sourceId, String dataType,
            List<UpstreamFieldMappingSaveRequest> requests) {
        if (!UpstreamDataType.ALL_TYPES.contains(dataType)) {
            throw new BusinessException("非法的数据域：" + dataType);
        }
        if (upstreamSourceMapper.selectById(sourceId) == null) {
            throw new BusinessException("上游数据源不存在");
        }
        List<UpstreamFieldMappingSaveRequest> effectiveRequests = requests == null ? List.of() : requests;
        assertRequestsValid(dataType, effectiveRequests);

        upstreamFieldMappingMapper.delete(new LambdaQueryWrapper<UpstreamFieldMappingEntity>()
                .eq(UpstreamFieldMappingEntity::getSourceId, sourceId)
                .eq(UpstreamFieldMappingEntity::getDataType, dataType));

        String operator = Objects.toString(currentOperatorService.resolveUserId(), null);
        LocalDateTime now = LocalDateTime.now();
        for (UpstreamFieldMappingSaveRequest request : effectiveRequests) {
            UpstreamFieldMappingEntity entity = UpstreamFieldMappingConvert.INSTANCE.toEntity(request);
            entity.setSourceId(sourceId);
            entity.setDataType(dataType);
            entity.setTransformValue(TransformType.NO_TRANSFORM.equals(request.getTransformType())
                    ? null : request.getTransformValue());
            entity.setCreateBy(operator);
            entity.setCreateTime(now);
            entity.setUpdateBy(operator);
            entity.setUpdateTime(now);
            upstreamFieldMappingMapper.insert(entity);
        }

        return list(sourceId, dataType);
    }

    /**
     * 逐项校验字段映射保存请求：上游字段编码非空且同一请求列表内不重复；
     * {@code metadataFieldId} 必须存在、状态启用、{@code bizType} 与 {@code dataType}
     * 一致，且同一请求列表内不重复；{@code transformType=FIXED_VALUE} 时
     * {@code transformValue} 必填；{@code transformType=SCRIPT} 时 {@code transformValue}
     * 必填且语法必须合法。
     *
     * @param dataType 数据域
     * @param requests 本次提交的完整字段映射行列表
     */
    private void assertRequestsValid(String dataType, List<UpstreamFieldMappingSaveRequest> requests) {
        Set<String> seenUpstreamFieldCodes = new HashSet<>();
        Set<Long> seenMetadataFieldIds = new HashSet<>();
        for (UpstreamFieldMappingSaveRequest request : requests) {
            if (!seenUpstreamFieldCodes.add(request.getUpstreamFieldCode())) {
                throw new BusinessException("同一数据域下，上游字段编码不能重复：" + request.getUpstreamFieldCode());
            }
            if (!seenMetadataFieldIds.add(request.getMetadataFieldId())) {
                throw new BusinessException("同一数据域下，同一目标字段不能重复添加映射");
            }
        }

        for (UpstreamFieldMappingSaveRequest request : requests) {
            if (!TransformType.ALL_TYPES.contains(request.getTransformType())) {
                throw new BusinessException("非法的转换方式：" + request.getTransformType());
            }
            assertMetadataFieldValid(dataType, request.getMetadataFieldId());
            assertTransformValueValid(request.getTransformType(), request.getTransformValue());
        }
    }

    /**
     * 校验字段映射请求中的目标字段：必须存在、状态启用、{@code bizType} 与
     * {@code dataType} 一致。
     *
     * @param dataType        数据域
     * @param metadataFieldId 目标字段 id
     */
    private void assertMetadataFieldValid(String dataType, Long metadataFieldId) {
        MetadataFieldEntity metadataField = metadataFieldMapper.selectById(metadataFieldId);
        if (metadataField == null || !Objects.equals(metadataField.getStatus(), MetadataFieldStatus.ENABLED)) {
            throw new BusinessException("目标字段不存在或未启用：" + metadataFieldId);
        }
        if (!Objects.equals(metadataField.getBizType(), dataType)) {
            throw new BusinessException("目标字段[" + metadataField.getFieldName() + "]不属于数据域 " + dataType);
        }
    }

    /**
     * 按转换方式校验转换取值：{@code FIXED_VALUE} 要求非空；{@code SCRIPT} 要求非空且语法
     * 合法（{@link TransformScriptValidator#validateSyntax}）；{@code NO_TRANSFORM} 不校验。
     *
     * @param transformType  转换方式
     * @param transformValue 转换取值
     */
    private void assertTransformValueValid(String transformType, String transformValue) {
        if (TransformType.FIXED_VALUE.equals(transformType) && !StringUtils.hasText(transformValue)) {
            throw new BusinessException("转换方式为固定值时，转换取值不能为空");
        }
        if (TransformType.SCRIPT.equals(transformType)) {
            if (!StringUtils.hasText(transformValue)) {
                throw new BusinessException("转换方式为转换脚本时，转换取值不能为空");
            }
            TransformScriptValidator.validateSyntax(transformValue);
        }
    }
}
