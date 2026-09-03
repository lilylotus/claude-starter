package cn.nihility.rbac.userrole.mapper;

import cn.nihility.rbac.userrole.entity.UserRoleRuleEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户角色规则数据访问接口。单表 CRUD 直接复用 {@link BaseMapper}，不需要额外的联表 SQL。
 */
@Mapper
public interface UserRoleRuleMapper extends BaseMapper<UserRoleRuleEntity> {
}
