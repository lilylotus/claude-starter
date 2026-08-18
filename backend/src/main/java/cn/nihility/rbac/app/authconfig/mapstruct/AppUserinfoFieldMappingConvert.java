package cn.nihility.rbac.app.authconfig.mapstruct;

import cn.nihility.rbac.app.authconfig.dto.AppUserinfoFieldMappingRow;
import cn.nihility.rbac.app.authconfig.dto.AppUserinfoFieldMappingSaveRequest;
import cn.nihility.rbac.app.authconfig.dto.AppUserinfoFieldMappingVO;
import cn.nihility.rbac.app.authconfig.entity.AppUserinfoFieldMappingEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

/**
 * 应用用户信息响应字段映射相关实体/DTO/VO 之间的 MapStruct 转换器，不接入 Spring 容器，
 * 通过 {@link #INSTANCE} 静态创建单例调用。
 */
@Mapper
public interface AppUserinfoFieldMappingConvert {

    /** 静态单例，避免注册为 Spring bean。 */
    AppUserinfoFieldMappingConvert INSTANCE = Mappers.getMapper(AppUserinfoFieldMappingConvert.class);

    /**
     * 联表查询结果行（或现算默认行）转视图对象，字段名完全一致，直接同名复制。
     *
     * @param row 联表查询结果行
     * @return 应用用户信息响应字段映射视图对象
     */
    AppUserinfoFieldMappingVO toVO(AppUserinfoFieldMappingRow row);

    /**
     * 联表查询结果行列表批量转视图对象列表。
     *
     * @param rows 联表查询结果行列表
     * @return 应用用户信息响应字段映射视图对象列表
     */
    List<AppUserinfoFieldMappingVO> toVOList(List<AppUserinfoFieldMappingRow> rows);

    /**
     * 保存请求转持久化实体，{@code id}/{@code appRefId}/审计字段均无对应来源，由调用方
     * （{@code AppAuthConfigServiceImpl}）在转换后自行填充。
     *
     * @param request 单行保存请求
     * @return 应用用户信息响应字段映射实体
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "appRefId", ignore = true)
    @Mapping(target = "createBy", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateBy", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    AppUserinfoFieldMappingEntity toEntity(AppUserinfoFieldMappingSaveRequest request);
}
