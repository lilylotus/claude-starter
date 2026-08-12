package cn.nihility.rbac.app.sync.mapper;

import cn.nihility.rbac.app.sync.entity.AppSyncDomainConfigEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 应用同步数据域配置 MyBatis-Plus 数据访问接口，单表 CRUD 直接复用 {@link BaseMapper}，
 * 不在此处编写 SQL。
 */
@Mapper
public interface AppSyncDomainConfigMapper extends BaseMapper<AppSyncDomainConfigEntity> {
}
