package cn.nihility.rbac.abac.mapper;

import cn.nihility.rbac.abac.entity.AbacPolicyResourceEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * ABAC 策略目标资源编码数据访问接口。单表 CRUD（整体替换用的批量插入/按
 * {@code policyId} 删除、按 {@code permissionCode} 查询候选策略、按一组 {@code policyId}
 * 批量取资源编码回显）直接复用 {@link BaseMapper}（{@code selectList} 配合
 * {@code LambdaQueryWrapper} 即可一次性批量取多个候选策略的资源编码，避免 N+1），不涉及
 * 跨表 JOIN，故不新增自定义 XML SQL。
 */
@Mapper
public interface AbacPolicyResourceMapper extends BaseMapper<AbacPolicyResourceEntity> {
}
