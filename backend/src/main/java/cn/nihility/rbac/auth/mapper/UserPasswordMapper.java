package cn.nihility.rbac.auth.mapper;

import cn.nihility.rbac.auth.entity.UserPasswordEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户密码 MyBatis-Plus 数据访问接口，单表 CRUD 直接复用 {@link BaseMapper}，不在此处编写 SQL。
 */
@Mapper
public interface UserPasswordMapper extends BaseMapper<UserPasswordEntity> {
}
