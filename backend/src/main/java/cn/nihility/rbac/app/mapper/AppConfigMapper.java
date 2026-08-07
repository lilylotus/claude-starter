package cn.nihility.rbac.app.mapper;

import cn.nihility.rbac.app.entity.AppConfigEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 应用对外接口凭证配置 MyBatis-Plus 数据访问接口，单表 CRUD 直接复用 {@link BaseMapper}，
 * 不需要自定义方法。
 */
@Mapper
public interface AppConfigMapper extends BaseMapper<AppConfigEntity> {
}
