package cn.nihility.rbac.abac.mapper;

import cn.nihility.rbac.abac.entity.AbacPolicyTimeWindowEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * ABAC 策略时间窗口条件数据访问接口。单表 CRUD（整体替换用的批量插入/按
 * {@code policyId} 删除、按一组 {@code policyId} 批量查询）直接复用 {@link BaseMapper}
 * （{@code selectList} 配合 {@code LambdaQueryWrapper#in} 即可一次性批量取多个候选策略的
 * 时间窗口，避免 N+1），不涉及跨表 JOIN，故不新增自定义 XML SQL。
 */
@Mapper
public interface AbacPolicyTimeWindowMapper extends BaseMapper<AbacPolicyTimeWindowEntity> {
}
