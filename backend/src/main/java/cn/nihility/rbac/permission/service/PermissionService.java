package cn.nihility.rbac.permission.service;

import cn.nihility.rbac.common.PageResult;
import cn.nihility.rbac.permission.dto.PermissionCreateRequest;
import cn.nihility.rbac.permission.dto.PermissionUpdateRequest;
import cn.nihility.rbac.permission.dto.PermissionVO;

/**
 * 权限管理业务逻辑接口，提供权限点主数据的分页查询、维护、启停用、逻辑删除能力。
 */
public interface PermissionService {

    /**
     * 分页查询权限点（排除已逻辑删除的记录），按显示序号降序排列。
     *
     * @param page     页码，从 1 开始
     * @param pageSize 每页条数
     * @return 权限点的分页结果
     */
    PageResult<PermissionVO> getPage(Integer page, Integer pageSize);

    /**
     * 查询权限点详情。
     *
     * @param id 权限点 id
     * @return 权限点详情
     */
    PermissionVO getById(Long id);

    /**
     * 创建权限点。
     *
     * @param request 创建请求
     * @return 创建后的权限点详情
     */
    PermissionVO create(PermissionCreateRequest request);

    /**
     * 更新权限点，状态不通过本接口修改。
     *
     * @param id      权限点 id
     * @param request 更新请求
     * @return 更新后的权限点详情
     */
    PermissionVO update(Long id, PermissionUpdateRequest request);

    /**
     * 启用权限点。
     *
     * @param id 权限点 id
     * @return 更新后的权限点详情
     */
    PermissionVO enable(Long id);

    /**
     * 停用权限点。
     *
     * @param id 权限点 id
     * @return 更新后的权限点详情
     */
    PermissionVO disable(Long id);

    /**
     * 逻辑删除权限点。
     *
     * @param id 权限点 id
     */
    void delete(Long id);
}
