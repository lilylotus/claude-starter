package cn.nihility.rbac.identity.upstream.mapstruct;

import cn.nihility.rbac.identity.upstream.dto.UpstreamFieldMappingRow;
import cn.nihility.rbac.identity.upstream.dto.UpstreamFieldMappingSaveRequest;
import cn.nihility.rbac.identity.upstream.dto.UpstreamFieldMappingVO;
import cn.nihility.rbac.identity.upstream.entity.UpstreamFieldMappingEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

/**
 * 上游字段映射相关实体/DTO/VO 之间的 MapStruct 转换器，不接入 Spring 容器，通过
 * {@link #INSTANCE} 静态创建单例调用。
 */
@Mapper
public interface UpstreamFieldMappingConvert {

    /** 静态单例，避免注册为 Spring bean。 */
    UpstreamFieldMappingConvert INSTANCE = Mappers.getMapper(UpstreamFieldMappingConvert.class);

    /**
     * 联表查询结果行转视图对象，字段名完全一致，直接同名复制。
     *
     * @param row 联表查询结果行
     * @return 上游字段映射视图对象
     */
    UpstreamFieldMappingVO toVO(UpstreamFieldMappingRow row);

    /**
     * 联表查询结果行列表批量转视图对象列表。
     *
     * @param rows 联表查询结果行列表
     * @return 上游字段映射视图对象列表
     */
    List<UpstreamFieldMappingVO> toVOList(List<UpstreamFieldMappingRow> rows);

    /**
     * 保存请求转持久化实体，{@code id}/{@code sourceId}/{@code dataType}/审计字段均无
     * 对应来源，由调用方（{@code UpstreamFieldMappingServiceImpl}）在转换后自行填充。
     *
     * @param request 单行保存请求
     * @return 上游字段映射实体
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "sourceId", ignore = true)
    @Mapping(target = "dataType", ignore = true)
    @Mapping(target = "createBy", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateBy", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    UpstreamFieldMappingEntity toEntity(UpstreamFieldMappingSaveRequest request);
}
