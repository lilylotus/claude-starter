package cn.nihility.rbac.chat.mapper;

import cn.nihility.rbac.chat.entity.SensitiveWordEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 敏感词 MyBatis-Plus 数据访问接口，单表 CRUD 与分页查询直接复用 {@link BaseMapper}，
 * 无需自定义 SQL。
 */
@Mapper
public interface SensitiveWordMapper extends BaseMapper<SensitiveWordEntity> {
}
