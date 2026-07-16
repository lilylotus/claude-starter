package cn.nihility.rbac.role.mapper;

import cn.nihility.rbac.role.entity.RoleEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 角色 MyBatis-Plus 数据访问接口，单表 CRUD 直接复用 {@link BaseMapper}，不在此处编写 SQL。
 */
@Mapper
public interface RoleMapper extends BaseMapper<RoleEntity> {
}
