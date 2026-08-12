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
     * 实体转详情视图对象，{@code parentName} 需要由调用方另行解析并回填；
     * {@code createBy}/{@code updateBy} entity 侧落库为用户 id 文本，VO 侧需要展示为
     * 人可读展示名，两者语义不同，禁止 MapStruct 按同名字段直接复制，由调用方另行回填。
     *
     * @param entity 组织实体
     * @return 详情视图对象
     */
    @Mapping(target = "parentName", ignore = true)
    @Mapping(target = "createBy", ignore = true)
    @Mapping(target = "updateBy", ignore = true)
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
     * 创建请求转实体，id/状态/审计字段由服务层另行赋值；{@code ext1}..{@code ext10}
     * 随请求体按同名属性自动映射，无需手写转换代码。{@code parentCode} 完全由服务层
     * 根据 {@code parentId} 派生，请求体不携带该字段，显式忽略以消除 MapStruct
     * 未映射目标属性警告（org-add-parent-code change design.md Decision 4/5）。
     *
     * @param request 创建请求
     * @return 组织实体
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "parentCode", ignore = true)
    @Mapping(target = "createBy", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateBy", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    OrgEntity toEntity(OrgCreateRequest request);

    /**
     * 把更新请求的字段合并到已有实体上，id/状态/审计字段不受影响；
     * {@code ext1}..{@code ext10} 随请求体按同名属性自动映射。{@code parentCode}
     * 完全由服务层根据 {@code parentId} 是否变化按需重新派生，请求体不携带该字段，
     * 显式忽略以消除 MapStruct 未映射目标属性警告（org-add-parent-code change
     * design.md Decision 4/5）。
     *
     * @param request 更新请求
     * @param entity  待更新的组织实体
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "parentCode", ignore = true)
    @Mapping(target = "createBy", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateBy", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    void updateEntity(OrgUpdateRequest request, @MappingTarget OrgEntity entity);
}
