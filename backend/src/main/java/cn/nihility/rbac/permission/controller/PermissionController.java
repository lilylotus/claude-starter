package cn.nihility.rbac.permission.controller;

import cn.nihility.rbac.common.result.PageResult;
import cn.nihility.rbac.common.result.Result;
import cn.nihility.rbac.permission.dto.PermissionCreateRequest;
import cn.nihility.rbac.permission.dto.PermissionOptionVO;
import cn.nihility.rbac.permission.dto.PermissionUpdateRequest;
import cn.nihility.rbac.permission.dto.PermissionVO;
import cn.nihility.rbac.permission.service.PermissionService;
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
 * 权限管理接口，提供权限点主数据的分页查询、维护、启停用和逻辑删除能力。
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "权限管理", description = "权限点主数据维护接口")
public class PermissionController {

    /** 权限业务逻辑接口。 */
    private final PermissionService permissionService;

    /**
     * 分页查询权限点，不支持筛选，按显示序号降序排列。
     *
     * @param page     页码，默认第 1 页
     * @param pageSize 每页条数，默认 10 条
     * @return 权限点的分页结果
     */
    @Operation(summary = "分页查询权限点", description = "不支持筛选参数，排除已逻辑删除的权限点，按显示序号降序、id 升序排列")
    @GetMapping("/api/permissions")
    public PageResult<PermissionVO> page(
            @Parameter(description = "页码，默认第 1 页")
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @Parameter(description = "每页条数，默认 10 条")
            @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        return permissionService.getPage(page, pageSize);
    }

    /**
     * 查询权限点详情。
     *
     * @param id 权限点 id
     * @return 权限点详情
     */
    @Operation(summary = "查询权限点详情")
    @GetMapping("/api/permissions/{id}")
    public PermissionVO detail(@PathVariable Long id) {
        return permissionService.getById(id);
    }

    /**
     * 创建权限点。
     *
     * @param request 创建请求
     * @return 创建后的权限点详情
     */
    @Operation(summary = "创建权限点")
    @PostMapping("/api/permissions")
    public PermissionVO create(@Valid @RequestBody PermissionCreateRequest request) {
        return permissionService.create(request);
    }

    /**
     * 更新权限点信息，状态字段不通过本接口修改。
     *
     * @param id      权限点 id
     * @param request 更新请求
     * @return 更新后的权限点详情
     */
    @Operation(summary = "更新权限点", description = "更新权限点基础信息，状态请使用启用/停用接口")
    @PutMapping("/api/permissions/{id}")
    public PermissionVO update(@PathVariable Long id, @Valid @RequestBody PermissionUpdateRequest request) {
        return permissionService.update(id, request);
    }

    /**
     * 启用权限点。
     *
     * @param id 权限点 id
     * @return 更新后的权限点详情
     */
    @Operation(summary = "启用权限点")
    @PutMapping("/api/permissions/{id}/enable")
    public PermissionVO enable(@PathVariable Long id) {
        return permissionService.enable(id);
    }

    /**
     * 停用权限点。
     *
     * @param id 权限点 id
     * @return 更新后的权限点详情
     */
    @Operation(summary = "停用权限点")
    @PutMapping("/api/permissions/{id}/disable")
    public PermissionVO disable(@PathVariable Long id) {
        return permissionService.disable(id);
    }

    /**
     * 逻辑删除权限点。
     *
     * @param id 权限点 id
     * @return 无业务数据的成功响应
     */
    @Operation(summary = "删除权限点", description = "逻辑删除")
    @DeleteMapping("/api/permissions/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        permissionService.delete(id);
        return Result.success();
    }

    /**
     * 查询全部启用状态的权限点精简选项列表，供其他模块的权限点勾选控件一次性
     * 加载全量选项使用，不分页。
     *
     * @return 启用状态的权限点精简选项列表
     */
    @Operation(summary = "查询启用权限点选项", description = "返回全部未被逻辑删除且状态为启用的权限点列表，"
            + "每项包含 id、name、code，按显示序号降序、id 升序排列，不分页")
    @GetMapping("/api/permissions/options")
    public List<PermissionOptionVO> options() {
        return permissionService.getEnabledOptions();
    }
}
