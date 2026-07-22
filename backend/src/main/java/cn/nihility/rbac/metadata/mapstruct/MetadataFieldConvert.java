package cn.nihility.rbac.metadata.mapstruct;

import cn.nihility.rbac.metadata.dto.MetadataFieldUpdateRequest;
import cn.nihility.rbac.metadata.dto.MetadataFieldVO;
import cn.nihility.rbac.metadata.entity.MetadataFieldEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;

/**
 * 元数据字段实体与各类 DTO/VO 之间的 MapStruct 转换器，不接入 Spring 容器，
 * 通过 {@link #INSTANCE} 静态创建单例调用。
 */
@Mapper
public interface MetadataFieldConvert {

    /** 静态单例，避免注册为 Spring bean。 */
    MetadataFieldConvert INSTANCE = Mappers.getMapper(MetadataFieldConvert.class);

    /**
     * 实体转详情视图对象。
     *
     * @param entity 元数据字段实体
     * @return 详情视图对象
     */
    MetadataFieldVO toVO(MetadataFieldEntity entity);

    /**
     * 实体列表批量转详情视图对象列表。
     *
     * @param entities 元数据字段实体列表
     * @return 详情视图对象列表
     */
    List<MetadataFieldVO> toVOList(List<MetadataFieldEntity> entities);

    /**
     * 把更新请求的字段合并到已有实体上，仅 {@code fieldName} 会被更新；
     * id/业务对象类型/表名称/字段列名/字段类型/状态/审计字段均不受影响。
     *
     * @param request 更新请求
     * @param entity  待更新的元数据字段实体
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "bizType", ignore = true)
    @Mapping(target = "tableName", ignore = true)
    @Mapping(target = "columnName", ignore = true)
    @Mapping(target = "columnType", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createBy", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateBy", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    void updateEntity(MetadataFieldUpdateRequest request, @MappingTarget MetadataFieldEntity entity);
}
