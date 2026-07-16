package cn.nihility.rbac.app.service;

import cn.nihility.rbac.app.dto.AppCreateRequest;
import cn.nihility.rbac.app.dto.AppUpdateRequest;
import cn.nihility.rbac.app.dto.AppVO;
import cn.nihility.rbac.common.PageResult;

/**
 * 应用管理业务逻辑接口，提供应用主数据的分页查询、维护、启停用、逻辑删除能力。
 */
public interface AppService {

    /**
     * 分页查询应用（排除已逻辑删除的记录），按显示序号降序排列。
     *
     * @param page     页码，从 1 开始
     * @param pageSize 每页条数
     * @return 应用的分页结果
     */
    PageResult<AppVO> getPage(Integer page, Integer pageSize);

    /**
     * 查询应用详情。
     *
     * @param id 应用 id
     * @return 应用详情
     */
    AppVO getById(Long id);

    /**
     * 创建应用。
     *
     * @param request 创建请求
     * @return 创建后的应用详情
     */
    AppVO create(AppCreateRequest request);

    /**
     * 更新应用，状态不通过本接口修改。
     *
     * @param id      应用 id
     * @param request 更新请求
     * @return 更新后的应用详情
     */
    AppVO update(Long id, AppUpdateRequest request);

    /**
     * 启用应用。
     *
     * @param id 应用 id
     * @return 更新后的应用详情
     */
    AppVO enable(Long id);

    /**
     * 停用应用。
     *
     * @param id 应用 id
     * @return 更新后的应用详情
     */
    AppVO disable(Long id);

    /**
     * 逻辑删除应用。
     *
     * @param id 应用 id
     */
    void delete(Long id);
}
