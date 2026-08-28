package cn.nihility.rbac.user.service;

import cn.nihility.rbac.common.result.PageResult;
import cn.nihility.rbac.user.dto.UserCreateRequest;
import cn.nihility.rbac.user.dto.UserUpdateRequest;
import cn.nihility.rbac.user.dto.UserVO;
import java.util.List;

/**
 * 用户业务逻辑接口。
 */
public interface UserService {

    /**
     * 分页查询用户，支持按姓名/手机号/身份证号模糊搜索（均可选，组合时为"与"关系），
     * 排除已逻辑删除的用户。
     *
     * @param name     姓名关键字，可为空
     * @param mobile   手机号关键字，可为空
     * @param idCard   身份证号关键字，可为空
     * @param page     页码，从 1 开始
     * @param pageSize 每页条数
     * @return 用户的分页结果（不含任职记录）
     */
    PageResult<UserVO> getPage(String name, String mobile, String idCard, Integer page, Integer pageSize);

    /**
     * 查询用户详情，含其名下全部任职记录（各自回填组织名称）。
     *
     * @param id 用户 id
     * @return 用户详情
     */
    UserVO getById(Long id);

    /**
     * 创建用户，可同时提交 0 到多条任职记录一并创建。
     *
     * @param request 创建请求
     * @return 创建后的用户详情
     */
    UserVO create(UserCreateRequest request);

    /**
     * 更新用户基础信息，并按请求中的 {@code positions} 列表对任职记录做增量 diff
     * （更新既有、新增缺失、删除多余；请求携带不属于当前用户的任职记录 id 时拒绝整个更新）。
     *
     * @param id      用户 id
     * @param request 更新请求
     * @return 更新后的用户详情
     */
    UserVO update(Long id, UserUpdateRequest request);

    /**
     * 启用用户。
     *
     * @param id 用户 id
     * @return 更新后的用户详情
     */
    UserVO enable(Long id);

    /**
     * 停用用户。
     *
     * @param id 用户 id
     * @return 更新后的用户详情
     */
    UserVO disable(Long id);

    /**
     * 逻辑删除用户，不级联处理其名下的任职记录。
     *
     * @param id 用户 id
     */
    void delete(Long id);

    /**
     * 重置指定用户的密码为默认密码 {@code Default#123456}，并将其标记为待首次登录强制改密状态；
     * 仅重置密码，不修改用户的 {@code status}。
     *
     * @param id 用户 id
     */
    void resetPassword(Long id);

    /**
     * 查询全部未逻辑删除的用户，不做组织范围收紧、不分页，供
     * {@code master-data-excel-export} 能力的用户导出使用；与 {@link #getPage} 现状
     * 保持一致（design.md Decision 2：用户导出维持现状，不按管辖组织范围收窄）。
     *
     * @return 全部未删除用户的详情列表
     */
    List<UserVO> listAllForExport();
}
