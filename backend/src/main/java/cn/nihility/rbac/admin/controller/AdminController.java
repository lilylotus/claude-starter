package cn.nihility.rbac.admin.controller;

import cn.nihility.rbac.admin.dto.AdminCreateRequest;
import cn.nihility.rbac.admin.dto.AdminUpdateRequest;
import cn.nihility.rbac.admin.dto.AdminVO;
import cn.nihility.rbac.admin.service.AdminService;
import cn.nihility.rbac.common.result.PageResult;
import cn.nihility.rbac.common.result.Result;
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
 * 管理员管理接口，提供管理员主数据的分页查询、维护、启停用、逻辑删除能力，
 * 以及随主数据一并整体提交的角色关联、组织管辖范围维护能力。
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "管理员管理", description = "管理员主数据及角色关联、组织管辖范围维护接口")
public class AdminController {

    /** 管理员业务逻辑接口。 */
    private final AdminService adminService;

    /**
     * 分页查询管理员，不支持筛选，按显示序号降序排列。
     *
     * @param page     页码，默认第 1 页
     * @param pageSize 每页条数，默认 10 条
     * @return 管理员的分页结果
     */
    @Operation(summary = "分页查询管理员", description = "不支持筛选参数，排除已逻辑删除的管理员，按显示序号降序、id 升序排列，"
            + "每条记录含关联用户姓名，不含角色列表与组织管辖范围列表")
    @GetMapping("/api/admins")
    public PageResult<AdminVO> page(
            @Parameter(description = "页码，默认第 1 页")
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @Parameter(description = "每页条数，默认 10 条")
            @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        return adminService.getPage(page, pageSize);
    }

    /**
     * 查询管理员详情，含其全部关联角色与全部管辖组织范围。
     *
     * @param id 管理员 id
     * @return 管理员详情
     */
    @Operation(summary = "查询管理员详情", description = "返回管理员完整信息及其全部关联角色、全部管辖组织范围")
    @GetMapping("/api/admins/{id}")
    public AdminVO detail(@PathVariable Long id) {
        return adminService.getById(id);
    }

    /**
     * 创建管理员，可同时提交角色 id 列表与管辖组织范围列表一并创建。
     *
     * @param request 创建请求
     * @return 创建后的管理员详情
     */
    @Operation(summary = "创建管理员", description = "编码在未删除管理员范围内需唯一，关联用户 id 同样需唯一，"
            + "可同时提交角色 id 列表、管辖组织范围列表一并创建，新建默认状态为启用")
    @PostMapping("/api/admins")
    public AdminVO create(@Valid @RequestBody AdminCreateRequest request) {
        return adminService.create(request);
    }

    /**
     * 更新管理员基础信息及角色关联、组织管辖范围，状态字段不通过本接口修改。
     *
     * @param id      管理员 id
     * @param request 更新请求
     * @return 更新后的管理员详情
     */
    @Operation(summary = "更新管理员", description = "更新管理员基础信息，角色列表、管辖组织范围按整体替换语义处理，"
            + "状态请使用启用/停用接口")
    @PutMapping("/api/admins/{id}")
    public AdminVO update(@PathVariable Long id, @Valid @RequestBody AdminUpdateRequest request) {
        return adminService.update(id, request);
    }

    /**
     * 启用管理员，不影响角色关联与组织管辖范围。
     *
     * @param id 管理员 id
     * @return 更新后的管理员详情
     */
    @Operation(summary = "启用管理员")
    @PutMapping("/api/admins/{id}/enable")
    public AdminVO enable(@PathVariable Long id) {
        return adminService.enable(id);
    }

    /**
     * 停用管理员，不影响角色关联与组织管辖范围。
     *
     * @param id 管理员 id
     * @return 更新后的管理员详情
     */
    @Operation(summary = "停用管理员")
    @PutMapping("/api/admins/{id}/disable")
    public AdminVO disable(@PathVariable Long id) {
        return adminService.disable(id);
    }

    /**
     * 逻辑删除管理员，不物理删除该管理员及其角色关联、组织管辖范围数据。
     *
     * @param id 管理员 id
     * @return 无业务数据的成功响应
     */
    @Operation(summary = "删除管理员", description = "逻辑删除，不物理删除该管理员及其角色关联、组织管辖范围数据")
    @DeleteMapping("/api/admins/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        adminService.delete(id);
        return Result.success();
    }
}
