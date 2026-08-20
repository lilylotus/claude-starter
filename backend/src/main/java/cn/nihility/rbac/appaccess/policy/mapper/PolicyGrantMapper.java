package cn.nihility.rbac.appaccess.policy.mapper;

import cn.nihility.rbac.appaccess.policy.entity.PolicyGrantEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 策略计算结果（POLICY 来源授权记录）数据访问接口。按 {@code policyId} 整体重建用的
 * 批量插入/删除直接复用 {@link BaseMapper}；{@link #existsActiveGrant}/
 * {@link #selectActivePolicyIds} 需要关联 {@code tab_app_access_policy} 校验策略当前
 * 是否为"启用"状态，SQL 写在 {@code resources/mybatis/mapper/PolicyGrantMapper.xml} 里。
 * 也供 {@code cn.nihility.rbac.sso.support.AppAccessAuthorizationChecker} 直接注入使用
 * （沿用本项目"跨模块只读查询直接复用对方 Mapper"的既有约定）。
 */
@Mapper
public interface PolicyGrantMapper extends BaseMapper<PolicyGrantEntity> {

    /**
     * 判断给定用户对给定应用是否存在至少一条"由当前处于启用状态的策略产生"的策略授权
     * 记录（最终生效权限计算规则的第③优先级，见 spec.md"最终生效权限计算规则"需求）。
     *
     * @param userId 用户 id
     * @param appId  应用 id
     * @return 命中的记录数，{@code > 0} 即存在
     */
    int existsActiveGrant(@Param("userId") Long userId, @Param("appId") Long appId);

    /**
     * 查询给定用户对给定应用、由当前处于启用状态的策略产生的策略授权记录，去重后的
     * {@code policyId} 集合（app-access-request-control change design.md Decision 3，
     * 供"考虑请求上下文"的最终生效权限判定批量取候选策略的请求控制条件）。
     *
     * @param userId 用户 id
     * @param appId  应用 id
     * @return 去重后的候选策略 id 列表，可能为空
     */
    List<Long> selectActivePolicyIds(@Param("userId") Long userId, @Param("appId") Long appId);
}
