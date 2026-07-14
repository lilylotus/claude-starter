package cn.nihility.rbac.user.service;

import cn.nihility.rbac.common.PageResult;
import cn.nihility.rbac.user.dto.PositionCreateRequest;
import cn.nihility.rbac.user.dto.PositionUpdateRequest;
import cn.nihility.rbac.user.dto.PositionVO;

/**
 * 任职管理业务逻辑接口，以组织为导航维度对任职记录做独立查询与维护，
 * 复用用户管理模块既有的 {@code tab_user_position} 表/实体/Mapper。
 */
public interface PositionService {

    /**
     * 按所属组织 id 分页查询任职记录（排除已逻辑删除的记录）。
     *
     * @param orgId    所属组织 id，必填
     * @param page     页码，从 1 开始
     * @param pageSize 每页条数
     * @return 任职记录的分页结果
     */
    PageResult<PositionVO> getPage(Long orgId, Integer page, Integer pageSize);

    /**
     * 查询任职记录详情。
     *
     * @param id 任职记录 id
     * @return 任职记录详情
     */
    PositionVO getById(Long id);

    /**
     * 创建任职记录。
     *
     * @param request 创建请求
     * @return 创建后的任职记录详情
     */
    PositionVO create(PositionCreateRequest request);

    /**
     * 更新任职记录，所属用户与状态不通过本接口修改。
     *
     * @param id      任职记录 id
     * @param request 更新请求
     * @return 更新后的任职记录详情
     */
    PositionVO update(Long id, PositionUpdateRequest request);

    /**
     * 启用任职记录。
     *
     * @param id 任职记录 id
     * @return 更新后的任职记录详情
     */
    PositionVO enable(Long id);

    /**
     * 停用任职记录。
     *
     * @param id 任职记录 id
     * @return 更新后的任职记录详情
     */
    PositionVO disable(Long id);

    /**
     * 逻辑删除任职记录。
     *
     * @param id 任职记录 id
     */
    void delete(Long id);
}
