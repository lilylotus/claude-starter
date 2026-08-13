package cn.nihility.rbac.identity.upstream.mapper;

import cn.nihility.rbac.identity.upstream.dto.UpstreamFieldMappingRow;
import cn.nihility.rbac.identity.upstream.entity.UpstreamFieldMappingEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 上游字段映射 MyBatis-Plus 数据访问接口。单表 CRUD 直接复用 {@link BaseMapper}；
 * 联表查询（携带目标字段名称/编码）走自定义方法 + {@code UpstreamFieldMappingMapper.xml}
 * （仓库既有约定：多表 JOIN 查询写在 MyBatis XML，不在 Java 侧批量查询后手工合并）。
 */
@Mapper
public interface UpstreamFieldMappingMapper extends BaseMapper<UpstreamFieldMappingEntity> {

    /**
     * 按数据源 id、数据域查询字段映射列表，携带 JOIN {@code tab_metadata_field} 得到的
     * 目标字段名称/编码，按 {@code id} 升序排列。
     *
     * @param sourceId 上游数据源 id（{@code tab_upstream_source.id}）
     * @param dataType 数据域
     * @return 字段映射行列表
     */
    List<UpstreamFieldMappingRow> selectBySourceIdAndDataType(@Param("sourceId") Long sourceId,
            @Param("dataType") String dataType);
}
