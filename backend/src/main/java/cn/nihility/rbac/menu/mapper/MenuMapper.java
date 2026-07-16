package cn.nihility.rbac.menu.mapper;

import cn.nihility.rbac.menu.entity.MenuEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 资源 MyBatis-Plus 数据访问接口，单表 CRUD 直接复用 {@link BaseMapper}，
 * 不在此处编写 SQL。
 */
@Mapper
public interface MenuMapper extends BaseMapper<MenuEntity> {
}
