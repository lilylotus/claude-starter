package cn.nihility.rbac.user.mapstruct;

import cn.nihility.rbac.user.dto.UserCreateRequest;
import cn.nihility.rbac.user.dto.UserPositionRequest;
import cn.nihility.rbac.user.dto.UserPositionVO;
import cn.nihility.rbac.user.dto.UserUpdateRequest;
import cn.nihility.rbac.user.dto.UserVO;
import cn.nihility.rbac.user.entity.UserEntity;
import cn.nihility.rbac.user.entity.UserPositionEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;

/**
 * 用户/用户任职记录实体与各类 DTO/VO 之间的 MapStruct 转换器，不接入 Spring 容器，
 * 通过 {@link #INSTANCE} 静态创建单例调用。
 */
@Mapper
public interface UserConvert {

    /** 静态单例，避免注册为 Spring bean。 */
    UserConvert INSTANCE = Mappers.getMapper(UserConvert.class);

    /**
     * 用户实体转详情视图对象，{@code positions} 需要由调用方另行查询并回填；{@code createBy}/
     * {@code updateBy} 落库内容是登录用户 id 的字符串，需要由调用方查询后回填为
     * "姓名（账号编码）" 展示名，此处显式 ignore 避免 MapStruct 把 id 文本原样当展示名复制。
     *
     * @param entity 用户实体
     * @return 详情视图对象
     */
    @Mapping(target = "positions", ignore = true)
    @Mapping(target = "createBy", ignore = true)
    @Mapping(target = "updateBy", ignore = true)
    UserVO toVO(UserEntity entity);

    /**
     * 用户实体列表批量转详情视图对象列表，{@code positions} 不会被填充（列表查询不需要）；
     * {@code createBy}/{@code updateBy} 同 {@link #toVO} 需要由调用方另行回填展示名。
     *
     * @param entities 用户实体列表
     * @return 详情视图对象列表
     */
    List<UserVO> toVOList(List<UserEntity> entities);

    /**
     * 创建请求转用户实体，id/状态/审计字段由服务层另行赋值，{@code positions} 由服务层
     * 另行处理（用户实体本身没有该字段）。
     *
     * @param request 创建请求
     * @return 用户实体
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createBy", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateBy", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    UserEntity toEntity(UserCreateRequest request);

    /**
     * 把更新请求的字段合并到已有用户实体上，id/状态/审计字段不受影响。
     *
     * @param request 更新请求
     * @param entity  待更新的用户实体
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createBy", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateBy", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    void updateEntity(UserUpdateRequest request, @MappingTarget UserEntity entity);

    /**
     * 任职记录请求转实体，供新增场景使用；id/所属用户/状态/审计字段由服务层另行赋值
     * （新增记录的状态由服务层显式置为启用）。用户管理内嵌的任职子表单
     * （{@link UserPositionRequest}）与独立任职管理入口（{@code PositionCreateRequest}）
     * 一样支持 {@code ext1}..{@code ext10}，按同名字段自动映射。
     *
     * @param request 任职记录请求
     * @return 任职记录实体
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createBy", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateBy", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    UserPositionEntity toPositionEntity(UserPositionRequest request);

    /**
     * 把任职记录更新请求的字段合并到已有任职记录实体上；id/所属用户/状态/创建审计字段
     * 不受影响，更新审计字段由服务层另行刷新；{@code ext1}..{@code ext10} 同
     * {@link #toPositionEntity} 按同名字段自动映射覆盖原值。
     *
     * @param request 任职记录请求
     * @param entity  待更新的任职记录实体
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createBy", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateBy", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    void updatePositionEntity(UserPositionRequest request, @MappingTarget UserPositionEntity entity);

    /**
     * 任职记录实体转视图对象，{@code orgName} 需要由调用方另行解析并回填；{@code createBy}/
     * {@code updateBy} 落库内容是登录用户 id 的字符串，需要由调用方查询后回填为
     * "姓名（账号编码）" 展示名，此处显式 ignore 避免 MapStruct 把 id 文本原样当展示名复制。
     *
     * @param entity 任职记录实体
     * @return 任职记录视图对象
     */
    @Mapping(target = "orgName", ignore = true)
    @Mapping(target = "createBy", ignore = true)
    @Mapping(target = "updateBy", ignore = true)
    UserPositionVO toPositionVO(UserPositionEntity entity);

    /**
     * 任职记录实体列表批量转视图对象列表，{@code orgName} 需要由调用方另行解析并回填；
     * {@code createBy}/{@code updateBy} 同 {@link #toPositionVO} 需要由调用方另行回填展示名。
     *
     * @param entities 任职记录实体列表
     * @return 任职记录视图对象列表
     */
    List<UserPositionVO> toPositionVOList(List<UserPositionEntity> entities);
}
