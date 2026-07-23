package cn.nihility.rbac.admin.service;

import cn.nihility.rbac.admin.dto.AdminCreateRequest;
import cn.nihility.rbac.admin.dto.AdminUpdateRequest;
import cn.nihility.rbac.admin.dto.AdminVO;
import cn.nihility.rbac.common.result.PageResult;

/**
 * 管理员管理业务逻辑接口，提供管理员主数据的分页查询、维护、启停用、逻辑删除能力，
 * 以及随主数据一并整体同步的角色关联、组织管辖范围维护能力。
 */
public interface AdminService {

    /**
     * 分页查询管理员（排除已逻辑删除的记录），不支持筛选，按显示序号降序、id 升序排列，
     * 每条记录含关联用户姓名，不含角色列表与组织管辖范围列表。
     *
     * @param page     页码，从 1 开始
     * @param pageSize 每页条数
     * @return 管理员的分页结果
     */
    PageResult<AdminVO> getPage(Integer page, Integer pageSize);

    /**
     * 查询管理员详情，含其全部关联角色与全部管辖组织范围。
     *
     * @param id 管理员 id
     * @return 管理员详情
     */
    AdminVO getById(Long id);

    /**
     * 创建管理员，新建默认状态为启用，可同时提交角色 id 列表与管辖组织范围列表一并创建。
     *
     * @param request 创建请求
     * @return 创建后的管理员详情
     */
    AdminVO create(AdminCreateRequest request);

    /**
     * 更新管理员，状态不通过本接口修改；角色列表与管辖组织范围按整体替换语义处理
     * （更新后的关联集合与本次请求携带的集合完全一致）。
     *
     * @param id      管理员 id
     * @param request 更新请求
     * @return 更新后的管理员详情
     */
    AdminVO update(Long id, AdminUpdateRequest request);

    /**
     * 启用管理员，仅修改状态字段，不影响角色关联与组织管辖范围。
     *
     * @param id 管理员 id
     * @return 更新后的管理员详情
     */
    AdminVO enable(Long id);

    /**
     * 停用管理员，仅修改状态字段，不影响角色关联与组织管辖范围。
     *
     * @param id 管理员 id
     * @return 更新后的管理员详情
     */
    AdminVO disable(Long id);

    /**
     * 逻辑删除管理员，不物理删除该管理员及其角色关联、组织管辖范围数据。
     *
     * @param id 管理员 id
     */
    void delete(Long id);
}
