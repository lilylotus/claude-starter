package cn.nihility.rbac.abac.service.impl;

import cn.nihility.rbac.abac.constant.AbacAttrOperator;
import cn.nihility.rbac.abac.constant.AbacPolicyStatus;
import cn.nihility.rbac.abac.dto.AbacPolicyCreateRequest;
import cn.nihility.rbac.abac.dto.AbacPolicyQueryRequest;
import cn.nihility.rbac.abac.dto.AbacPolicyTimeWindowRequestItem;
import cn.nihility.rbac.abac.dto.AbacPolicyTimeWindowVO;
import cn.nihility.rbac.abac.dto.AbacPolicyUpdateRequest;
import cn.nihility.rbac.abac.dto.AbacPolicyUserAttrRequestItem;
import cn.nihility.rbac.abac.dto.AbacPolicyUserAttrRow;
import cn.nihility.rbac.abac.dto.AbacPolicyUserAttrVO;
import cn.nihility.rbac.abac.dto.AbacPolicyVO;
import cn.nihility.rbac.abac.entity.AbacPolicyEntity;
import cn.nihility.rbac.abac.entity.AbacPolicyResourceEntity;
import cn.nihility.rbac.abac.entity.AbacPolicyTimeWindowEntity;
import cn.nihility.rbac.abac.entity.AbacPolicyUserAttrEntity;
import cn.nihility.rbac.abac.mapper.AbacPolicyMapper;
import cn.nihility.rbac.abac.mapper.AbacPolicyResourceMapper;
import cn.nihility.rbac.abac.mapper.AbacPolicyTimeWindowMapper;
import cn.nihility.rbac.abac.mapper.AbacPolicyUserAttrMapper;
import cn.nihility.rbac.abac.mapstruct.AbacPolicyConvert;
import cn.nihility.rbac.abac.service.AbacPolicyService;
import cn.nihility.rbac.auth.service.CurrentOperatorService;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.common.result.PageResult;
import cn.nihility.rbac.formfield.constant.FormFieldBizType;
import cn.nihility.rbac.metadata.constant.MetadataFieldStatus;
import cn.nihility.rbac.metadata.entity.MetadataFieldEntity;
import cn.nihility.rbac.metadata.mapper.MetadataFieldMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * ABAC 策略规则业务逻辑实现。
 */
@Service
@RequiredArgsConstructor
public class AbacPolicyServiceImpl implements AbacPolicyService {

    /** 显示序号未提供时的默认值。 */
    private static final int DEFAULT_SHOW_ORDER = 0;

    /** 策略规则数据访问接口。 */
    private final AbacPolicyMapper abacPolicyMapper;

    /** 策略目标资源编码数据访问接口。 */
    private final AbacPolicyResourceMapper abacPolicyResourceMapper;

    /** 策略用户属性条件数据访问接口。 */
    private final AbacPolicyUserAttrMapper abacPolicyUserAttrMapper;

    /** 策略时间窗口条件数据访问接口。 */
    private final AbacPolicyTimeWindowMapper abacPolicyTimeWindowMapper;

    /** 元数据字段数据访问接口，校验用户属性条件请求中 {@code metadataFieldId} 的合法性。 */
    private final MetadataFieldMapper metadataFieldMapper;

    /** 当前登录操作人用户 id 解析服务。 */
    private final CurrentOperatorService currentOperatorService;

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResult<AbacPolicyVO> page(AbacPolicyQueryRequest request) {
        LambdaQueryWrapper<AbacPolicyEntity> wrapper = new LambdaQueryWrapper<AbacPolicyEntity>()
                .like(StringUtils.hasText(request.getName()), AbacPolicyEntity::getName, request.getName())
                .eq(request.getStatus() != null, AbacPolicyEntity::getStatus, request.getStatus())
                .orderByAsc(AbacPolicyEntity::getShowOrder)
                .orderByAsc(AbacPolicyEntity::getId);

        Page<AbacPolicyEntity> queryPage = new Page<>(request.getPage(), request.getPageSize());
        Page<AbacPolicyEntity> resultPage = abacPolicyMapper.selectPage(queryPage, wrapper);

        List<AbacPolicyVO> records = resultPage.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(records, resultPage);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AbacPolicyVO detail(Long id) {
        return toVO(getExisting(id));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public AbacPolicyVO create(AbacPolicyCreateRequest request) {
        assertResourceCodesNotEmpty(request.getResourceCodes());
        assertConditionsNotBothEmpty(request.getUserAttrs(), request.getTimeWindows());
        assertUserAttrsValid(request.getUserAttrs());
        assertTimeWindowsValid(request.getTimeWindows());

        String operator = resolveOperator();
        LocalDateTime now = LocalDateTime.now();
        AbacPolicyEntity entity = AbacPolicyEntity.builder()
                .name(request.getName())
                .remark(request.getRemark())
                .status(AbacPolicyStatus.ENABLED)
                .showOrder(resolveShowOrder(request.getShowOrder()))
                .createBy(operator)
                .createTime(now)
                .updateBy(operator)
                .updateTime(now)
                .build();
        abacPolicyMapper.insert(entity);

        saveResourceCodes(entity.getId(), request.getResourceCodes(), operator, now);
        saveUserAttrs(entity.getId(), request.getUserAttrs(), operator, now);
        saveTimeWindows(entity.getId(), request.getTimeWindows(), operator, now);
        return toVO(entity);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public AbacPolicyVO update(Long id, AbacPolicyUpdateRequest request) {
        AbacPolicyEntity entity = getExisting(id);
        assertResourceCodesNotEmpty(request.getResourceCodes());
        assertConditionsNotBothEmpty(request.getUserAttrs(), request.getTimeWindows());
        assertUserAttrsValid(request.getUserAttrs());
        assertTimeWindowsValid(request.getTimeWindows());

        String operator = resolveOperator();
        LocalDateTime now = LocalDateTime.now();
        entity.setName(request.getName());
        entity.setRemark(request.getRemark());
        entity.setShowOrder(resolveShowOrder(request.getShowOrder()));
        entity.setUpdateBy(operator);
        entity.setUpdateTime(now);
        abacPolicyMapper.updateById(entity);

        abacPolicyResourceMapper.delete(new LambdaQueryWrapper<AbacPolicyResourceEntity>()
                .eq(AbacPolicyResourceEntity::getPolicyId, id));
        saveResourceCodes(id, request.getResourceCodes(), operator, now);

        abacPolicyUserAttrMapper.delete(new LambdaQueryWrapper<AbacPolicyUserAttrEntity>()
                .eq(AbacPolicyUserAttrEntity::getPolicyId, id));
        saveUserAttrs(id, request.getUserAttrs(), operator, now);

        abacPolicyTimeWindowMapper.delete(new LambdaQueryWrapper<AbacPolicyTimeWindowEntity>()
                .eq(AbacPolicyTimeWindowEntity::getPolicyId, id));
        saveTimeWindows(id, request.getTimeWindows(), operator, now);
        return toVO(entity);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AbacPolicyVO enable(Long id) {
        return updateStatus(id, AbacPolicyStatus.ENABLED);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AbacPolicyVO disable(Long id) {
        return updateStatus(id, AbacPolicyStatus.DISABLED);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void delete(Long id) {
        getExisting(id);
        abacPolicyResourceMapper.delete(new LambdaQueryWrapper<AbacPolicyResourceEntity>()
                .eq(AbacPolicyResourceEntity::getPolicyId, id));
        abacPolicyUserAttrMapper.delete(new LambdaQueryWrapper<AbacPolicyUserAttrEntity>()
                .eq(AbacPolicyUserAttrEntity::getPolicyId, id));
        abacPolicyTimeWindowMapper.delete(new LambdaQueryWrapper<AbacPolicyTimeWindowEntity>()
                .eq(AbacPolicyTimeWindowEntity::getPolicyId, id));
        abacPolicyMapper.deleteById(id);
    }

    /**
     * 修改策略状态。
     *
     * @param id     策略 id
     * @param status 目标状态
     * @return 更新后的策略规则详情
     */
    private AbacPolicyVO updateStatus(Long id, int status) {
        AbacPolicyEntity entity = getExisting(id);
        entity.setStatus(status);
        entity.setUpdateBy(resolveOperator());
        entity.setUpdateTime(LocalDateTime.now());
        abacPolicyMapper.updateById(entity);
        return toVO(entity);
    }

    /**
     * 按 id 查询策略规则实体，不存在时抛出业务异常。
     *
     * @param id 策略 id
     * @return 策略规则实体
     */
    private AbacPolicyEntity getExisting(Long id) {
        AbacPolicyEntity entity = abacPolicyMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("策略规则不存在");
        }
        return entity;
    }

    /**
     * 校验目标资源编码不能为空（spec.md"ABAC 策略规则的定义与维护"需求；Bean Validation
     * 的 {@code @NotEmpty} 已在 controller 层拦截绝大多数场景，此处服务层显式兜底，不依赖
     * 调用方一定经过 {@code @Valid} 校验）。
     *
     * @param resourceCodes 目标资源编码列表
     */
    private void assertResourceCodesNotEmpty(List<String> resourceCodes) {
        if (resourceCodes == null || resourceCodes.isEmpty()) {
            throw new BusinessException("目标资源编码不能为空");
        }
    }

    /**
     * 校验用户属性条件、时间窗口条件不能同时为空，两者中至少配置一类即可保存（design.md
     * Decision 5）。
     *
     * @param userAttrs   用户属性条件列表
     * @param timeWindows 时间窗口条件列表
     */
    private void assertConditionsNotBothEmpty(List<AbacPolicyUserAttrRequestItem> userAttrs,
            List<AbacPolicyTimeWindowRequestItem> timeWindows) {
        boolean userAttrEmpty = userAttrs == null || userAttrs.isEmpty();
        boolean timeWindowEmpty = timeWindows == null || timeWindows.isEmpty();
        if (userAttrEmpty && timeWindowEmpty) {
            throw new BusinessException("用户属性条件、时间窗口条件不能同时为空，请至少配置一类");
        }
    }

    /**
     * 解析显示序号：请求未提供时默认 {@code 0}。
     *
     * @param showOrder 请求携带的显示序号，可为空
     * @return 解析后的显示序号
     */
    private Integer resolveShowOrder(Integer showOrder) {
        return showOrder == null ? DEFAULT_SHOW_ORDER : showOrder;
    }

    /**
     * 校验用户属性条件请求列表：运算符合法、同一策略内字段不能重复、{@code EQ}/{@code NE}
     * 比较值只能一个、关联的元数据字段存在且属于用户业务域并已启用（spec.md"ABAC 策略
     * 规则的用户属性条件"需求）。
     *
     * @param userAttrs 用户属性条件请求列表
     */
    private void assertUserAttrsValid(List<AbacPolicyUserAttrRequestItem> userAttrs) {
        if (userAttrs == null || userAttrs.isEmpty()) {
            return;
        }
        Set<Long> seenFieldIds = new HashSet<>();
        for (AbacPolicyUserAttrRequestItem item : userAttrs) {
            if (!seenFieldIds.add(item.getMetadataFieldId())) {
                throw new BusinessException("同一策略内，属性字段不能重复配置");
            }
            if (!AbacAttrOperator.ALL_OPERATORS.contains(item.getOperator())) {
                throw new BusinessException("非法的运算符：" + item.getOperator());
            }
            if (!AbacAttrOperator.IN.equals(item.getOperator()) && item.getValues().size() != 1) {
                throw new BusinessException("运算符为等于/不等于时，比较值只能有一个");
            }

            MetadataFieldEntity field = metadataFieldMapper.selectById(item.getMetadataFieldId());
            if (field == null || !Objects.equals(field.getStatus(), MetadataFieldStatus.ENABLED)) {
                throw new BusinessException("属性字段不存在或未启用：" + item.getMetadataFieldId());
            }
            if (!FormFieldBizType.USER.equals(field.getBizType())) {
                throw new BusinessException("属性字段[" + field.getFieldName() + "]不属于用户业务域");
            }
        }
    }

    /**
     * 校验时间窗口条件请求列表：结束时间须晚于起始时间（spec.md"ABAC 策略规则的时间窗口
     * 条件"需求）。
     *
     * @param timeWindows 时间窗口条件请求列表
     */
    private void assertTimeWindowsValid(List<AbacPolicyTimeWindowRequestItem> timeWindows) {
        if (timeWindows == null || timeWindows.isEmpty()) {
            return;
        }
        for (AbacPolicyTimeWindowRequestItem item : timeWindows) {
            if (item.getStartTime() == null || item.getEndTime() == null
                    || !item.getEndTime().isAfter(item.getStartTime())) {
                throw new BusinessException("时间窗口的结束时间必须晚于起始时间");
            }
        }
    }

    /**
     * 保存目标资源编码，同一请求列表内 {@code permissionCode} 不允许重复。
     *
     * @param policyId      策略 id
     * @param resourceCodes 目标资源编码列表，不能为空
     * @param operator      操作人
     * @param now           操作时间
     */
    private void saveResourceCodes(Long policyId, List<String> resourceCodes, String operator, LocalDateTime now) {
        Set<String> seenCodes = new HashSet<>();
        for (String permissionCode : resourceCodes) {
            if (!seenCodes.add(permissionCode)) {
                throw new BusinessException("同一策略内，目标资源编码不允许重复配置");
            }
            abacPolicyResourceMapper.insert(AbacPolicyResourceEntity.builder()
                    .policyId(policyId)
                    .permissionCode(permissionCode)
                    .createBy(operator)
                    .createTime(now)
                    .updateBy(operator)
                    .updateTime(now)
                    .build());
        }
    }

    /**
     * 保存用户属性条件，{@code IN} 运算符的比较值序列化为逗号分隔字符串落库。
     *
     * @param policyId  策略 id
     * @param userAttrs 用户属性条件请求列表，可为空
     * @param operator  操作人
     * @param now       操作时间
     */
    private void saveUserAttrs(Long policyId, List<AbacPolicyUserAttrRequestItem> userAttrs, String operator,
            LocalDateTime now) {
        if (userAttrs == null || userAttrs.isEmpty()) {
            return;
        }
        for (AbacPolicyUserAttrRequestItem item : userAttrs) {
            String attrValue = String.join(",", item.getValues());
            abacPolicyUserAttrMapper.insert(AbacPolicyUserAttrEntity.builder()
                    .policyId(policyId)
                    .metadataFieldId(item.getMetadataFieldId())
                    .operator(item.getOperator())
                    .attrValue(attrValue)
                    .createBy(operator)
                    .createTime(now)
                    .updateBy(operator)
                    .updateTime(now)
                    .build());
        }
    }

    /**
     * 保存时间窗口条件。
     *
     * @param policyId    策略 id
     * @param timeWindows 时间窗口条件请求列表，可为空
     * @param operator    操作人
     * @param now         操作时间
     */
    private void saveTimeWindows(Long policyId, List<AbacPolicyTimeWindowRequestItem> timeWindows, String operator,
            LocalDateTime now) {
        if (timeWindows == null || timeWindows.isEmpty()) {
            return;
        }
        for (AbacPolicyTimeWindowRequestItem item : timeWindows) {
            abacPolicyTimeWindowMapper.insert(AbacPolicyTimeWindowEntity.builder()
                    .policyId(policyId)
                    .dayOfWeek(item.getDayOfWeek())
                    .startTime(item.getStartTime())
                    .endTime(item.getEndTime())
                    .createBy(operator)
                    .createTime(now)
                    .updateBy(operator)
                    .updateTime(now)
                    .build());
        }
    }

    /**
     * 组装策略规则详情视图对象：基础字段通过 {@link AbacPolicyConvert} 转换，目标资源
     * 编码/用户属性条件/时间窗口条件通过各自的查询回填。
     *
     * @param entity 策略规则实体
     * @return 策略规则视图对象
     */
    private AbacPolicyVO toVO(AbacPolicyEntity entity) {
        AbacPolicyVO vo = AbacPolicyConvert.INSTANCE.toVO(entity);

        List<AbacPolicyResourceEntity> resources = abacPolicyResourceMapper.selectList(
                new LambdaQueryWrapper<AbacPolicyResourceEntity>()
                        .eq(AbacPolicyResourceEntity::getPolicyId, entity.getId())
                        .orderByAsc(AbacPolicyResourceEntity::getId));
        List<String> resourceCodes = resources.stream().map(AbacPolicyResourceEntity::getPermissionCode).toList();

        List<AbacPolicyUserAttrRow> attrRows = abacPolicyUserAttrMapper.selectByPolicyId(entity.getId());
        List<AbacPolicyUserAttrVO> userAttrs = new ArrayList<>(attrRows.size());
        for (AbacPolicyUserAttrRow row : attrRows) {
            userAttrs.add(AbacPolicyUserAttrVO.builder()
                    .metadataFieldId(row.getMetadataFieldId())
                    .fieldName(row.getFieldName())
                    .fieldCode(row.getFieldCode())
                    .operator(row.getOperator())
                    .values(Arrays.asList(row.getAttrValue().split(",")))
                    .build());
        }

        List<AbacPolicyTimeWindowEntity> timeWindowEntities = abacPolicyTimeWindowMapper.selectList(
                new LambdaQueryWrapper<AbacPolicyTimeWindowEntity>()
                        .eq(AbacPolicyTimeWindowEntity::getPolicyId, entity.getId())
                        .orderByAsc(AbacPolicyTimeWindowEntity::getId));
        List<AbacPolicyTimeWindowVO> timeWindows = timeWindowEntities.stream()
                .map(window -> AbacPolicyTimeWindowVO.builder()
                        .dayOfWeek(window.getDayOfWeek())
                        .startTime(window.getStartTime())
                        .endTime(window.getEndTime())
                        .build())
                .toList();

        vo.setResourceCodes(resourceCodes);
        vo.setUserAttrs(userAttrs);
        vo.setTimeWindows(timeWindows);
        return vo;
    }

    /**
     * 解析当前登录操作人用户 id 的文本形式。
     *
     * @return 当前登录操作人用户 id 文本
     */
    private String resolveOperator() {
        return Objects.toString(currentOperatorService.resolveUserId(), null);
    }
}
