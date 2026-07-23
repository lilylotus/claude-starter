package cn.nihility.rbac.excelimport.mapper;

import cn.nihility.rbac.excelimport.constant.ImportFieldConfigStatus;
import cn.nihility.rbac.excelimport.entity.ImportFieldConfigEntity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * Excel 导入字段配置 MyBatis-Plus 数据访问接口，单表 CRUD 直接复用
 * {@link BaseMapper}，不在此处编写 SQL。
 */
@Mapper
public interface ImportFieldConfigMapper extends BaseMapper<ImportFieldConfigEntity> {

    /**
     * 判断给定业务对象类型下，指定字段标识当前是否已被至少一条有效（未逻辑删除）的
     * 导入字段配置占用。
     *
     * @param bizType   业务对象类型
     * @param fieldCode 字段标识
     * @return 是否存在有效占用
     */
    default boolean existsActiveByFieldCode(String bizType, String fieldCode) {
        return existsActiveByFieldCodeExcluding(bizType, fieldCode, null);
    }

    /**
     * 判断给定业务对象类型下，指定字段标识当前是否已被至少一条有效（未逻辑删除）的
     * 导入字段配置占用，排除指定 id 的配置自身（供编辑场景使用）。
     *
     * @param bizType   业务对象类型
     * @param fieldCode 字段标识
     * @param excludeId 需要排除的配置 id，可为 null（不排除）
     * @return 是否存在有效占用
     */
    default boolean existsActiveByFieldCodeExcluding(String bizType, String fieldCode, Long excludeId) {
        LambdaQueryWrapper<ImportFieldConfigEntity> wrapper = new LambdaQueryWrapper<ImportFieldConfigEntity>()
                .eq(ImportFieldConfigEntity::getBizType, bizType)
                .eq(ImportFieldConfigEntity::getFieldCode, fieldCode)
                .ne(ImportFieldConfigEntity::getStatus, ImportFieldConfigStatus.DELETED);
        if (excludeId != null) {
            wrapper.ne(ImportFieldConfigEntity::getId, excludeId);
        }
        Long count = selectCount(wrapper);
        return count != null && count > 0;
    }
}
