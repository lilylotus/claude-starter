package cn.nihility.rbac.permission.service;

import cn.nihility.rbac.common.result.PageResult;
import cn.nihility.rbac.permission.dto.PermissionCreateRequest;
import cn.nihility.rbac.permission.dto.PermissionOptionVO;
import cn.nihility.rbac.permission.dto.PermissionUpdateRequest;
import cn.nihility.rbac.permission.dto.PermissionVO;
import java.util.List;

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

    /**
     * 查询全部未被逻辑删除且状态为启用的权限点精简选项列表，供角色管理等其他模块的
     * 权限点勾选控件一次性加载全量选项使用，不分页。
     *
     * @return 启用状态的权限点精简选项列表，按显示序号降序、id 升序排列
     */
    List<PermissionOptionVO> getEnabledOptions();

    /**
     * 查询全部未被逻辑删除的权限点完整信息列表，不按状态筛选（启用、停用的权限点都会
     * 返回），不分页，专供权限管理前端的树形管理视图一次性加载全量数据使用，不供角色
     * 勾选等只读选项场景复用（后者请使用 {@link #getEnabledOptions()}）。
     *
     * <p><b>注意排序方向</b>：结果按显示序号（{@code showOrder}）<b>升序</b>、相同时按
     * id 升序排列——显示序号越小排在越前面。这与本模块其余接口（{@link #getPage}、
     * {@link #getEnabledOptions()}）"showOrder 降序、值越大越靠前"的约定刚好相反，
     * 是权限点管理树形展示特意选择的顺序（编号即顺位，序号小的排前面），
     * 只影响这一个接口的展示，请勿"顺手"把方向改得和其他接口一致。
     *
     * @return 权限点完整信息列表，按显示序号升序、id 升序排列
     */
    List<PermissionVO> getAllList();
}
