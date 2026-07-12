package cn.nihility.rbac.user.controller;

import cn.nihility.rbac.common.PageResult;
import cn.nihility.rbac.common.Result;
import cn.nihility.rbac.user.dto.UserCreateRequest;
import cn.nihility.rbac.user.dto.UserUpdateRequest;
import cn.nihility.rbac.user.dto.UserVO;
import cn.nihility.rbac.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户管理接口，提供用户主数据的分页查询、维护、启停用、逻辑删除能力，
 * 以及内嵌其中的任职记录整体维护能力。
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "用户管理", description = "用户主数据及任职记录维护接口")
public class UserController {

    /** 用户业务逻辑接口。 */
    private final UserService userService;

    /**
     * 分页查询用户，支持按姓名/手机号/身份证号模糊搜索，组合时为"与"关系。
     *
     * @param name     姓名关键字，可选
     * @param mobile   手机号关键字，可选
     * @param idCard   身份证号关键字，可选
     * @param page     页码，默认第 1 页
     * @param pageSize 每页条数，默认 10 条
     * @return 用户的分页结果
     */
    @Operation(summary = "分页查询用户", description = "支持按姓名/手机号/身份证号模糊搜索，均可选，组合时为“与”关系，排除已逻辑删除的用户")
    @GetMapping("/api/users")
    public PageResult<UserVO> page(
            @Parameter(description = "姓名关键字")
            @RequestParam(required = false) String name,
            @Parameter(description = "手机号关键字")
            @RequestParam(required = false) String mobile,
            @Parameter(description = "身份证号关键字")
            @RequestParam(required = false) String idCard,
            @Parameter(description = "页码，默认第 1 页")
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @Parameter(description = "每页条数，默认 10 条")
            @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        return userService.getPage(name, mobile, idCard, page, pageSize);
    }

    /**
     * 查询用户详情，含其名下全部任职记录。
     *
     * @param id 用户 id
     * @return 用户详情
     */
    @Operation(summary = "查询用户详情", description = "返回用户完整信息及其名下全部任职记录（含各自回填的组织名称）")
    @GetMapping("/api/users/{id}")
    public UserVO detail(@PathVariable Long id) {
        return userService.getById(id);
    }

    /**
     * 创建用户，可同时提交 0 到多条任职记录一并创建。
     *
     * @param request 创建请求
     * @return 创建后的用户详情
     */
    @Operation(summary = "创建用户", description = "编号/身份证号在未删除用户范围内需唯一，可同时提交任职记录一并创建")
    @PostMapping("/api/users")
    public UserVO create(@Valid @RequestBody UserCreateRequest request) {
        return userService.create(request);
    }

    /**
     * 更新用户基础信息及任职记录，状态字段不通过本接口修改。
     *
     * @param id      用户 id
     * @param request 更新请求
     * @return 更新后的用户详情
     */
    @Operation(summary = "更新用户", description = "更新用户基础信息，并按 positions 列表对任职记录做增量 diff，状态请使用启用/停用接口")
    @PutMapping("/api/users/{id}")
    public UserVO update(@PathVariable Long id, @Valid @RequestBody UserUpdateRequest request) {
        return userService.update(id, request);
    }

    /**
     * 启用用户。
     *
     * @param id 用户 id
     * @return 更新后的用户详情
     */
    @Operation(summary = "启用用户")
    @PutMapping("/api/users/{id}/enable")
    public UserVO enable(@PathVariable Long id) {
        return userService.enable(id);
    }

    /**
     * 停用用户。
     *
     * @param id 用户 id
     * @return 更新后的用户详情
     */
    @Operation(summary = "停用用户")
    @PutMapping("/api/users/{id}/disable")
    public UserVO disable(@PathVariable Long id) {
        return userService.disable(id);
    }

    /**
     * 逻辑删除用户，不级联处理其名下的任职记录。
     *
     * @param id 用户 id
     * @return 无业务数据的成功响应
     */
    @Operation(summary = "删除用户", description = "逻辑删除，不级联处理其名下的任职记录")
    @DeleteMapping("/api/users/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return Result.success();
    }
}
