package cn.nihility.rbac.role.service;

import cn.nihility.rbac.common.result.PageResult;
import cn.nihility.rbac.role.dto.RoleCreateRequest;
import cn.nihility.rbac.role.dto.RoleOptionVO;
import cn.nihility.rbac.role.dto.RoleUpdateRequest;
import cn.nihility.rbac.role.dto.RoleVO;
import java.util.List;

/**
 * 角色管理业务逻辑接口，提供角色主数据的分页查询、维护、启停用、逻辑删除能力。
 */
public interface RoleService {

    /**
     * 分页查询角色（排除已逻辑删除的记录），按显示序号降序排列。
     *
     * @param page     页码，从 1 开始
     * @param pageSize 每页条数
     * @return 角色的分页结果
     */
    PageResult<RoleVO> getPage(Integer page, Integer pageSize);

    /**
     * 查询角色详情。
     *
     * @param id 角色 id
     * @return 角色详情
     */
    RoleVO getById(Long id);

    /**
     * 创建角色。
     *
     * @param request 创建请求
     * @return 创建后的角色详情
     */
    RoleVO create(RoleCreateRequest request);

    /**
     * 更新角色，状态不通过本接口修改。
     *
     * @param id      角色 id
     * @param request 更新请求
     * @return 更新后的角色详情
     */
    RoleVO update(Long id, RoleUpdateRequest request);

    /**
     * 启用角色。
     *
     * @param id 角色 id
     * @return 更新后的角色详情
     */
    RoleVO enable(Long id);

    /**
     * 停用角色。
     *
     * @param id 角色 id
     * @return 更新后的角色详情
     */
    RoleVO disable(Long id);

    /**
     * 逻辑删除角色。
     *
     * @param id 角色 id
     */
    void delete(Long id);

    /**
     * 查询全部未被逻辑删除且状态为启用的角色精简选项列表，供其他模块的角色多选/单选
     * 选择器一次性加载全量选项使用，不分页。
     *
     * @return 启用状态的角色精简选项列表，按显示序号降序、id 升序排列
     */
    List<RoleOptionVO> getEnabledOptions();
}
