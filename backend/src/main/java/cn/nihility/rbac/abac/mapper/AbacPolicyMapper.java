package cn.nihility.rbac.abac.mapper;

import cn.nihility.rbac.abac.entity.AbacPolicyEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * ABAC 策略规则 MyBatis-Plus 数据访问接口，单表 CRUD（新增/详情/分页/启停用/删除）
 * 直接复用 {@link BaseMapper}，不在此处编写 SQL。
 */
@Mapper
public interface AbacPolicyMapper extends BaseMapper<AbacPolicyEntity> {
}
