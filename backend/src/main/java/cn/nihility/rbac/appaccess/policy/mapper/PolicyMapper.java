package cn.nihility.rbac.appaccess.policy.mapper;

import cn.nihility.rbac.appaccess.policy.entity.PolicyEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 策略规则 MyBatis-Plus 数据访问接口，单表 CRUD（新增/详情/分页/启停用/删除/更新执行
 * 信息）直接复用 {@link BaseMapper}，不在此处编写 SQL。
 */
@Mapper
public interface PolicyMapper extends BaseMapper<PolicyEntity> {
}
