package cn.nihility.rbac.dict.mapper;

import cn.nihility.rbac.dict.entity.DictItemEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 字典项 MyBatis-Plus 数据访问接口，单表 CRUD 直接复用 {@link BaseMapper}，
 * 不在此处编写 SQL。
 */
@Mapper
public interface DictItemMapper extends BaseMapper<DictItemEntity> {
}
