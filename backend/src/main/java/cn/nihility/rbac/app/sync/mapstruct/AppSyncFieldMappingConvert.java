package cn.nihility.rbac.app.sync.mapstruct;

import cn.nihility.rbac.app.sync.dto.AppSyncFieldMappingRow;
import cn.nihility.rbac.app.sync.dto.AppSyncFieldMappingSaveRequest;
import cn.nihility.rbac.app.sync.dto.AppSyncFieldMappingVO;
import cn.nihility.rbac.app.sync.entity.AppSyncFieldMappingEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

/**
 * 应用同步字段映射相关实体/DTO/VO 之间的 MapStruct 转换器，不接入 Spring 容器，
 * 通过 {@link #INSTANCE} 静态创建单例调用。
 */
@Mapper
public interface AppSyncFieldMappingConvert {

    /** 静态单例，避免注册为 Spring bean。 */
    AppSyncFieldMappingConvert INSTANCE = Mappers.getMapper(AppSyncFieldMappingConvert.class);

    /**
     * 联表查询结果行转视图对象，字段名完全一致，直接同名复制。
     *
     * @param row 联表查询结果行
     * @return 应用同步字段映射视图对象
     */
    AppSyncFieldMappingVO toVO(AppSyncFieldMappingRow row);

    /**
     * 联表查询结果行列表批量转视图对象列表。
     *
     * @param rows 联表查询结果行列表
     * @return 应用同步字段映射视图对象列表
     */
    List<AppSyncFieldMappingVO> toVOList(List<AppSyncFieldMappingRow> rows);

    /**
     * 保存请求转持久化实体，{@code id}/{@code appRefId}/{@code syncDomain}/审计字段均无
     * 对应来源，由调用方（{@code AppSyncConfigServiceImpl}）在转换后自行填充。
     *
     * @param request 单行保存请求
     * @return 应用同步字段映射实体
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "appRefId", ignore = true)
    @Mapping(target = "syncDomain", ignore = true)
    @Mapping(target = "createBy", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateBy", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    AppSyncFieldMappingEntity toEntity(AppSyncFieldMappingSaveRequest request);
}
