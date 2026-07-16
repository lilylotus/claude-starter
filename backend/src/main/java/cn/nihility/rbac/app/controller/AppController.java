package cn.nihility.rbac.app.controller;

import cn.nihility.rbac.app.dto.AppCreateRequest;
import cn.nihility.rbac.app.dto.AppUpdateRequest;
import cn.nihility.rbac.app.dto.AppVO;
import cn.nihility.rbac.app.service.AppService;
import cn.nihility.rbac.common.PageResult;
import cn.nihility.rbac.common.Result;
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
 * 应用管理接口，提供应用主数据的分页查询、维护、启停用和逻辑删除能力。
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "应用管理", description = "应用主数据维护接口")
public class AppController {

    /** 应用业务逻辑接口。 */
    private final AppService appService;

    /**
     * 分页查询应用，不支持筛选，按显示序号降序排列。
     *
     * @param page     页码，默认第 1 页
     * @param pageSize 每页条数，默认 10 条
     * @return 应用的分页结果
     */
    @Operation(summary = "分页查询应用", description = "不支持筛选参数，排除已逻辑删除的应用，按显示序号降序、id 升序排列")
    @GetMapping("/api/apps")
    public PageResult<AppVO> page(
            @Parameter(description = "页码，默认第 1 页")
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @Parameter(description = "每页条数，默认 10 条")
            @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        return appService.getPage(page, pageSize);
    }

    /**
     * 查询应用详情。
     *
     * @param id 应用 id
     * @return 应用详情
     */
    @Operation(summary = "查询应用详情")
    @GetMapping("/api/apps/{id}")
    public AppVO detail(@PathVariable Long id) {
        return appService.getById(id);
    }

    /**
     * 创建应用。
     *
     * @param request 创建请求
     * @return 创建后的应用详情
     */
    @Operation(summary = "创建应用")
    @PostMapping("/api/apps")
    public AppVO create(@Valid @RequestBody AppCreateRequest request) {
        return appService.create(request);
    }

    /**
     * 更新应用信息，状态字段不通过本接口修改。
     *
     * @param id      应用 id
     * @param request 更新请求
     * @return 更新后的应用详情
     */
    @Operation(summary = "更新应用", description = "更新应用基础信息，状态请使用启用/停用接口")
    @PutMapping("/api/apps/{id}")
    public AppVO update(@PathVariable Long id, @Valid @RequestBody AppUpdateRequest request) {
        return appService.update(id, request);
    }

    /**
     * 启用应用。
     *
     * @param id 应用 id
     * @return 更新后的应用详情
     */
    @Operation(summary = "启用应用")
    @PutMapping("/api/apps/{id}/enable")
    public AppVO enable(@PathVariable Long id) {
        return appService.enable(id);
    }

    /**
     * 停用应用。
     *
     * @param id 应用 id
     * @return 更新后的应用详情
     */
    @Operation(summary = "停用应用")
    @PutMapping("/api/apps/{id}/disable")
    public AppVO disable(@PathVariable Long id) {
        return appService.disable(id);
    }

    /**
     * 逻辑删除应用。
     *
     * @param id 应用 id
     * @return 无业务数据的成功响应
     */
    @Operation(summary = "删除应用", description = "逻辑删除")
    @DeleteMapping("/api/apps/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        appService.delete(id);
        return Result.success();
    }
}
