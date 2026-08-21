package cn.nihility.rbac.appaccess.support.impl;

import cn.nihility.rbac.appaccess.constant.EffectiveSourceType;
import cn.nihility.rbac.appaccess.dto.AppAccessEffectiveItemVO;
import cn.nihility.rbac.appaccess.override.constant.OverrideType;
import cn.nihility.rbac.appaccess.override.entity.ManualOverrideEntity;
import cn.nihility.rbac.appaccess.override.mapper.ManualOverrideMapper;
import cn.nihility.rbac.appaccess.policy.constant.PolicyRequestControlBrowser;
import cn.nihility.rbac.appaccess.policy.entity.PolicyBrowserRuleEntity;
import cn.nihility.rbac.appaccess.policy.entity.PolicyIpRuleEntity;
import cn.nihility.rbac.appaccess.policy.mapper.PolicyBrowserRuleMapper;
import cn.nihility.rbac.appaccess.policy.mapper.PolicyGrantMapper;
import cn.nihility.rbac.appaccess.policy.mapper.PolicyIpRuleMapper;
import cn.nihility.rbac.appaccess.support.AppAccessAuthorizationDecision;
import cn.nihility.rbac.appaccess.support.AppAccessEffectivePermissionService;
import cn.nihility.rbac.appaccess.support.IpCidrMatcher;
import cn.nihility.rbac.appaccess.support.dto.EffectiveRawRow;
import cn.nihility.rbac.appaccess.support.mapper.AppAccessEffectiveMapper;
import cn.nihility.rbac.common.result.PageResult;
import cn.nihility.rbac.operationlog.util.UserAgentParser;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 最终生效权限计算与查询业务逻辑实现（design.md Decision 2/3；app-access-request-control
 * change design.md Decision 3 新增"考虑请求上下文"的判定入口；policy-condition-exclusive
 * -priority change design.md Decision 将"考虑请求上下文"的候选策略判定改为"取排在最前的
 * 一条，非黑即白"，并新增 {@link #checkAuthorization} 返回拒绝来源的策略 id）。
 */
@Service
@RequiredArgsConstructor
public class AppAccessEffectivePermissionServiceImpl implements AppAccessEffectivePermissionService {

    /** 人工例外数据访问接口。 */
    private final ManualOverrideMapper manualOverrideMapper;

    /** 策略计算结果数据访问接口。 */
    private final PolicyGrantMapper policyGrantMapper;

    /** 最终生效权限查询专用数据访问接口。 */
    private final AppAccessEffectiveMapper appAccessEffectiveMapper;

    /** 策略请求控制条件-浏览器白名单数据访问接口（app-access-request-control change）。 */
    private final PolicyBrowserRuleMapper policyBrowserRuleMapper;

    /** 策略请求控制条件-IP/网段白名单数据访问接口（app-access-request-control change）。 */
    private final PolicyIpRuleMapper policyIpRuleMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isAuthorized(Long userId, Long appId) {
        Boolean overrideDecision = resolveOverrideDecision(userId, appId);
        if (overrideDecision != null) {
            return overrideDecision;
        }
        return policyGrantMapper.existsActiveGrant(userId, appId) > 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isAuthorized(Long userId, Long appId, String clientIp, String userAgent) {
        return checkAuthorization(userId, appId, clientIp, userAgent).authorized();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AppAccessAuthorizationDecision checkAuthorization(Long userId, Long appId, String clientIp,
            String userAgent) {
        Boolean overrideDecision = resolveOverrideDecision(userId, appId);
        if (overrideDecision != null) {
            return overrideDecision ? AppAccessAuthorizationDecision.allow()
                    : AppAccessAuthorizationDecision.denyWithoutPolicy();
        }

        // 候选策略已按 show_order 升序、id 升序排列（PolicyGrantMapper.xml），只取排在
        // 最前的一条，由它单独决定放行或拒绝，不再遍历其余候选（design.md Decision：
        // "取第一条，非黑即白"，取代原"任一候选满足即放行"的并集语义）。
        List<Long> candidatePolicyIds = policyGrantMapper.selectActivePolicyIds(userId, appId);
        if (candidatePolicyIds.isEmpty()) {
            return AppAccessAuthorizationDecision.denyWithoutPolicy();
        }
        Long topPolicyId = candidatePolicyIds.get(0);

        LambdaQueryWrapper<PolicyBrowserRuleEntity> browserRuleWrapper = new LambdaQueryWrapper<PolicyBrowserRuleEntity>()
                .eq(PolicyBrowserRuleEntity::getPolicyId, topPolicyId);
        List<String> browserWhitelist = policyBrowserRuleMapper.selectList(browserRuleWrapper).stream()
                .map(PolicyBrowserRuleEntity::getBrowserCode).toList();

        LambdaQueryWrapper<PolicyIpRuleEntity> ipRuleWrapper = new LambdaQueryWrapper<PolicyIpRuleEntity>()
                .eq(PolicyIpRuleEntity::getPolicyId, topPolicyId);
        List<String> ipWhitelist = policyIpRuleMapper.selectList(ipRuleWrapper).stream()
                .map(PolicyIpRuleEntity::getIpCidr).toList();

        String browserCode = PolicyRequestControlBrowser.resolveCode(UserAgentParser.parseBrowser(userAgent));
        boolean browserSatisfied = browserWhitelist.isEmpty()
                || (browserCode != null && browserWhitelist.contains(browserCode));
        boolean ipSatisfied = ipWhitelist.isEmpty()
                || ipWhitelist.stream().anyMatch(rule -> IpCidrMatcher.matches(clientIp, rule));

        if (browserSatisfied && ipSatisfied) {
            return AppAccessAuthorizationDecision.allow();
        }
        return AppAccessAuthorizationDecision.denyByPolicy(topPolicyId);
    }

    /**
     * 解析人工例外优先级判定：存在 {@code DENY} 例外返回 {@code false}，存在 {@code GRANT}
     * 例外返回 {@code true}，不存在人工例外返回 {@code null}（由调用方继续按策略授权记录
     * 判定），供 {@code isAuthorized(Long, Long)} 与 {@code checkAuthorization} 共享，
     * 避免重复实现"取候选策略 id 集合"之前的这段逻辑（design.md Decision 3）。
     *
     * @param userId 用户 id
     * @param appId  应用 id
     * @return {@code true}/{@code false} 表示人工例外已给出最终判定，{@code null} 表示
     *         不存在人工例外
     */
    private Boolean resolveOverrideDecision(Long userId, Long appId) {
        ManualOverrideEntity override = manualOverrideMapper.selectOne(new LambdaQueryWrapper<ManualOverrideEntity>()
                .eq(ManualOverrideEntity::getUserId, userId)
                .eq(ManualOverrideEntity::getAppId, appId));
        if (override == null) {
            return null;
        }
        // 人工例外优先级最高：GRANT 直接放行（不看请求上下文），DENY 直接拒绝，均不再看
        // 策略授权/请求控制。
        return OverrideType.GRANT.equals(override.getOverrideType());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResult<AppAccessEffectiveItemVO> listEffectiveByUser(Long userId, boolean includeRevoked, int page,
            int pageSize) {
        List<EffectiveRawRow> grantRows = appAccessEffectiveMapper.selectActiveGrantRowsByUser(userId);
        List<EffectiveRawRow> manualOnlyRows = appAccessEffectiveMapper.selectManualOnlyRowsByUser(userId);

        Map<Long, Accumulator> byAppId = new LinkedHashMap<>();
        for (EffectiveRawRow row : grantRows) {
            Accumulator acc = byAppId.computeIfAbsent(row.getAppId(), key -> new Accumulator(key, row.getAppName()));
            acc.policyNames.add(row.getPolicyName());
            acc.hasRequestControl = acc.hasRequestControl || Boolean.TRUE.equals(row.getHasRequestControl());
            if (row.getOverrideType() != null) {
                acc.overrideType = row.getOverrideType();
            }
        }
        for (EffectiveRawRow row : manualOnlyRows) {
            Accumulator acc = byAppId.computeIfAbsent(row.getAppId(), key -> new Accumulator(key, row.getAppName()));
            acc.overrideType = row.getOverrideType();
        }

        List<AppAccessEffectiveItemVO> items = new ArrayList<>();
        for (Accumulator acc : byAppId.values()) {
            String sourceType = acc.resolveSourceType(includeRevoked);
            if (sourceType == null) {
                continue;
            }
            items.add(AppAccessEffectiveItemVO.builder()
                    .userId(userId)
                    .appId(acc.id)
                    .appName(acc.name)
                    .sourceType(sourceType)
                    .policyNames(EffectiveSourceType.MANUAL_GRANT.equals(sourceType) ? List.of() : acc.policyNames)
                    .hasRequestControl(EffectiveSourceType.POLICY.equals(sourceType) && acc.hasRequestControl)
                    .build());
        }
        return paginate(items, page, pageSize);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResult<AppAccessEffectiveItemVO> listEffectiveByApp(Long appId, int page, int pageSize) {
        List<EffectiveRawRow> grantRows = appAccessEffectiveMapper.selectActiveGrantRowsByApp(appId);
        List<EffectiveRawRow> manualOnlyRows = appAccessEffectiveMapper.selectManualOnlyRowsByApp(appId);

        Map<Long, Accumulator> byUserId = new LinkedHashMap<>();
        for (EffectiveRawRow row : grantRows) {
            Accumulator acc = byUserId.computeIfAbsent(row.getUserId(), key -> new Accumulator(key, row.getUserName()));
            acc.policyNames.add(row.getPolicyName());
            acc.hasRequestControl = acc.hasRequestControl || Boolean.TRUE.equals(row.getHasRequestControl());
            if (row.getOverrideType() != null) {
                acc.overrideType = row.getOverrideType();
            }
        }
        for (EffectiveRawRow row : manualOnlyRows) {
            Accumulator acc = byUserId.computeIfAbsent(row.getUserId(), key -> new Accumulator(key, row.getUserName()));
            acc.overrideType = row.getOverrideType();
        }

        // 按应用查询不支持"包含已收回"，DENY 例外覆盖的用户直接排除（spec.md"最终生效权限
        // 查询"需求只对按用户查询开放该审计选项）。
        List<AppAccessEffectiveItemVO> items = new ArrayList<>();
        for (Accumulator acc : byUserId.values()) {
            String sourceType = acc.resolveSourceType(false);
            if (sourceType == null) {
                continue;
            }
            items.add(AppAccessEffectiveItemVO.builder()
                    .appId(appId)
                    .userId(acc.id)
                    .userName(acc.name)
                    .sourceType(sourceType)
                    .policyNames(EffectiveSourceType.MANUAL_GRANT.equals(sourceType) ? List.of() : acc.policyNames)
                    .hasRequestControl(EffectiveSourceType.POLICY.equals(sourceType) && acc.hasRequestControl)
                    .build());
        }
        return paginate(items, page, pageSize);
    }

    /**
     * 对合并计算后的完整结果列表做内存分页（结果集规模有限，见 design.md Decision 3）。
     *
     * @param all      完整结果列表
     * @param page     页码，从 1 开始
     * @param pageSize 每页条数
     * @return 分页结果
     */
    private PageResult<AppAccessEffectiveItemVO> paginate(List<AppAccessEffectiveItemVO> all, int page, int pageSize) {
        int total = all.size();
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        int fromIndex = Math.min((safePage - 1) * safePageSize, total);
        int toIndex = Math.min(fromIndex + safePageSize, total);
        return new PageResult<>(all.subList(fromIndex, toIndex), (long) total, safePage, safePageSize);
    }

    /**
     * 按 {@code appId}（按用户查询场景）或 {@code userId}（按应用查询场景）分组的中间累加
     * 状态：命中的策略名称列表与（若存在）该用户+应用的人工例外类型，据此应用 DENY 优先于
     * GRANT 优先于 POLICY 的合并判定规则；{@code hasRequestControl} 记录命中的策略中是否
     * 存在任意一条配置了请求控制条件（app-access-request-control change design.md
     * Decision 5）。
     */
    private static final class Accumulator {

        /** 分组 key（{@code appId} 或 {@code userId}）。 */
        private final Long id;

        /** 分组展示名称（应用名称或用户姓名）。 */
        private final String name;

        /** 命中的策略名称列表。 */
        private final List<String> policyNames = new ArrayList<>();

        /** 人工例外类型：GRANT/DENY，不存在人工例外时为 {@code null}。 */
        private String overrideType;

        /** 命中的策略中是否存在任意一条配置了请求控制条件。 */
        private boolean hasRequestControl;

        private Accumulator(Long id, String name) {
            this.id = id;
            this.name = name;
        }

        /**
         * 应用合并判定规则解析本条最终结果的判定依据。
         *
         * @param includeRevoked 是否需要返回"曾经可能可访问但被收回"的条目
         * @return 判定依据；返回 {@code null} 表示本条不出现在最终结果列表中
         */
        private String resolveSourceType(boolean includeRevoked) {
            boolean hasPolicy = !policyNames.isEmpty();
            if (OverrideType.DENY.equals(overrideType)) {
                return includeRevoked && hasPolicy ? EffectiveSourceType.REVOKED : null;
            }
            if (OverrideType.GRANT.equals(overrideType)) {
                return EffectiveSourceType.MANUAL_GRANT;
            }
            return hasPolicy ? EffectiveSourceType.POLICY : null;
        }
    }
}
