package cn.nihility.rbac.org.mapstruct;

import cn.nihility.rbac.org.dto.OrgCreateRequest;
import cn.nihility.rbac.org.dto.OrgTreeNodeVO;
import cn.nihility.rbac.org.dto.OrgUpdateRequest;
import cn.nihility.rbac.org.dto.OrgVO;
import cn.nihility.rbac.org.entity.OrgEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;

/**
 * 组织实体与各类 DTO/VO 之间的 MapStruct 转换器，不接入 Spring 容器，
 * 通过 {@link #INSTANCE} 静态创建单例调用。
 */
@Mapper
public interface OrgConvert {

    /** 静态单例，避免注册为 Spring bean。 */
    OrgConvert INSTANCE = Mappers.getMapper(OrgConvert.class);

    /**
     * 实体转详情视图对象，{@code parentName} 需要由调用方另行解析并回填。
     *
     * @param entity 组织实体
     * @return 详情视图对象
     */
    @Mapping(target = "parentName", ignore = true)
    OrgVO toVO(OrgEntity entity);

    /**
     * 实体列表批量转详情视图对象列表。
     *
     * @param entities 组织实体列表
     * @return 详情视图对象列表
     */
    List<OrgVO> toVOList(List<OrgEntity> entities);

    /**
     * 实体转树节点视图对象，{@code children} 由调用方组装。
     *
     * @param entity 组织实体
     * @return 树节点视图对象
     */
    @Mapping(target = "children", ignore = true)
    OrgTreeNodeVO toTreeNode(OrgEntity entity);

    /**
     * 创建请求转实体，id/状态/扩展字段/审计字段由服务层另行赋值。
     *
     * @param request 创建请求
     * @return 组织实体
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "ext1", ignore = true)
    @Mapping(target = "ext2", ignore = true)
    @Mapping(target = "ext3", ignore = true)
    @Mapping(target = "ext4", ignore = true)
    @Mapping(target = "ext5", ignore = true)
    @Mapping(target = "ext6", ignore = true)
    @Mapping(target = "ext7", ignore = true)
    @Mapping(target = "ext8", ignore = true)
    @Mapping(target = "ext9", ignore = true)
    @Mapping(target = "ext10", ignore = true)
    @Mapping(target = "createBy", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateBy", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    OrgEntity toEntity(OrgCreateRequest request);

    /**
     * 把更新请求的字段合并到已有实体上，id/状态/扩展字段/审计字段不受影响。
     *
     * @param request 更新请求
     * @param entity  待更新的组织实体
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "ext1", ignore = true)
    @Mapping(target = "ext2", ignore = true)
    @Mapping(target = "ext3", ignore = true)
    @Mapping(target = "ext4", ignore = true)
    @Mapping(target = "ext5", ignore = true)
    @Mapping(target = "ext6", ignore = true)
    @Mapping(target = "ext7", ignore = true)
    @Mapping(target = "ext8", ignore = true)
    @Mapping(target = "ext9", ignore = true)
    @Mapping(target = "ext10", ignore = true)
    @Mapping(target = "createBy", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateBy", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    void updateEntity(OrgUpdateRequest request, @MappingTarget OrgEntity entity);
}
