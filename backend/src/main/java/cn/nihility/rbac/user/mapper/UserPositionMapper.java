package cn.nihility.rbac.user.mapper;

import cn.nihility.rbac.user.entity.UserPositionEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户任职记录 MyBatis-Plus 数据访问接口，单表 CRUD 直接复用 {@link BaseMapper}，
 * 不在此处编写 SQL。
 */
@Mapper
public interface UserPositionMapper extends BaseMapper<UserPositionEntity> {
}
