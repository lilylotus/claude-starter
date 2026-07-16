package cn.nihility.rbac.app.mapstruct;

import cn.nihility.rbac.app.dto.AppCreateRequest;
import cn.nihility.rbac.app.dto.AppUpdateRequest;
import cn.nihility.rbac.app.dto.AppVO;
import cn.nihility.rbac.app.entity.AppEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;

/**
 * 应用实体与各类 DTO/VO 之间的 MapStruct 转换器，不接入 Spring 容器，通过
 * {@link #INSTANCE} 静态创建单例调用。
 */
@Mapper
public interface AppConvert {

    /** 静态单例，避免注册为 Spring bean。 */
    AppConvert INSTANCE = Mappers.getMapper(AppConvert.class);

    /**
     * 实体转详情视图对象，{@code ownerName}/{@code orgName} 需要由调用方另行解析并回填。
     *
     * @param entity 应用实体
     * @return 详情视图对象
     */
    @Mapping(target = "ownerName", ignore = true)
    @Mapping(target = "orgName", ignore = true)
    AppVO toVO(AppEntity entity);

    /**
     * 实体列表批量转详情视图对象列表，{@code ownerName}/{@code orgName} 需要由调用方
     * 另行解析并回填。
     *
     * @param entities 应用实体列表
     * @return 详情视图对象列表
     */
    List<AppVO> toVOList(List<AppEntity> entities);

    /**
     * 创建请求转实体，id/状态/审计字段由服务层另行赋值。
     *
     * @param request 创建请求
     * @return 应用实体
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createBy", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateBy", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    AppEntity toEntity(AppCreateRequest request);

    /**
     * 把更新请求的字段合并到已有实体上，id/状态/审计字段不受影响。
     *
     * @param request 更新请求
     * @param entity  待更新的应用实体
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createBy", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateBy", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    void updateEntity(AppUpdateRequest request, @MappingTarget AppEntity entity);
}
