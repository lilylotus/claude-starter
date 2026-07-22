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
     * 更新元数据字段，{@code fieldName}/{@code fieldCode} 均可被修改；{@code fieldCode}
     * 需在同一业务对象类型下保持唯一，重复时拒绝更新。
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
     * 供"表单管理"新增字段定义时选择。等价于 {@link #listAvailable(String, Long)}
     * 的 {@code excludeDefinitionId} 传 {@code null}。
     *
     * @param bizType 业务对象类型
     * @return 可用元数据字段列表
     */
    List<MetadataFieldVO> listAvailable(String bizType);

    /**
     * 按业务对象类型查询"可用"元数据字段，供"表单管理"新增/编辑字段定义时选择。
     * 基础过滤条件与 {@link #listAvailable(String)} 一致（状态为启用、且未被任何
     * 有效表单字段定义绑定）；若 {@code excludeDefinitionId} 对应一条存在且未被
     * 逻辑删除的表单字段定义，额外把该定义当前绑定的元数据字段（若状态为启用）
     * 一并纳入返回列表，供编辑弹窗展示"当前绑定 + 其余可选"。{@code excludeDefinitionId}
     * 为 {@code null}、不存在或对应定义已被逻辑删除时，行为与
     * {@link #listAvailable(String)} 完全一致。
     *
     * @param bizType            业务对象类型
     * @param excludeDefinitionId 编辑场景下的表单字段定义 id，可为 null
     * @return 可用元数据字段列表
     */
    List<MetadataFieldVO> listAvailable(String bizType, Long excludeDefinitionId);
}
