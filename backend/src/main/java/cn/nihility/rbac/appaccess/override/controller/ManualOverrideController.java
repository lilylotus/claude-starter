package cn.nihility.rbac.appaccess.override.controller;

import cn.nihility.rbac.appaccess.override.dto.ManualOverrideQueryRequest;
import cn.nihility.rbac.appaccess.override.dto.ManualOverrideUpsertRequest;
import cn.nihility.rbac.appaccess.override.dto.ManualOverrideVO;
import cn.nihility.rbac.appaccess.override.service.ManualOverrideService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 应用访问授权 - 人工例外管理接口：分页查询、新增/更新（upsert 语义）、删除。
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "应用访问授权-人工例外", description = "对具体用户+应用组合手动追加授权/手动收回授权")
public class ManualOverrideController {

    /** 人工例外业务逻辑接口。 */
    private final ManualOverrideService manualOverrideService;

    /**
     * 分页查询人工例外，支持按用户、应用、例外类型过滤。
     *
     * @param userId       用户 id，精确匹配，可选
     * @param appId        应用 id，精确匹配，可选
     * @param overrideType 例外类型，精确匹配，可选
     * @param page         页码，默认第 1 页
     * @param pageSize     每页条数，默认 10 条
     * @return 人工例外的分页结果
     */
    @Operation(summary = "分页查询人工例外", description = "支持按用户、应用、例外类型过滤，均可选")
    @GetMapping("/api/app-access/manual-overrides")
    public PageResult<ManualOverrideVO> page(
            @Parameter(description = "用户 id，精确匹配") @RequestParam(required = false) Long userId,
            @Parameter(description = "应用 id，精确匹配") @RequestParam(required = false) Long appId,
            @Parameter(description = "例外类型：GRANT/DENY") @RequestParam(required = false) String overrideType,
            @Parameter(description = "页码，默认第 1 页") @RequestParam(required = false, defaultValue = "1") Integer page,
            @Parameter(description = "每页条数，默认 10 条") @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        ManualOverrideQueryRequest request = new ManualOverrideQueryRequest();
        request.setUserId(userId);
        request.setAppId(appId);
        request.setOverrideType(overrideType);
        request.setPage(page);
        request.setPageSize(pageSize);
        return manualOverrideService.page(request);
    }

    /**
     * 新增或更新人工例外：{@code userId+appId} 组合已存在记录时更新，不新增一行。
     *
     * @param request 新增/更新请求
     * @return 保存后的人工例外详情
     */
    @Operation(summary = "新增或更新人工例外", description = "userId+appId 组合已存在记录时更新 overrideType/remark，不新增一行")
    @PostMapping("/api/app-access/manual-overrides")
    public ManualOverrideVO upsert(@Valid @RequestBody ManualOverrideUpsertRequest request) {
        return manualOverrideService.upsert(request);
    }

    /**
     * 删除人工例外。
     *
     * @param id 人工例外 id
     * @return 无业务数据的成功响应
     */
    @Operation(summary = "删除人工例外", description = "删除后该用户+应用组合的最终生效权限退回只看策略授权")
    @DeleteMapping("/api/app-access/manual-overrides/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        manualOverrideService.delete(id);
        return Result.success();
    }
}
