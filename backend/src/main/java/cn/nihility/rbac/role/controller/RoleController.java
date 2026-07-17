package cn.nihility.rbac.role.controller;

import cn.nihility.rbac.common.PageResult;
import cn.nihility.rbac.common.Result;
import cn.nihility.rbac.role.dto.RoleCreateRequest;
import cn.nihility.rbac.role.dto.RoleOptionVO;
import cn.nihility.rbac.role.dto.RoleUpdateRequest;
import cn.nihility.rbac.role.dto.RoleVO;
import cn.nihility.rbac.role.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
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
 * 角色管理接口，提供角色主数据的分页查询、维护、启停用和逻辑删除能力。
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "角色管理", description = "角色主数据维护接口")
public class RoleController {

    /** 角色业务逻辑接口。 */
    private final RoleService roleService;

    /**
     * 分页查询角色，不支持筛选，按显示序号降序排列。
     *
     * @param page     页码，默认第 1 页
     * @param pageSize 每页条数，默认 10 条
     * @return 角色的分页结果
     */
    @Operation(summary = "分页查询角色", description = "不支持筛选参数，排除已逻辑删除的角色，按显示序号降序、id 升序排列")
    @GetMapping("/api/roles")
    public PageResult<RoleVO> page(
            @Parameter(description = "页码，默认第 1 页")
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @Parameter(description = "每页条数，默认 10 条")
            @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        return roleService.getPage(page, pageSize);
    }

    /**
     * 查询角色详情。
     *
     * @param id 角色 id
     * @return 角色详情
     */
    @Operation(summary = "查询角色详情")
    @GetMapping("/api/roles/{id}")
    public RoleVO detail(@PathVariable Long id) {
        return roleService.getById(id);
    }

    /**
     * 创建角色。
     *
     * @param request 创建请求
     * @return 创建后的角色详情
     */
    @Operation(summary = "创建角色")
    @PostMapping("/api/roles")
    public RoleVO create(@Valid @RequestBody RoleCreateRequest request) {
        return roleService.create(request);
    }

    /**
     * 更新角色信息，状态字段不通过本接口修改。
     *
     * @param id      角色 id
     * @param request 更新请求
     * @return 更新后的角色详情
     */
    @Operation(summary = "更新角色", description = "更新角色基础信息，状态请使用启用/停用接口")
    @PutMapping("/api/roles/{id}")
    public RoleVO update(@PathVariable Long id, @Valid @RequestBody RoleUpdateRequest request) {
        return roleService.update(id, request);
    }

    /**
     * 启用角色。
     *
     * @param id 角色 id
     * @return 更新后的角色详情
     */
    @Operation(summary = "启用角色")
    @PutMapping("/api/roles/{id}/enable")
    public RoleVO enable(@PathVariable Long id) {
        return roleService.enable(id);
    }

    /**
     * 停用角色。
     *
     * @param id 角色 id
     * @return 更新后的角色详情
     */
    @Operation(summary = "停用角色")
    @PutMapping("/api/roles/{id}/disable")
    public RoleVO disable(@PathVariable Long id) {
        return roleService.disable(id);
    }

    /**
     * 逻辑删除角色。
     *
     * @param id 角色 id
     * @return 无业务数据的成功响应
     */
    @Operation(summary = "删除角色", description = "逻辑删除")
    @DeleteMapping("/api/roles/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        roleService.delete(id);
        return Result.success();
    }

    /**
     * 查询全部启用状态的角色精简选项列表，供其他模块的角色多选/单选选择器一次性
     * 加载全量选项使用，不分页。
     *
     * @return 启用状态的角色精简选项列表
     */
    @Operation(summary = "查询启用角色选项", description = "返回全部未被逻辑删除且状态为启用的角色列表，"
            + "每项包含 id、name、code，按显示序号降序、id 升序排列，不分页")
    @GetMapping("/api/roles/options")
    public List<RoleOptionVO> options() {
        return roleService.getEnabledOptions();
    }
}
