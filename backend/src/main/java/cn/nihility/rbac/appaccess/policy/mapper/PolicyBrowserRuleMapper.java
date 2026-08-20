package cn.nihility.rbac.appaccess.policy.mapper;

import cn.nihility.rbac.appaccess.policy.entity.PolicyBrowserRuleEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 策略请求控制条件-浏览器白名单数据访问接口。单表 CRUD（整体替换用的批量插入/按
 * {@code policyId} 删除、按单个/多个 {@code policyId} 批量查询）直接复用
 * {@link BaseMapper}（{@code selectList} 配合 {@code LambdaQueryWrapper#in} 即可一次性
 * 批量取多个候选策略的规则，避免 N+1），不涉及跨表 JOIN，故不新增自定义 XML SQL。
 */
@Mapper
public interface PolicyBrowserRuleMapper extends BaseMapper<PolicyBrowserRuleEntity> {
}
