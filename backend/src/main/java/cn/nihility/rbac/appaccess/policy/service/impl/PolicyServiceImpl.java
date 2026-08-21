package cn.nihility.rbac.appaccess.policy.service.impl;

import cn.nihility.rbac.appaccess.policy.constant.PolicyAttrOperator;
import cn.nihility.rbac.appaccess.policy.constant.PolicyRequestControlBrowser;
import cn.nihility.rbac.appaccess.policy.constant.PolicyStatus;
import cn.nihility.rbac.appaccess.policy.dto.PolicyBrowserRuleVO;
import cn.nihility.rbac.appaccess.policy.dto.PolicyCreateRequest;
import cn.nihility.rbac.appaccess.policy.dto.PolicyIpRuleVO;
import cn.nihility.rbac.appaccess.policy.dto.PolicyOrgScopeRequestItem;
import cn.nihility.rbac.appaccess.policy.dto.PolicyOrgScopeVO;
import cn.nihility.rbac.appaccess.policy.dto.PolicyQueryRequest;
import cn.nihility.rbac.appaccess.policy.dto.PolicyTargetAppVO;
import cn.nihility.rbac.appaccess.policy.dto.PolicyUpdateRequest;
import cn.nihility.rbac.appaccess.policy.dto.PolicyUserAttrRequestItem;
import cn.nihility.rbac.appaccess.policy.dto.PolicyUserAttrRow;
import cn.nihility.rbac.appaccess.policy.dto.PolicyUserAttrVO;
import cn.nihility.rbac.appaccess.policy.dto.PolicyVO;
import cn.nihility.rbac.appaccess.policy.entity.PolicyBrowserRuleEntity;
import cn.nihility.rbac.appaccess.policy.entity.PolicyEntity;
import cn.nihility.rbac.appaccess.policy.entity.PolicyGrantEntity;
import cn.nihility.rbac.appaccess.policy.entity.PolicyIpRuleEntity;
import cn.nihility.rbac.appaccess.policy.entity.PolicyOrgScopeEntity;
import cn.nihility.rbac.appaccess.policy.entity.PolicyTargetAppEntity;
import cn.nihility.rbac.appaccess.policy.entity.PolicyUserAttrEntity;
import cn.nihility.rbac.appaccess.policy.mapper.PolicyBrowserRuleMapper;
import cn.nihility.rbac.appaccess.policy.mapper.PolicyGrantMapper;
import cn.nihility.rbac.appaccess.policy.mapper.PolicyIpRuleMapper;
import cn.nihility.rbac.appaccess.policy.mapper.PolicyMapper;
import cn.nihility.rbac.appaccess.policy.mapper.PolicyOrgScopeMapper;
import cn.nihility.rbac.appaccess.policy.mapper.PolicyTargetAppMapper;
import cn.nihility.rbac.appaccess.policy.mapper.PolicyUserAttrMapper;
import cn.nihility.rbac.appaccess.policy.mapstruct.PolicyConvert;
import cn.nihility.rbac.appaccess.policy.service.PolicyService;
import cn.nihility.rbac.appaccess.support.IpCidrMatcher;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 策略规则业务逻辑实现。
 */
@Service
@RequiredArgsConstructor
public class PolicyServiceImpl implements PolicyService {

    /** 策略规则数据访问接口。 */
    private final PolicyMapper policyMapper;

    /** 策略组织范围条件数据访问接口。 */
    private final PolicyOrgScopeMapper policyOrgScopeMapper;

    /** 策略用户属性条件数据访问接口。 */
    private final PolicyUserAttrMapper policyUserAttrMapper;

    /** 策略目标应用数据访问接口。 */
    private final PolicyTargetAppMapper policyTargetAppMapper;

    /** 策略请求控制条件-浏览器白名单数据访问接口（app-access-request-control change）。 */
    private final PolicyBrowserRuleMapper policyBrowserRuleMapper;

    /** 策略请求控制条件-IP/网段白名单数据访问接口（app-access-request-control change）。 */
    private final PolicyIpRuleMapper policyIpRuleMapper;

    /** 策略计算结果数据访问接口，删除策略时级联清理其产生的授权记录。 */
    private final PolicyGrantMapper policyGrantMapper;

    /** 元数据字段数据访问接口，校验用户属性条件请求中 {@code metadataFieldId} 的合法性。 */
    private final MetadataFieldMapper metadataFieldMapper;

    /** 当前登录操作人用户 id 解析服务。 */
    private final CurrentOperatorService currentOperatorService;

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResult<PolicyVO> page(PolicyQueryRequest request) {
        LambdaQueryWrapper<PolicyEntity> wrapper = new LambdaQueryWrapper<PolicyEntity>()
                .like(StringUtils.hasText(request.getName()), PolicyEntity::getName, request.getName())
                .eq(request.getStatus() != null, PolicyEntity::getStatus, request.getStatus())
                .orderByDesc(PolicyEntity::getId);

        Page<PolicyEntity> queryPage = new Page<>(request.getPage(), request.getPageSize());
        Page<PolicyEntity> resultPage = policyMapper.selectPage(queryPage, wrapper);

        List<PolicyVO> records = resultPage.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(records, resultPage);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PolicyVO detail(Long id) {
        return toVO(getExisting(id));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public PolicyVO create(PolicyCreateRequest request) {
        assertScopeAndAttrNotBothEmpty(request.getOrgScopes(), request.getUserAttrs(), request.getBrowserRules(),
                request.getIpRules());
        assertTargetAppsNotEmpty(request.getTargetAppIds());
        assertUserAttrsValid(request.getUserAttrs());
        assertRequestControlValid(request.getBrowserRules(), request.getIpRules());

        String operator = resolveOperator();
        LocalDateTime now = LocalDateTime.now();
        PolicyEntity entity = PolicyEntity.builder()
                .name(request.getName())
                .remark(request.getRemark())
                .status(PolicyStatus.ENABLED)
                .createBy(operator)
                .createTime(now)
                .updateBy(operator)
                .updateTime(now)
                .build();
        policyMapper.insert(entity);

        saveOrgScopes(entity.getId(), request.getOrgScopes(), operator, now);
        saveUserAttrs(entity.getId(), request.getUserAttrs(), operator, now);
        saveTargetApps(entity.getId(), request.getTargetAppIds(), operator, now);
        saveBrowserRules(entity.getId(), request.getBrowserRules(), operator, now);
        saveIpRules(entity.getId(), request.getIpRules(), operator, now);
        return toVO(entity);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public PolicyVO update(Long id, PolicyUpdateRequest request) {
        PolicyEntity entity = getExisting(id);
        assertScopeAndAttrNotBothEmpty(request.getOrgScopes(), request.getUserAttrs(), request.getBrowserRules(),
                request.getIpRules());
        assertTargetAppsNotEmpty(request.getTargetAppIds());
        assertUserAttrsValid(request.getUserAttrs());
        assertRequestControlValid(request.getBrowserRules(), request.getIpRules());

        String operator = resolveOperator();
        LocalDateTime now = LocalDateTime.now();
        entity.setName(request.getName());
        entity.setRemark(request.getRemark());
        entity.setUpdateBy(operator);
        entity.setUpdateTime(now);
        policyMapper.updateById(entity);

        policyOrgScopeMapper.delete(new LambdaQueryWrapper<PolicyOrgScopeEntity>()
                .eq(PolicyOrgScopeEntity::getPolicyId, id));
        saveOrgScopes(id, request.getOrgScopes(), operator, now);

        policyUserAttrMapper.delete(new LambdaQueryWrapper<PolicyUserAttrEntity>()
                .eq(PolicyUserAttrEntity::getPolicyId, id));
        saveUserAttrs(id, request.getUserAttrs(), operator, now);

        policyTargetAppMapper.delete(new LambdaQueryWrapper<PolicyTargetAppEntity>()
                .eq(PolicyTargetAppEntity::getPolicyId, id));
        saveTargetApps(id, request.getTargetAppIds(), operator, now);

        policyBrowserRuleMapper.delete(new LambdaQueryWrapper<PolicyBrowserRuleEntity>()
                .eq(PolicyBrowserRuleEntity::getPolicyId, id));
        saveBrowserRules(id, request.getBrowserRules(), operator, now);

        policyIpRuleMapper.delete(new LambdaQueryWrapper<PolicyIpRuleEntity>()
                .eq(PolicyIpRuleEntity::getPolicyId, id));
        saveIpRules(id, request.getIpRules(), operator, now);
        return toVO(entity);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PolicyVO enable(Long id) {
        return updateStatus(id, PolicyStatus.ENABLED);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PolicyVO disable(Long id) {
        return updateStatus(id, PolicyStatus.DISABLED);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void delete(Long id) {
        getExisting(id);
        policyOrgScopeMapper.delete(new LambdaQueryWrapper<PolicyOrgScopeEntity>()
                .eq(PolicyOrgScopeEntity::getPolicyId, id));
        policyUserAttrMapper.delete(new LambdaQueryWrapper<PolicyUserAttrEntity>()
                .eq(PolicyUserAttrEntity::getPolicyId, id));
        policyTargetAppMapper.delete(new LambdaQueryWrapper<PolicyTargetAppEntity>()
                .eq(PolicyTargetAppEntity::getPolicyId, id));
        policyBrowserRuleMapper.delete(new LambdaQueryWrapper<PolicyBrowserRuleEntity>()
                .eq(PolicyBrowserRuleEntity::getPolicyId, id));
        policyIpRuleMapper.delete(new LambdaQueryWrapper<PolicyIpRuleEntity>()
                .eq(PolicyIpRuleEntity::getPolicyId, id));
        policyGrantMapper.delete(new LambdaQueryWrapper<PolicyGrantEntity>()
                .eq(PolicyGrantEntity::getPolicyId, id));
        policyMapper.deleteById(id);
    }

    /**
     * 修改策略状态。
     *
     * @param id     策略 id
     * @param status 目标状态
     * @return 更新后的策略规则详情
     */
    private PolicyVO updateStatus(Long id, int status) {
        PolicyEntity entity = getExisting(id);
        entity.setStatus(status);
        entity.setUpdateBy(resolveOperator());
        entity.setUpdateTime(LocalDateTime.now());
        policyMapper.updateById(entity);
        return toVO(entity);
    }

    /**
     * 按 id 查询策略规则实体，不存在时抛出业务异常。
     *
     * @param id 策略 id
     * @return 策略规则实体
     */
    private PolicyEntity getExisting(Long id) {
        PolicyEntity entity = policyMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("策略规则不存在");
        }
        return entity;
    }

    /**
     * 校验组织范围、用户属性条件、请求控制条件（浏览器白名单或 IP 白名单任一非空）不能同时
     * 为空，仅四者全部为空时才拒绝，允许"仅配置请求控制"的策略保存（close-sso-log-and-policy-gaps
     * change design.md Decision 3，反转原 app-access-authorization change design.md
     * Decision 1"两者不能同时为空"的校验）。
     *
     * @param orgScopes    组织范围条件列表
     * @param userAttrs    用户属性条件列表
     * @param browserRules 浏览器白名单编码列表
     * @param ipRules      IP/网段白名单列表
     */
    private void assertScopeAndAttrNotBothEmpty(List<PolicyOrgScopeRequestItem> orgScopes,
            List<PolicyUserAttrRequestItem> userAttrs, List<String> browserRules, List<String> ipRules) {
        boolean orgScopeEmpty = orgScopes == null || orgScopes.isEmpty();
        boolean userAttrEmpty = userAttrs == null || userAttrs.isEmpty();
        boolean browserRulesEmpty = browserRules == null || browserRules.isEmpty();
        boolean ipRulesEmpty = ipRules == null || ipRules.isEmpty();
        if (orgScopeEmpty && userAttrEmpty && browserRulesEmpty && ipRulesEmpty) {
            throw new BusinessException("组织范围、用户属性条件、请求控制条件不能同时为空，请至少配置一类");
        }
    }

    /**
     * 校验用户属性条件请求列表：运算符合法、同一策略内字段不能重复、{@code EQ}/{@code NE}
     * 比较值只能一个、关联的元数据字段存在且属于用户业务域并已启用（spec.md"策略规则的用户
     * 属性条件"需求）。
     *
     * @param userAttrs 用户属性条件请求列表
     */
    private void assertUserAttrsValid(List<PolicyUserAttrRequestItem> userAttrs) {
        if (userAttrs == null || userAttrs.isEmpty()) {
            return;
        }
        Set<Long> seenFieldIds = new HashSet<>();
        for (PolicyUserAttrRequestItem item : userAttrs) {
            if (!seenFieldIds.add(item.getMetadataFieldId())) {
                throw new BusinessException("同一策略内，属性字段不能重复配置");
            }
            if (!PolicyAttrOperator.ALL_OPERATORS.contains(item.getOperator())) {
                throw new BusinessException("非法的运算符：" + item.getOperator());
            }
            if (!PolicyAttrOperator.IN.equals(item.getOperator()) && item.getValues().size() != 1) {
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
     * 校验目标应用不能为空（spec.md"策略规则的定义与维护"需求；Bean Validation 的
     * {@code @NotEmpty} 已在 controller 层拦截绝大多数场景，此处服务层显式兜底，不依赖
     * 调用方一定经过 {@code @Valid} 校验）。
     *
     * @param targetAppIds 目标应用 id 列表
     */
    private void assertTargetAppsNotEmpty(List<Long> targetAppIds) {
        if (targetAppIds == null || targetAppIds.isEmpty()) {
            throw new BusinessException("目标应用不能为空");
        }
    }

    /**
     * 校验请求控制条件（浏览器白名单/IP 白名单）：两者完全可选，不受组织范围/用户属性
     * 条件"至少一项非空"约束影响（spec.md"策略规则的请求控制条件"需求）；浏览器编码须
     * 属于 {@link PolicyRequestControlBrowser#ALL_CODES}，IP/网段条目须能通过
     * {@link IpCidrMatcher#isValidRule} 格式校验，同一策略内两类条目均不允许重复。
     *
     * @param browserRules 浏览器白名单编码列表，可为空
     * @param ipRules      IP/网段白名单列表，可为空
     */
    private void assertRequestControlValid(List<String> browserRules, List<String> ipRules) {
        if (browserRules != null && !browserRules.isEmpty()) {
            Set<String> seenCodes = new HashSet<>();
            for (String browserCode : browserRules) {
                if (!PolicyRequestControlBrowser.ALL_CODES.contains(browserCode)) {
                    throw new BusinessException("非法的浏览器编码：" + browserCode);
                }
                if (!seenCodes.add(browserCode)) {
                    throw new BusinessException("同一策略内，浏览器白名单不允许重复配置");
                }
            }
        }
        if (ipRules != null && !ipRules.isEmpty()) {
            Set<String> seenRules = new HashSet<>();
            for (String ipCidr : ipRules) {
                if (!IpCidrMatcher.isValidRule(ipCidr)) {
                    throw new BusinessException("非法的 IP/网段格式：" + ipCidr);
                }
                if (!seenRules.add(ipCidr)) {
                    throw new BusinessException("同一策略内，IP/网段白名单不允许重复配置");
                }
            }
        }
    }

    /**
     * 保存组织范围条件，同一请求列表内 {@code orgId} 不允许重复。
     *
     * @param policyId  策略 id
     * @param orgScopes 组织范围条件请求列表，可为空
     * @param operator  操作人
     * @param now       操作时间
     */
    private void saveOrgScopes(Long policyId, List<PolicyOrgScopeRequestItem> orgScopes, String operator,
            LocalDateTime now) {
        if (orgScopes == null || orgScopes.isEmpty()) {
            return;
        }
        Set<Long> seenOrgIds = new HashSet<>();
        for (PolicyOrgScopeRequestItem item : orgScopes) {
            if (!seenOrgIds.add(item.getOrgId())) {
                throw new BusinessException("同一策略内，组织范围不允许重复配置");
            }
            policyOrgScopeMapper.insert(PolicyOrgScopeEntity.builder()
                    .policyId(policyId)
                    .orgId(item.getOrgId())
                    .includeChildren(item.getIncludeChildren())
                    .createBy(operator)
                    .createTime(now)
                    .updateBy(operator)
                    .updateTime(now)
                    .build());
        }
    }

    /**
     * 保存用户属性条件，{@code IN} 运算符的比较值序列化为逗号分隔字符串落库（design.md
     * Decision 1）。
     *
     * @param policyId  策略 id
     * @param userAttrs 用户属性条件请求列表，可为空
     * @param operator  操作人
     * @param now       操作时间
     */
    private void saveUserAttrs(Long policyId, List<PolicyUserAttrRequestItem> userAttrs, String operator,
            LocalDateTime now) {
        if (userAttrs == null || userAttrs.isEmpty()) {
            return;
        }
        for (PolicyUserAttrRequestItem item : userAttrs) {
            String attrValue = String.join(",", item.getValues());
            policyUserAttrMapper.insert(PolicyUserAttrEntity.builder()
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
     * 保存目标应用，同一请求列表内 {@code appId} 不允许重复。
     *
     * @param policyId     策略 id
     * @param targetAppIds 目标应用 id 列表，不能为空
     * @param operator     操作人
     * @param now          操作时间
     */
    private void saveTargetApps(Long policyId, List<Long> targetAppIds, String operator, LocalDateTime now) {
        Set<Long> seenAppIds = new HashSet<>();
        for (Long appId : targetAppIds) {
            if (!seenAppIds.add(appId)) {
                throw new BusinessException("同一策略内，目标应用不允许重复配置");
            }
            policyTargetAppMapper.insert(PolicyTargetAppEntity.builder()
                    .policyId(policyId)
                    .appId(appId)
                    .createBy(operator)
                    .createTime(now)
                    .updateBy(operator)
                    .updateTime(now)
                    .build());
        }
    }

    /**
     * 保存请求控制条件-浏览器白名单，完全可选，{@link #assertRequestControlValid} 已保证
     * 合法性与去重，此处只负责落库。
     *
     * @param policyId     策略 id
     * @param browserRules 浏览器白名单编码列表，可为空
     * @param operator     操作人
     * @param now          操作时间
     */
    private void saveBrowserRules(Long policyId, List<String> browserRules, String operator, LocalDateTime now) {
        if (browserRules == null || browserRules.isEmpty()) {
            return;
        }
        for (String browserCode : browserRules) {
            policyBrowserRuleMapper.insert(PolicyBrowserRuleEntity.builder()
                    .policyId(policyId)
                    .browserCode(browserCode)
                    .createBy(operator)
                    .createTime(now)
                    .updateBy(operator)
                    .updateTime(now)
                    .build());
        }
    }

    /**
     * 保存请求控制条件-IP/网段白名单，完全可选，{@link #assertRequestControlValid} 已保证
     * 合法性与去重，此处只负责落库。
     *
     * @param policyId 策略 id
     * @param ipRules  IP/网段白名单列表，可为空
     * @param operator 操作人
     * @param now      操作时间
     */
    private void saveIpRules(Long policyId, List<String> ipRules, String operator, LocalDateTime now) {
        if (ipRules == null || ipRules.isEmpty()) {
            return;
        }
        for (String ipCidr : ipRules) {
            policyIpRuleMapper.insert(PolicyIpRuleEntity.builder()
                    .policyId(policyId)
                    .ipCidr(ipCidr)
                    .createBy(operator)
                    .createTime(now)
                    .updateBy(operator)
                    .updateTime(now)
                    .build());
        }
    }

    /**
     * 组装策略规则详情视图对象：基础字段通过 {@link PolicyConvert} 转换，组织范围/用户
     * 属性条件/目标应用/请求控制条件通过各自的查询回填，并计算"配置是否已变更待重新执行"
     * 标记（请求控制条件不参与该计算，见 app-access-request-control change tasks.md
     * 3.4）。
     *
     * @param entity 策略规则实体
     * @return 策略规则视图对象
     */
    private PolicyVO toVO(PolicyEntity entity) {
        PolicyVO vo = PolicyConvert.INSTANCE.toVO(entity);
        List<PolicyOrgScopeVO> orgScopes = policyOrgScopeMapper.selectByPolicyId(entity.getId());
        List<PolicyUserAttrRow> attrRows = policyUserAttrMapper.selectByPolicyId(entity.getId());
        List<PolicyUserAttrVO> userAttrs = new ArrayList<>(attrRows.size());
        for (PolicyUserAttrRow row : attrRows) {
            userAttrs.add(PolicyUserAttrVO.builder()
                    .metadataFieldId(row.getMetadataFieldId())
                    .fieldName(row.getFieldName())
                    .fieldCode(row.getFieldCode())
                    .operator(row.getOperator())
                    .values(Arrays.asList(row.getAttrValue().split(",")))
                    .updateTime(row.getUpdateTime())
                    .build());
        }
        List<PolicyTargetAppVO> targetApps = policyTargetAppMapper.selectByPolicyId(entity.getId());
        LambdaQueryWrapper<PolicyBrowserRuleEntity> browserRuleWrapper = new LambdaQueryWrapper<>();
        browserRuleWrapper.eq(PolicyBrowserRuleEntity::getPolicyId, entity.getId());
        List<PolicyBrowserRuleVO> browserRules = policyBrowserRuleMapper.selectList(browserRuleWrapper).stream()
                .map(rule -> PolicyBrowserRuleVO.builder()
                        .browserCode(rule.getBrowserCode())
                        .browserLabel(PolicyRequestControlBrowser.label(rule.getBrowserCode()))
                        .build())
                .toList();
        LambdaQueryWrapper<PolicyIpRuleEntity> ipRuleWrapper = new LambdaQueryWrapper<PolicyIpRuleEntity>()
                .eq(PolicyIpRuleEntity::getPolicyId, entity.getId());
        List<PolicyIpRuleVO> ipRules = policyIpRuleMapper.selectList(ipRuleWrapper).stream()
                .map(rule -> PolicyIpRuleVO.builder().ipCidr(rule.getIpCidr()).build())
                .toList();

        vo.setOrgScopes(orgScopes);
        vo.setUserAttrs(userAttrs);
        vo.setTargetApps(targetApps);
        vo.setBrowserRules(browserRules);
        vo.setIpRules(ipRules);
        vo.setPendingReExecute(computePendingReExecute(entity.getLastExecTime(), orgScopes, userAttrs, targetApps));
        return vo;
    }

    /**
     * 计算"配置是否已变更待重新执行"标记：{@code lastExecTime} 非空，且组织范围/用户属性
     * 条件/目标应用中任一条最新更新时间晚于 {@code lastExecTime}（design.md Decision 4，
     * 纯查询判断，不新增状态字段）。
     *
     * @param lastExecTime 最近一次执行时间，未执行过为空
     * @param orgScopes    组织范围条件列表
     * @param userAttrs    用户属性条件列表
     * @param targetApps   目标应用列表
     * @return {@code true} 表示配置已变更，待重新执行
     */
    private boolean computePendingReExecute(LocalDateTime lastExecTime, List<PolicyOrgScopeVO> orgScopes,
            List<PolicyUserAttrVO> userAttrs, List<PolicyTargetAppVO> targetApps) {
        if (lastExecTime == null) {
            return false;
        }
        LocalDateTime maxUpdateTime = null;
        for (PolicyOrgScopeVO item : orgScopes) {
            maxUpdateTime = laterOf(maxUpdateTime, item.getUpdateTime());
        }
        for (PolicyUserAttrVO item : userAttrs) {
            maxUpdateTime = laterOf(maxUpdateTime, item.getUpdateTime());
        }
        for (PolicyTargetAppVO item : targetApps) {
            maxUpdateTime = laterOf(maxUpdateTime, item.getUpdateTime());
        }
        return maxUpdateTime != null && maxUpdateTime.isAfter(lastExecTime);
    }

    /**
     * 返回两个可能为空的时间中较晚的一个。
     *
     * @param current  当前记录的最晚时间，可为空
     * @param candidate 待比较的时间
     * @return 较晚的时间
     */
    private LocalDateTime laterOf(LocalDateTime current, LocalDateTime candidate) {
        if (current == null) {
            return candidate;
        }
        if (candidate == null) {
            return current;
        }
        return candidate.isAfter(current) ? candidate : current;
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
