package cn.nihility.rbac.abac.service.impl;

import cn.nihility.rbac.abac.constant.AbacPolicyStatus;
import cn.nihility.rbac.abac.entity.AbacPolicyEntity;
import cn.nihility.rbac.abac.entity.AbacPolicyResourceEntity;
import cn.nihility.rbac.abac.entity.AbacPolicyTimeWindowEntity;
import cn.nihility.rbac.abac.entity.AbacPolicyUserAttrEntity;
import cn.nihility.rbac.abac.mapper.AbacPolicyMapper;
import cn.nihility.rbac.abac.mapper.AbacPolicyResourceMapper;
import cn.nihility.rbac.abac.mapper.AbacPolicyTimeWindowMapper;
import cn.nihility.rbac.abac.mapper.AbacPolicyUserAttrMapper;
import cn.nihility.rbac.abac.service.AbacAuthorizationService;
import cn.nihility.rbac.abac.support.AbacTimeWindowEvaluator;
import cn.nihility.rbac.abac.support.AbacUserAttrEvaluator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * ABAC 运行时限制判定业务逻辑实现（abac-policy-engine change design.md Decision 5）：
 * ① 查询资源编码命中、状态为启用的候选策略，候选为空时直接返回不拒绝，不做任何属性/
 * 时间查询（保证未配置 ABAC 的资源零额外开销）；② 逐条候选批量查询用户属性条件、时间
 * 窗口条件（按策略 id 分组，避免 N+1），判断是否命中（用户属性条件维度与时间窗口条件
 * 维度均满足，未配置某一类维度时该维度视为恒满足）；③ 候选策略中存在至少一条命中时
 * 判定为拒绝，否则判定为不拒绝。每次请求实时查询，不引入缓存。
 */
@Service
@RequiredArgsConstructor
public class AbacAuthorizationServiceImpl implements AbacAuthorizationService {

    /** 策略规则数据访问接口，查询候选策略的启用状态。 */
    private final AbacPolicyMapper abacPolicyMapper;

    /** 策略目标资源编码数据访问接口，按资源编码查询命中的候选策略 id。 */
    private final AbacPolicyResourceMapper abacPolicyResourceMapper;

    /** 策略用户属性条件数据访问接口，批量查询候选策略的用户属性条件。 */
    private final AbacPolicyUserAttrMapper abacPolicyUserAttrMapper;

    /** 策略时间窗口条件数据访问接口，批量查询候选策略的时间窗口条件。 */
    private final AbacPolicyTimeWindowMapper abacPolicyTimeWindowMapper;

    /** 用户属性条件单用户实时判定组件。 */
    private final AbacUserAttrEvaluator abacUserAttrEvaluator;

    /** 时间窗口条件判定组件。 */
    private final AbacTimeWindowEvaluator abacTimeWindowEvaluator;

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isDenied(Long userId, String resourceCode) {
        List<AbacPolicyResourceEntity> resources = abacPolicyResourceMapper.selectList(
                new LambdaQueryWrapper<AbacPolicyResourceEntity>()
                        .eq(AbacPolicyResourceEntity::getPermissionCode, resourceCode));
        if (resources.isEmpty()) {
            return false;
        }

        Set<Long> boundPolicyIds = resources.stream().map(AbacPolicyResourceEntity::getPolicyId)
                .collect(Collectors.toSet());
        List<AbacPolicyEntity> enabledPolicies = abacPolicyMapper.selectList(new LambdaQueryWrapper<AbacPolicyEntity>()
                .in(AbacPolicyEntity::getId, boundPolicyIds)
                .eq(AbacPolicyEntity::getStatus, AbacPolicyStatus.ENABLED));
        if (enabledPolicies.isEmpty()) {
            return false;
        }

        Set<Long> enabledPolicyIds = enabledPolicies.stream().map(AbacPolicyEntity::getId)
                .collect(Collectors.toSet());
        Map<Long, List<AbacPolicyUserAttrEntity>> userAttrsByPolicyId = abacPolicyUserAttrMapper
                .selectList(new LambdaQueryWrapper<AbacPolicyUserAttrEntity>()
                        .in(AbacPolicyUserAttrEntity::getPolicyId, enabledPolicyIds))
                .stream()
                .collect(Collectors.groupingBy(AbacPolicyUserAttrEntity::getPolicyId));
        Map<Long, List<AbacPolicyTimeWindowEntity>> timeWindowsByPolicyId = abacPolicyTimeWindowMapper
                .selectList(new LambdaQueryWrapper<AbacPolicyTimeWindowEntity>()
                        .in(AbacPolicyTimeWindowEntity::getPolicyId, enabledPolicyIds))
                .stream()
                .collect(Collectors.groupingBy(AbacPolicyTimeWindowEntity::getPolicyId));

        for (Long policyId : enabledPolicyIds) {
            boolean userAttrSatisfied = abacUserAttrEvaluator.matchesAll(userId, userAttrsByPolicyId.get(policyId));
            boolean timeWindowSatisfied = abacTimeWindowEvaluator.matchesAny(timeWindowsByPolicyId.get(policyId));
            if (userAttrSatisfied && timeWindowSatisfied) {
                return true;
            }
        }
        return false;
    }
}
