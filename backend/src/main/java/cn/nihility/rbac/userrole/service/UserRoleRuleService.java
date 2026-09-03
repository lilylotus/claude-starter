package cn.nihility.rbac.userrole.service;

import cn.nihility.rbac.common.result.PageResult;
import cn.nihility.rbac.userrole.dto.UserRoleMatchedUserVO;
import cn.nihility.rbac.userrole.dto.UserRoleRuleCreateRequest;
import cn.nihility.rbac.userrole.dto.UserRoleRulePreviewRequest;
import cn.nihility.rbac.userrole.dto.UserRoleRuleUpdateRequest;
import cn.nihility.rbac.userrole.dto.UserRoleRuleVO;
import java.util.List;

/**
 * 用户角色规则业务逻辑接口，提供规则的查询、预览（不落库）、新增/编辑（保存后同步执行一次）、
 * 删除（级联收回其产生的角色关联）能力（add-user-role-batch-assignment change design.md
 * Decision 3/3a/4，二次设计版本）。
 */
public interface UserRoleRuleService {

    /**
     * 按角色 id 查询其全部规则的摘要列表（id/名称/备注/最近执行时间/最近执行人/当前命中
     * 人数），不携带组织范围/用户属性条件明细，避免列表接口 N+1（风格对齐
     * {@code AdminService#getPage} 与 {@code AdminVO} 分页列表不填充关联子集合的既有约定）；
     * 需要条件明细时调用 {@link #getById(Long)}。
     *
     * @param roleId 目标角色 id
     * @return 规则摘要列表
     */
    List<UserRoleRuleVO> listByRoleId(Long roleId);

    /**
     * 查询单条规则详情，含其全部组织范围条件、用户属性条件明细与当前命中人数，供前端"编辑
     * 规则"表单回填使用。
     *
     * @param id 规则 id
     * @return 规则详情
     */
    UserRoleRuleVO getById(Long id);

    /**
     * 按给定条件预览命中用户，不依赖已保存的规则、不写库。
     *
     * @param request 预览请求参数
     * @return 命中用户的分页结果
     */
    PageResult<UserRoleMatchedUserVO> preview(UserRoleRulePreviewRequest request);

    /**
     * 新增规则：校验目标角色存在、两类条件至少一类非空，落库规则主表与条件子表，随后同步
     * 执行一次，返回规则详情（含本次命中人数）。
     *
     * @param request 新增请求参数
     * @return 新建后的规则详情
     */
    UserRoleRuleVO create(UserRoleRuleCreateRequest request);

    /**
     * 编辑规则：条件子表整体替换（先删后插），保存后同步按新条件重新执行一次，返回更新后的
     * 规则详情。
     *
     * @param id      规则 id
     * @param request 编辑请求参数
     * @return 更新后的规则详情
     */
    UserRoleRuleVO update(Long id, UserRoleRuleUpdateRequest request);

    /**
     * 删除规则：先按"命中集合为空"收回该规则已产生的全部角色关联（触发对应的联动停用检查），
     * 再物理删除规则本身及其组织范围/用户属性条件子表记录。
     *
     * @param id 规则 id
     */
    void delete(Long id);
}
