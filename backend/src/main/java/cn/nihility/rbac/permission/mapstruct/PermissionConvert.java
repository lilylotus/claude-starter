package cn.nihility.rbac.permission.mapstruct;

import cn.nihility.rbac.permission.dto.PermissionCreateRequest;
import cn.nihility.rbac.permission.dto.PermissionOptionVO;
import cn.nihility.rbac.permission.dto.PermissionUpdateRequest;
import cn.nihility.rbac.permission.dto.PermissionVO;
import cn.nihility.rbac.permission.entity.PermissionEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;

/**
 * 权限点实体与各类 DTO/VO 之间的 MapStruct 转换器，不接入 Spring 容器，通过
 * {@link #INSTANCE} 静态创建单例调用。
 */
@Mapper
public interface PermissionConvert {

    /** 静态单例，避免注册为 Spring bean。 */
    PermissionConvert INSTANCE = Mappers.getMapper(PermissionConvert.class);

    /**
     * 实体转详情视图对象；{@code createBy}/{@code updateBy} 在实体上落库的是用户 id 文本，
     * 需要由服务层查询后回填成人可读的展示名，此处显式忽略，避免 MapStruct 把 id 文本
     * 原样复制到 VO 上冒充展示名。
     *
     * @param entity 权限点实体
     * @return 详情视图对象
     */
    @Mapping(target = "createBy", ignore = true)
    @Mapping(target = "updateBy", ignore = true)
    PermissionVO toVO(PermissionEntity entity);

    /**
     * 实体列表批量转详情视图对象列表。
     *
     * @param entities 权限点实体列表
     * @return 详情视图对象列表
     */
    List<PermissionVO> toVOList(List<PermissionEntity> entities);

    /**
     * 创建请求转实体，id/状态/审计字段由服务层另行赋值。
     *
     * @param request 创建请求
     * @return 权限点实体
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createBy", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateBy", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    PermissionEntity toEntity(PermissionCreateRequest request);

    /**
     * 把更新请求的字段合并到已有实体上，id/状态/审计字段不受影响。
     *
     * @param request 更新请求
     * @param entity  待更新的权限点实体
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createBy", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateBy", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    void updateEntity(PermissionUpdateRequest request, @MappingTarget PermissionEntity entity);

    /**
     * 实体转精简选项视图对象。
     *
     * @param entity 权限点实体
     * @return 精简选项视图对象
     */
    PermissionOptionVO toOptionVO(PermissionEntity entity);

    /**
     * 实体列表批量转精简选项视图对象列表。
     *
     * @param entities 权限点实体列表
     * @return 精简选项视图对象列表
     */
    List<PermissionOptionVO> toOptionVOList(List<PermissionEntity> entities);
}
