package cn.nihility.rbac.metadata.service;

import cn.nihility.rbac.common.PageResult;
import cn.nihility.rbac.metadata.dto.MetadataFieldUpdateRequest;
import cn.nihility.rbac.metadata.dto.MetadataFieldVO;
import java.util.List;

/**
 * 元数据字段配置业务逻辑接口。目录只能通过数据库迁移预置，本接口不提供新增/删除能力。
 */
public interface MetadataFieldService {

    /**
     * 按业务对象类型分页查询元数据字段。
     *
     * @param bizType  业务对象类型，可为空（为空时不按类型过滤）
     * @param page     页码，从 1 开始
     * @param pageSize 每页条数
     * @return 元数据字段的分页结果
     */
    PageResult<MetadataFieldVO> getPage(String bizType, Integer page, Integer pageSize);

    /**
     * 查询元数据字段详情。
     *
     * @param id 元数据字段 id
     * @return 元数据字段详情
     */
    MetadataFieldVO getById(Long id);

    /**
     * 更新元数据字段，仅 {@code fieldName} 可被修改。
     *
     * @param id      元数据字段 id
     * @param request 更新请求
     * @return 更新后的元数据字段详情
     */
    MetadataFieldVO update(Long id, MetadataFieldUpdateRequest request);

    /**
     * 启用元数据字段。
     *
     * @param id 元数据字段 id
     * @return 更新后的元数据字段详情
     */
    MetadataFieldVO enable(Long id);

    /**
     * 停用元数据字段；若该字段当前被至少一条有效表单字段定义绑定，拒绝停用。
     *
     * @param id 元数据字段 id
     * @return 更新后的元数据字段详情
     */
    MetadataFieldVO disable(Long id);

    /**
     * 按业务对象类型查询"可用"元数据字段：状态为启用、且未被任何有效表单字段定义绑定，
     * 供"表单管理"新增字段定义时选择。
     *
     * @param bizType 业务对象类型
     * @return 可用元数据字段列表
     */
    List<MetadataFieldVO> listAvailable(String bizType);
}
