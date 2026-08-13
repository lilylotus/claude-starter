package cn.nihility.rbac.identity.upstream.service;

import cn.nihility.rbac.identity.upstream.dto.UpstreamFieldMappingSaveRequest;
import cn.nihility.rbac.identity.upstream.dto.UpstreamFieldMappingVO;
import java.util.List;

/**
 * 上游字段映射业务逻辑接口：负责按数据源+数据域查询字段映射列表、整体替换保存
 * （spec.md Requirement：上游字段映射配置）。
 */
public interface UpstreamFieldMappingService {

    /**
     * 查询指定数据源某个数据域的字段映射列表。
     *
     * @param sourceId 上游数据源 id
     * @param dataType 数据域
     * @return 字段映射视图对象列表
     */
    List<UpstreamFieldMappingVO> list(Long sourceId, String dataType);

    /**
     * 整体替换指定数据源某个数据域的字段映射列表：先按 {@code (sourceId, dataType)}
     * 物理删除既有映射行，再按提交内容批量插入，整个操作在一个事务内完成，不做按行 diff。
     *
     * @param sourceId 上游数据源 id
     * @param dataType 数据域，必须是 {@code UpstreamDataType} 三个常量之一
     * @param requests 本次提交的完整字段映射行列表
     * @return 保存后的字段映射视图对象列表
     */
    List<UpstreamFieldMappingVO> replace(Long sourceId, String dataType,
            List<UpstreamFieldMappingSaveRequest> requests);
}
