package cn.nihility.rbac.role.mapstruct;

import cn.nihility.rbac.role.dto.RoleCreateRequest;
import cn.nihility.rbac.role.dto.RoleOptionVO;
import cn.nihility.rbac.role.dto.RoleUpdateRequest;
import cn.nihility.rbac.role.dto.RoleVO;
import cn.nihility.rbac.role.entity.RoleEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;

/**
 * 角色实体与各类 DTO/VO 之间的 MapStruct 转换器，不接入 Spring 容器，通过
 * {@link #INSTANCE} 静态创建单例调用。
 */
@Mapper
public interface RoleConvert {

    /** 静态单例，避免注册为 Spring bean。 */
    RoleConvert INSTANCE = Mappers.getMapper(RoleConvert.class);

    /**
     * 实体转详情视图对象。
     *
     * @param entity 角色实体
     * @return 详情视图对象
     */
    RoleVO toVO(RoleEntity entity);

    /**
     * 实体列表批量转详情视图对象列表。
     *
     * @param entities 角色实体列表
     * @return 详情视图对象列表
     */
    List<RoleVO> toVOList(List<RoleEntity> entities);

    /**
     * 创建请求转实体，id/状态/审计字段由服务层另行赋值。
     *
     * @param request 创建请求
     * @return 角色实体
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createBy", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateBy", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    RoleEntity toEntity(RoleCreateRequest request);

    /**
     * 把更新请求的字段合并到已有实体上，id/状态/审计字段不受影响。
     *
     * @param request 更新请求
     * @param entity  待更新的角色实体
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createBy", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateBy", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    void updateEntity(RoleUpdateRequest request, @MappingTarget RoleEntity entity);

    /**
     * 实体转精简选项视图对象。
     *
     * @param entity 角色实体
     * @return 精简选项视图对象
     */
    RoleOptionVO toOptionVO(RoleEntity entity);

    /**
     * 实体列表批量转精简选项视图对象列表。
     *
     * @param entities 角色实体列表
     * @return 精简选项视图对象列表
     */
    List<RoleOptionVO> toOptionVOList(List<RoleEntity> entities);
}
