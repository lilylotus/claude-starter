package cn.nihility.rbac.formfield.mapper;

import cn.nihility.rbac.formfield.constant.FormFieldStatus;
import cn.nihility.rbac.formfield.entity.FormFieldDefinitionEntity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 表单字段定义 MyBatis-Plus 数据访问接口，单表 CRUD 直接复用 {@link BaseMapper}，
 * 不在此处编写 SQL。也供 {@code cn.nihility.rbac.metadata} 模块直接注入，用于判断
 * 某个元数据字段当前是否被有效表单字段定义占用（沿用本项目"跨模块只读查询直接
 * 复用对方 Mapper"的既有约定）。
 */
@Mapper
public interface FormFieldDefinitionMapper extends BaseMapper<FormFieldDefinitionEntity> {

    /**
     * 判断给定的元数据字段当前是否被至少一条有效（未逻辑删除）的表单字段定义绑定。
     *
     * @param metadataFieldId 元数据字段 id
     * @return 是否存在有效绑定
     */
    default boolean existsActiveByMetadataFieldId(Long metadataFieldId) {
        Long count = selectCount(new LambdaQueryWrapper<FormFieldDefinitionEntity>()
                .eq(FormFieldDefinitionEntity::getMetadataFieldId, metadataFieldId)
                .ne(FormFieldDefinitionEntity::getStatus, FormFieldStatus.DELETED));
        return count != null && count > 0;
    }
}
