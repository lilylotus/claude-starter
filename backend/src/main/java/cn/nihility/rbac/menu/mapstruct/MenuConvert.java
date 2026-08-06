package cn.nihility.rbac.menu.mapstruct;

import cn.nihility.rbac.menu.dto.MenuCreateRequest;
import cn.nihility.rbac.menu.dto.MenuTreeNodeVO;
import cn.nihility.rbac.menu.dto.MenuUpdateRequest;
import cn.nihility.rbac.menu.dto.MenuVO;
import cn.nihility.rbac.menu.entity.MenuEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;

/**
 * 资源实体与各类 DTO/VO 之间的 MapStruct 转换器，不接入 Spring 容器，
 * 通过 {@link #INSTANCE} 静态创建单例调用。
 */
@Mapper
public interface MenuConvert {

    /** 静态单例，避免注册为 Spring bean。 */
    MenuConvert INSTANCE = Mappers.getMapper(MenuConvert.class);

    /**
     * 实体转详情视图对象，{@code parentName} 需要由调用方另行解析并回填；
     * {@code createBy}/{@code updateBy} entity 侧落库为用户 id 文本，VO 侧需要展示为
     * 人可读展示名，两者语义不同，禁止 MapStruct 按同名字段直接复制，由调用方另行回填。
     *
     * @param entity 资源实体
     * @return 详情视图对象
     */
    @Mapping(target = "parentName", ignore = true)
    @Mapping(target = "createBy", ignore = true)
    @Mapping(target = "updateBy", ignore = true)
    MenuVO toVO(MenuEntity entity);

    /**
     * 实体列表批量转详情视图对象列表。
     *
     * @param entities 资源实体列表
     * @return 详情视图对象列表
     */
    List<MenuVO> toVOList(List<MenuEntity> entities);

    /**
     * 实体转树节点视图对象，{@code children} 由调用方组装。
     *
     * @param entity 资源实体
     * @return 树节点视图对象
     */
    @Mapping(target = "children", ignore = true)
    MenuTreeNodeVO toTreeNode(MenuEntity entity);

    /**
     * 创建请求转实体，id/状态/审计字段由服务层另行赋值。
     *
     * @param request 创建请求
     * @return 资源实体
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createBy", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateBy", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    MenuEntity toEntity(MenuCreateRequest request);

    /**
     * 把更新请求的字段合并到已有实体上，id/状态/审计字段不受影响。
     *
     * @param request 更新请求
     * @param entity  待更新的资源实体
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createBy", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateBy", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    void updateEntity(MenuUpdateRequest request, @MappingTarget MenuEntity entity);
}
