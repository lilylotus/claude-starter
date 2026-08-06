package cn.nihility.rbac.formfield.mapstruct;

import cn.nihility.rbac.formfield.dto.FormFieldDefinitionCreateRequest;
import cn.nihility.rbac.formfield.dto.FormFieldDefinitionUpdateRequest;
import cn.nihility.rbac.formfield.dto.FormFieldDefinitionVO;
import cn.nihility.rbac.formfield.entity.FormFieldDefinitionEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;

/**
 * 表单字段定义实体与各类 DTO/VO 之间的 MapStruct 转换器，不接入 Spring 容器，
 * 通过 {@link #INSTANCE} 静态创建单例调用。
 */
@Mapper
public interface FormFieldDefinitionConvert {

    /** 静态单例，避免注册为 Spring bean。 */
    FormFieldDefinitionConvert INSTANCE = Mappers.getMapper(FormFieldDefinitionConvert.class);

    /**
     * 实体转详情视图对象，{@code columnName}/{@code dictTypeName}/{@code locked}
     * 需要由调用方另行解析/计算并回填；{@code createBy}/{@code updateBy} entity 侧
     * 落库为用户 id 文本，VO 侧需要展示为人可读展示名，两者语义不同，禁止 MapStruct
     * 按同名字段直接复制，由调用方另行回填。
     *
     * @param entity 表单字段定义实体
     * @return 详情视图对象
     */
    @Mapping(target = "columnName", ignore = true)
    @Mapping(target = "dictTypeName", ignore = true)
    @Mapping(target = "locked", ignore = true)
    @Mapping(target = "createBy", ignore = true)
    @Mapping(target = "updateBy", ignore = true)
    FormFieldDefinitionVO toVO(FormFieldDefinitionEntity entity);

    /**
     * 实体列表批量转详情视图对象列表，{@code columnName}/{@code dictTypeName}/
     * {@code locked} 需要由调用方另行解析/计算并回填。
     *
     * @param entities 表单字段定义实体列表
     * @return 详情视图对象列表
     */
    List<FormFieldDefinitionVO> toVOList(List<FormFieldDefinitionEntity> entities);

    /**
     * 创建请求转实体，{@code bizType}/{@code fieldCode} 均取自所绑定元数据字段，
     * 由服务层在映射之后另行赋值；id/状态/审计字段同样由服务层赋值。
     *
     * @param request 创建请求
     * @return 表单字段定义实体
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "bizType", ignore = true)
    @Mapping(target = "fieldCode", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createBy", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateBy", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    FormFieldDefinitionEntity toEntity(FormFieldDefinitionCreateRequest request);

    /**
     * 把更新请求的字段合并到已有实体上，id/业务对象类型/绑定的元数据字段/字段标识/
     * 状态/审计字段不受影响；{@code fieldCode} 完全派生自绑定的元数据字段，改绑时
     * 由服务层另行同步刷新。
     *
     * @param request 更新请求
     * @param entity  待更新的表单字段定义实体
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "bizType", ignore = true)
    @Mapping(target = "metadataFieldId", ignore = true)
    @Mapping(target = "fieldCode", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createBy", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateBy", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    void updateEntity(FormFieldDefinitionUpdateRequest request, @MappingTarget FormFieldDefinitionEntity entity);
}
