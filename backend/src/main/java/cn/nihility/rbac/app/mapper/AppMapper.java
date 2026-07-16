package cn.nihility.rbac.app.mapper;

import cn.nihility.rbac.app.entity.AppEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 应用 MyBatis-Plus 数据访问接口，单表 CRUD 直接复用 {@link BaseMapper}，不在此处编写 SQL。
 */
@Mapper
public interface AppMapper extends BaseMapper<AppEntity> {
}
