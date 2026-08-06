package cn.nihility.rbac.dict.mapstruct;

import cn.nihility.rbac.dict.dto.DictItemCreateRequest;
import cn.nihility.rbac.dict.dto.DictItemOptionVO;
import cn.nihility.rbac.dict.dto.DictItemUpdateRequest;
import cn.nihility.rbac.dict.dto.DictItemVO;
import cn.nihility.rbac.dict.dto.DictTypeCreateRequest;
import cn.nihility.rbac.dict.dto.DictTypeUpdateRequest;
import cn.nihility.rbac.dict.dto.DictTypeVO;
import cn.nihility.rbac.dict.entity.DictItemEntity;
import cn.nihility.rbac.dict.entity.DictTypeEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;

/**
 * 字典类型/字典项实体与各类 DTO/VO 之间的 MapStruct 转换器，不接入 Spring 容器，
 * 通过 {@link #INSTANCE} 静态创建单例调用。
 */
@Mapper
public interface DictConvert {

    /** 静态单例，避免注册为 Spring bean。 */
    DictConvert INSTANCE = Mappers.getMapper(DictConvert.class);

    /**
     * 字典类型实体转详情视图对象；{@code createBy}/{@code updateBy} entity 侧落库为用户 id
     * 文本，VO 侧需要展示为人可读展示名，两者语义不同，禁止 MapStruct 按同名字段直接复制，
     * 由调用方另行回填。
     *
     * @param entity 字典类型实体
     * @return 详情视图对象
     */
    @Mapping(target = "createBy", ignore = true)
    @Mapping(target = "updateBy", ignore = true)
    DictTypeVO toTypeVO(DictTypeEntity entity);

    /**
     * 字典类型实体列表批量转详情视图对象列表。
     *
     * @param entities 字典类型实体列表
     * @return 详情视图对象列表
     */
    List<DictTypeVO> toTypeVOList(List<DictTypeEntity> entities);

    /**
     * 创建请求转字典类型实体，id/状态/审计字段由服务层另行赋值。
     *
     * @param request 创建请求
     * @return 字典类型实体
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createBy", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateBy", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    DictTypeEntity toTypeEntity(DictTypeCreateRequest request);

    /**
     * 把更新请求的字段合并到已有字典类型实体上，id/状态/审计字段不受影响。
     *
     * @param request 更新请求
     * @param entity  待更新的字典类型实体
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createBy", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateBy", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    void updateTypeEntity(DictTypeUpdateRequest request, @MappingTarget DictTypeEntity entity);

    /**
     * 字典项实体转详情视图对象，{@code dictTypeName} 需要由调用方另行解析并回填；
     * {@code createBy}/{@code updateBy} entity 侧落库为用户 id 文本，VO 侧需要展示为
     * 人可读展示名，两者语义不同，禁止 MapStruct 按同名字段直接复制，由调用方另行回填。
     *
     * @param entity 字典项实体
     * @return 详情视图对象
     */
    @Mapping(target = "dictTypeName", ignore = true)
    @Mapping(target = "createBy", ignore = true)
    @Mapping(target = "updateBy", ignore = true)
    DictItemVO toItemVO(DictItemEntity entity);

    /**
     * 字典项实体列表批量转详情视图对象列表，{@code dictTypeName} 需要由调用方另行解析并回填。
     *
     * @param entities 字典项实体列表
     * @return 详情视图对象列表
     */
    List<DictItemVO> toItemVOList(List<DictItemEntity> entities);

    /**
     * 创建请求转字典项实体，id/状态/审计字段由服务层另行赋值。
     *
     * @param request 创建请求
     * @return 字典项实体
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createBy", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateBy", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    DictItemEntity toItemEntity(DictItemCreateRequest request);

    /**
     * 把更新请求的字段合并到已有字典项实体上，id/所属字典类型/状态/审计字段不受影响。
     *
     * @param request 更新请求
     * @param entity  待更新的字典项实体
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "dictTypeId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createBy", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateBy", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    void updateItemEntity(DictItemUpdateRequest request, @MappingTarget DictItemEntity entity);

    /**
     * 字典项实体转只读查询用的精简选项视图对象。
     *
     * @param entity 字典项实体
     * @return 精简选项视图对象
     */
    DictItemOptionVO toOptionVO(DictItemEntity entity);

    /**
     * 字典项实体列表批量转精简选项视图对象列表。
     *
     * @param entities 字典项实体列表
     * @return 精简选项视图对象列表
     */
    List<DictItemOptionVO> toOptionVOList(List<DictItemEntity> entities);
}
