package cn.nihility.rbac.org.mapper;

import cn.nihility.rbac.org.entity.OrgEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 组织机构 MyBatis-Plus 数据访问接口，单表 CRUD 直接复用 {@link BaseMapper}，
 * 不在此处编写 SQL。
 */
@Mapper
public interface OrgMapper extends BaseMapper<OrgEntity> {
}
