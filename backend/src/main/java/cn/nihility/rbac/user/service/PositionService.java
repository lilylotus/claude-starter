package cn.nihility.rbac.user.service;

import cn.nihility.rbac.common.result.PageResult;
import cn.nihility.rbac.user.dto.PositionCreateRequest;
import cn.nihility.rbac.user.dto.PositionUpdateRequest;
import cn.nihility.rbac.user.dto.PositionVO;
import java.util.List;
import java.util.Set;

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

    /**
     * 按当前登录用户的管辖组织范围查询全部未删除任职记录，不分页，供
     * {@code master-data-excel-export} 能力的任职导出使用（design.md Decision 1）。
     *
     * @return 管辖范围内的任职记录详情列表
     */
    List<PositionVO> listAllForExport();

    /**
     * 按岗位类型编码查询当前状态启用的任职用户 id 集合，供审批引擎
     * {@code PositionAssigneeResolver} 使用。本项目当前 schema 未落地独立的"岗位"主数据
     * 表，"岗位编码"实际对应任职记录的任职类型编码（{@code positionType}，取自字典类型
     * {@code position_type}，如 primary/part_time/temporary），同一岗位类型下可有多个用户
     * 同时任职（production-approval-lifecycle change tasks.md 5.3）。
     *
     * @param positionType 岗位类型编码
     * @return 状态启用的任职用户 id 集合，无匹配任职时返回空集合
     */
    Set<Long> findActiveUserIdsByPositionType(String positionType);
}
