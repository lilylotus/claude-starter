package cn.nihility.rbac.user.mapstruct;

import cn.nihility.rbac.user.dto.PositionCreateRequest;
import cn.nihility.rbac.user.dto.PositionUpdateRequest;
import cn.nihility.rbac.user.dto.PositionVO;
import cn.nihility.rbac.user.entity.UserPositionEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;

/**
 * 任职管理入口下，任职记录实体与各类 DTO/VO 之间的 MapStruct 转换器，不接入 Spring 容器，
 * 通过 {@link #INSTANCE} 静态创建单例调用。
 */
@Mapper
public interface PositionConvert {

    /** 静态单例，避免注册为 Spring bean。 */
    PositionConvert INSTANCE = Mappers.getMapper(PositionConvert.class);

    /**
     * 实体转详情视图对象，{@code userName}/{@code orgName} 需要由调用方另行解析并回填；
     * {@code createBy}/{@code updateBy} 落库内容是登录用户 id 的字符串，需要由调用方查询后
     * 回填为 "姓名（账号编码）" 展示名，此处显式 ignore 避免 MapStruct 把 id 文本原样当
     * 展示名复制。
     *
     * @param entity 任职记录实体
     * @return 详情视图对象
     */
    @Mapping(target = "userName", ignore = true)
    @Mapping(target = "orgName", ignore = true)
    @Mapping(target = "createBy", ignore = true)
    @Mapping(target = "updateBy", ignore = true)
    PositionVO toVO(UserPositionEntity entity);

    /**
     * 实体列表批量转详情视图对象列表，{@code userName}/{@code orgName} 需要由调用方
     * 另行解析并回填；{@code createBy}/{@code updateBy} 同 {@link #toVO} 需要由调用方
     * 另行回填展示名。
     *
     * @param entities 任职记录实体列表
     * @return 详情视图对象列表
     */
    List<PositionVO> toVOList(List<UserPositionEntity> entities);

    /**
     * 创建请求转实体，id/状态/审计字段由服务层另行赋值。
     *
     * @param request 创建请求
     * @return 任职记录实体
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createBy", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateBy", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    UserPositionEntity toEntity(PositionCreateRequest request);

    /**
     * 把更新请求的字段合并到已有实体上，id/所属用户/状态/审计字段不受影响。
     *
     * @param request 更新请求
     * @param entity  待更新的任职记录实体
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createBy", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateBy", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    void updateEntity(PositionUpdateRequest request, @MappingTarget UserPositionEntity entity);
}
