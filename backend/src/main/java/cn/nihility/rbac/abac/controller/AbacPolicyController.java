package cn.nihility.rbac.abac.controller;

import cn.nihility.rbac.abac.dto.AbacPolicyCreateRequest;
import cn.nihility.rbac.abac.dto.AbacPolicyQueryRequest;
import cn.nihility.rbac.abac.dto.AbacPolicyUpdateRequest;
import cn.nihility.rbac.abac.dto.AbacPolicyVO;
import cn.nihility.rbac.abac.service.AbacPolicyService;
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
 * ABAC 策略规则管理接口：分页查询、详情、新增、编辑、启停用、删除。在现有 RBAC 权限点
 * 判定通过之后，叠加一层基于用户属性 + 时间窗口的限制性（DENY-only）判定，供
 * {@code IdentityAuthFilter} 在运行时调用，本接口只负责策略规则的维护。
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "ABAC 策略管理", description = "ABAC 策略规则的定义与维护")
public class AbacPolicyController {

    /** ABAC 策略规则业务逻辑接口。 */
    private final AbacPolicyService abacPolicyService;

    /**
     * 分页查询策略规则，支持按名称模糊搜索、按状态过滤。
     *
     * @param name     策略名称，模糊匹配，可选
     * @param status   状态，精确匹配，可选
     * @param page     页码，默认第 1 页
     * @param pageSize 每页条数，默认 10 条
     * @return 策略规则的分页结果
     */
    @Operation(summary = "分页查询策略规则", description = "支持按名称模糊搜索、按状态过滤，均可选；默认按显示序号升序、id 升序排列")
    @GetMapping("/api/abac/policies")
    public PageResult<AbacPolicyVO> page(
            @Parameter(description = "策略名称，模糊匹配") @RequestParam(required = false) String name,
            @Parameter(description = "状态：2000=启用，3000=停用") @RequestParam(required = false) Integer status,
            @Parameter(description = "页码，默认第 1 页") @RequestParam(required = false, defaultValue = "1") Integer page,
            @Parameter(description = "每页条数，默认 10 条") @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        AbacPolicyQueryRequest request = new AbacPolicyQueryRequest();
        request.setName(name);
        request.setStatus(status);
        request.setPage(page);
        request.setPageSize(pageSize);
        return abacPolicyService.page(request);
    }

    /**
     * 查询策略规则详情，含目标资源编码/用户属性条件/时间窗口条件完整回显。
     *
     * @param id 策略 id
     * @return 策略规则详情
     */
    @Operation(summary = "查询策略规则详情")
    @GetMapping("/api/abac/policies/{id}")
    public AbacPolicyVO detail(@Parameter(description = "策略 id", required = true) @PathVariable Long id) {
        return abacPolicyService.detail(id);
    }

    /**
     * 新增策略规则。
     *
     * @param request 新增请求
     * @return 新增后的策略规则详情
     */
    @Operation(summary = "新增策略规则",
            description = "目标资源编码不能为空；用户属性条件、时间窗口条件两者中至少配置一类，不允许同时为空；"
                    + "显示序号可选，未提供时默认 0，仅用于列表展示排序，不参与判定优先级")
    @PostMapping("/api/abac/policies")
    public AbacPolicyVO create(@Valid @RequestBody AbacPolicyCreateRequest request) {
        return abacPolicyService.create(request);
    }

    /**
     * 编辑策略规则，目标资源编码/用户属性条件/时间窗口条件均为整体替换语义。
     *
     * @param id      策略 id
     * @param request 编辑请求
     * @return 编辑后的策略规则详情
     */
    @Operation(summary = "编辑策略规则",
            description = "目标资源编码/用户属性条件/时间窗口条件均为整体替换语义（先删后插）；用户属性条件、"
                    + "时间窗口条件两者中至少配置一类，不允许同时为空")
    @PutMapping("/api/abac/policies/{id}")
    public AbacPolicyVO update(@Parameter(description = "策略 id", required = true) @PathVariable Long id,
            @Valid @RequestBody AbacPolicyUpdateRequest request) {
        return abacPolicyService.update(id, request);
    }

    /**
     * 启用策略规则。
     *
     * @param id 策略 id
     * @return 更新后的策略规则详情
     */
    @Operation(summary = "启用策略规则", description = "启用后立即参与运行时 ABAC 判定，作为候选策略之一")
    @PutMapping("/api/abac/policies/{id}/enable")
    public AbacPolicyVO enable(@Parameter(description = "策略 id", required = true) @PathVariable Long id) {
        return abacPolicyService.enable(id);
    }

    /**
     * 停用策略规则。
     *
     * @param id 策略 id
     * @return 更新后的策略规则详情
     */
    @Operation(summary = "停用策略规则", description = "停用后立即不再作为运行时 ABAC 判定的候选策略，不影响任何用户对其目标资源编码的访问")
    @PutMapping("/api/abac/policies/{id}/disable")
    public AbacPolicyVO disable(@Parameter(description = "策略 id", required = true) @PathVariable Long id) {
        return abacPolicyService.disable(id);
    }

    /**
     * 删除策略规则，级联清理目标资源编码/用户属性条件/时间窗口条件。
     *
     * @param id 策略 id
     * @return 无业务数据的成功响应
     */
    @Operation(summary = "删除策略规则", description = "物理删除，级联清理该策略的目标资源编码/用户属性条件/时间窗口条件")
    @DeleteMapping("/api/abac/policies/{id}")
    public Result<Void> delete(@Parameter(description = "策略 id", required = true) @PathVariable Long id) {
        abacPolicyService.delete(id);
        return Result.success();
    }
}
