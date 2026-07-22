package cn.nihility.rbac.metadata.mapper;

import cn.nihility.rbac.metadata.entity.MetadataFieldEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 元数据字段配置 MyBatis-Plus 数据访问接口，单表 CRUD 直接复用 {@link BaseMapper}，
 * 不在此处编写 SQL。也供 {@code cn.nihility.rbac.formfield} 模块直接注入，按需查询
 * 元数据字段的 {@code tableName}/{@code columnName}/{@code bizType} 等物理属性
 * （沿用本项目"跨模块只读查询直接复用对方 Mapper"的既有约定，如
 * {@code AppServiceImpl} 直接注入 {@code OrgMapper}/{@code UserMapper}）。
 */
@Mapper
public interface MetadataFieldMapper extends BaseMapper<MetadataFieldEntity> {
}
