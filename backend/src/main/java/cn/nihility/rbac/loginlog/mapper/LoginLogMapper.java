package cn.nihility.rbac.loginlog.mapper;

import cn.nihility.rbac.loginlog.entity.LoginLogEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 登录日志数据访问接口。单表 CRUD 直接复用 {@link BaseMapper}，分页查询按可选条件
 * 动态拼接的 {@code LambdaQueryWrapper} 即可满足需求，不需要自定义 XML。
 */
@Mapper
public interface LoginLogMapper extends BaseMapper<LoginLogEntity> {
}
