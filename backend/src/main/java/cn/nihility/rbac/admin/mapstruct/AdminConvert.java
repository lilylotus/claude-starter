package cn.nihility.rbac.admin.mapstruct;

import cn.nihility.rbac.admin.dto.AdminCreateRequest;
import cn.nihility.rbac.admin.dto.AdminUpdateRequest;
import cn.nihility.rbac.admin.dto.AdminVO;
import cn.nihility.rbac.admin.entity.AdminEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;

/**
 * 管理员实体与各类 DTO/VO 之间的 MapStruct 转换器，不接入 Spring 容器，通过
 * {@link #INSTANCE} 静态创建单例调用。角色关联、组织管辖范围不在本转换器内处理，
 * 由服务层另行整体同步与查询回填。
 */
@Mapper
public interface AdminConvert {

    /** 静态单例，避免注册为 Spring bean。 */
    AdminConvert INSTANCE = Mappers.getMapper(AdminConvert.class);

    /**
     * 实体转详情视图对象，{@code userName}/{@code roles}/{@code orgScopes} 需要由调用方
     * 另行解析并回填；{@code createBy}/{@code updateBy} 在实体上落库的是用户 id 文本，
     * 需要由服务层查询后回填成人可读的展示名，此处显式忽略，避免 MapStruct 把 id 文本
     * 原样复制到 VO 上冒充展示名。
     *
     * @param entity 管理员实体
     * @return 详情视图对象
     */
    @Mapping(target = "userName", ignore = true)
    @Mapping(target = "roles", ignore = true)
    @Mapping(target = "orgScopes", ignore = true)
    @Mapping(target = "createBy", ignore = true)
    @Mapping(target = "updateBy", ignore = true)
    AdminVO toVO(AdminEntity entity);

    /**
     * 实体列表批量转详情视图对象列表，{@code userName}/{@code roles}/{@code orgScopes}
     * 不会被填充。
     *
     * @param entities 管理员实体列表
     * @return 详情视图对象列表
     */
    List<AdminVO> toVOList(List<AdminEntity> entities);

    /**
     * 创建请求转实体，id/状态/审计字段由服务层另行赋值，{@code roleIds}/{@code orgScopes}
     * 由服务层另行处理（管理员实体本身没有这两个字段）。
     *
     * @param request 创建请求
     * @return 管理员实体
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createBy", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateBy", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    AdminEntity toEntity(AdminCreateRequest request);

    /**
     * 把更新请求的字段合并到已有管理员实体上，id/状态/审计字段不受影响。
     *
     * @param request 更新请求
     * @param entity  待更新的管理员实体
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createBy", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateBy", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    void updateEntity(AdminUpdateRequest request, @MappingTarget AdminEntity entity);
}
