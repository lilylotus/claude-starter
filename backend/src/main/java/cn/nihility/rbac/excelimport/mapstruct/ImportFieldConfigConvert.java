package cn.nihility.rbac.excelimport.mapstruct;

import cn.nihility.rbac.excelimport.dto.ImportFieldConfigCreateRequest;
import cn.nihility.rbac.excelimport.dto.ImportFieldConfigUpdateRequest;
import cn.nihility.rbac.excelimport.dto.ImportFieldConfigVO;
import cn.nihility.rbac.excelimport.entity.ImportFieldConfigEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;

/**
 * Excel 导入字段配置实体与各类 DTO/VO 之间的 MapStruct 转换器，不接入 Spring 容器，
 * 通过 {@link #INSTANCE} 静态单例调用。
 */
@Mapper
public interface ImportFieldConfigConvert {

    /** 静态单例，避免注册为 Spring bean。 */
    ImportFieldConfigConvert INSTANCE = Mappers.getMapper(ImportFieldConfigConvert.class);

    /**
     * 实体转详情视图对象，{@code locked} 需要由调用方另行计算并回填；
     * {@code createBy}/{@code updateBy} entity 侧落库为用户 id 文本，VO 侧需要展示为
     * 人可读展示名，两者语义不同，禁止 MapStruct 按同名字段直接复制，由调用方另行回填。
     *
     * @param entity 导入字段配置实体
     * @return 详情视图对象
     */
    @Mapping(target = "locked", ignore = true)
    @Mapping(target = "createBy", ignore = true)
    @Mapping(target = "updateBy", ignore = true)
    ImportFieldConfigVO toVO(ImportFieldConfigEntity entity);

    /**
     * 实体列表批量转详情视图对象列表，{@code locked} 需要由调用方另行计算并回填。
     *
     * @param entities 导入字段配置实体列表
     * @return 详情视图对象列表
     */
    List<ImportFieldConfigVO> toVOList(List<ImportFieldConfigEntity> entities);

    /**
     * 创建请求转实体，{@code fieldCode} 最终取值由服务层按是否关联表单字段定义决定
     * （关联时取自定义的当前值，覆盖请求中提交的值）；id/状态/审计字段同样由服务层
     * 赋值。
     *
     * @param request 创建请求
     * @return 导入字段配置实体
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createBy", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateBy", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    ImportFieldConfigEntity toEntity(ImportFieldConfigCreateRequest request);

    /**
     * 把更新请求的字段合并到已有实体上，id/业务对象类型/绑定的表单字段定义/字段标识/
     * 状态/审计字段不受影响；改绑/字段标识刷新由服务层另行处理。
     *
     * @param request 更新请求
     * @param entity  待更新的导入字段配置实体
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "bizType", ignore = true)
    @Mapping(target = "formFieldDefinitionId", ignore = true)
    @Mapping(target = "fieldCode", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createBy", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateBy", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    void updateEntity(ImportFieldConfigUpdateRequest request, @MappingTarget ImportFieldConfigEntity entity);
}
