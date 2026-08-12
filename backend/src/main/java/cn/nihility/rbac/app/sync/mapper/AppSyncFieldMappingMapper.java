package cn.nihility.rbac.app.sync.mapper;

import cn.nihility.rbac.app.sync.dto.AppSyncFieldMappingRow;
import cn.nihility.rbac.app.sync.entity.AppSyncFieldMappingEntity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 应用同步字段映射 MyBatis-Plus 数据访问接口。单表 CRUD 直接复用 {@link BaseMapper}；
 * 联表查询（携带源字段名称/编码）走自定义方法 + {@code AppSyncFieldMappingMapper.xml}
 * （仓库既有约定：多表 JOIN 查询写在 MyBatis XML，不在 Java 侧批量查询后手工合并）。
 * 也供 {@code cn.nihility.rbac.metadata} 模块直接注入，用于判断某个元数据字段当前是否
 * 被至少一条应用同步字段映射引用（沿用本项目"跨模块只读查询直接复用对方 Mapper"的既有
 * 约定，参照 {@code FormFieldDefinitionMapper#existsActiveByMetadataFieldId}）。
 */
@Mapper
public interface AppSyncFieldMappingMapper extends BaseMapper<AppSyncFieldMappingEntity> {

    /**
     * 判断给定的元数据字段当前是否被至少一条应用同步字段映射引用。
     *
     * @param metadataFieldId 元数据字段 id
     * @return 是否存在引用
     */
    default boolean existsByMetadataFieldId(Long metadataFieldId) {
        Long count = selectCount(new LambdaQueryWrapper<AppSyncFieldMappingEntity>()
                .eq(AppSyncFieldMappingEntity::getMetadataFieldId, metadataFieldId));
        return count != null && count > 0;
    }

    /**
     * 按应用 id、数据域查询字段映射列表，携带 JOIN {@code tab_metadata_field} 得到的源字段
     * 名称/编码，按 {@code id} 升序排列（整体替换语义下 id 升序即保存时的提交顺序，见
     * design.md Decision 5）。
     *
     * @param appRefId   应用 id（{@code tab_app.id}）
     * @param syncDomain 数据域
     * @return 字段映射行列表
     */
    List<AppSyncFieldMappingRow> selectByAppRefIdAndDomain(@Param("appRefId") Long appRefId,
            @Param("syncDomain") String syncDomain);
}
