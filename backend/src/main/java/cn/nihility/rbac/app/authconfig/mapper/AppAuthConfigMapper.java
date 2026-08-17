package cn.nihility.rbac.app.authconfig.mapper;

import cn.nihility.rbac.app.authconfig.entity.AppAuthConfigEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 应用单点登录协议配置数据访问接口。
 */
@Mapper
public interface AppAuthConfigMapper extends BaseMapper<AppAuthConfigEntity> {
}
