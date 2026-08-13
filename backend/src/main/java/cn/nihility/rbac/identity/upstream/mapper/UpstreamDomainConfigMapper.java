package cn.nihility.rbac.identity.upstream.mapper;

import cn.nihility.rbac.identity.upstream.entity.UpstreamDomainConfigEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 上游数据源数据域配置 MyBatis-Plus 数据访问接口，单表 CRUD 直接复用 {@link BaseMapper}，
 * 不在此处编写 SQL。
 */
@Mapper
public interface UpstreamDomainConfigMapper extends BaseMapper<UpstreamDomainConfigEntity> {
}
