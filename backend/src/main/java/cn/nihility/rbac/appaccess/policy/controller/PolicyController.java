package cn.nihility.rbac.appaccess.policy.controller;

import cn.nihility.rbac.appaccess.policy.dto.PolicyCreateRequest;
import cn.nihility.rbac.appaccess.policy.dto.PolicyQueryRequest;
import cn.nihility.rbac.appaccess.policy.dto.PolicyUpdateRequest;
import cn.nihility.rbac.appaccess.policy.dto.PolicyVO;
import cn.nihility.rbac.appaccess.policy.service.PolicyExecutionService;
import cn.nihility.rbac.appaccess.policy.service.PolicyService;
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
 * 应用访问授权 - 策略规则管理接口：分页查询、详情、新增、编辑、启停用、删除、手动执行。
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "应用访问授权-策略规则", description = "策略规则的定义、维护与手动执行")
public class PolicyController {

    /** 策略规则业务逻辑接口。 */
    private final PolicyService policyService;

    /** 策略规则手动执行业务逻辑接口。 */
    private final PolicyExecutionService policyExecutionService;

    /**
     * 分页查询策略规则，支持按名称模糊搜索、按状态过滤。
     *
     * @param name     策略名称，模糊匹配，可选
     * @param status   状态，精确匹配，可选
     * @param page     页码，默认第 1 页
     * @param pageSize 每页条数，默认 10 条
     * @return 策略规则的分页结果
     */
    @Operation(summary = "分页查询策略规则", description = "支持按名称模糊搜索、按状态过滤，均可选")
    @GetMapping("/api/app-access/policies")
    public PageResult<PolicyVO> page(
            @Parameter(description = "策略名称，模糊匹配") @RequestParam(required = false) String name,
            @Parameter(description = "状态：2000=启用，3000=停用") @RequestParam(required = false) Integer status,
            @Parameter(description = "页码，默认第 1 页") @RequestParam(required = false, defaultValue = "1") Integer page,
            @Parameter(description = "每页条数，默认 10 条") @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        PolicyQueryRequest request = new PolicyQueryRequest();
        request.setName(name);
        request.setStatus(status);
        request.setPage(page);
        request.setPageSize(pageSize);
        return policyService.page(request);
    }

    /**
     * 查询策略规则详情，含组织范围/用户属性条件/目标应用完整回显。
     *
     * @param id 策略 id
     * @return 策略规则详情
     */
    @Operation(summary = "查询策略规则详情")
    @GetMapping("/api/app-access/policies/{id}")
    public PolicyVO detail(@PathVariable Long id) {
        return policyService.detail(id);
    }

    /**
     * 新增策略规则。
     *
     * @param request 新增请求
     * @return 新增后的策略规则详情
     */
    @Operation(summary = "新增策略规则", description = "组织范围与用户属性条件均可选，但不能同时为空；目标应用不能为空")
    @PostMapping("/api/app-access/policies")
    public PolicyVO create(@Valid @RequestBody PolicyCreateRequest request) {
        return policyService.create(request);
    }

    /**
     * 编辑策略规则，组织范围/用户属性条件/目标应用均为整体替换语义。
     *
     * @param id      策略 id
     * @param request 编辑请求
     * @return 编辑后的策略规则详情
     */
    @Operation(summary = "编辑策略规则", description = "组织范围/用户属性条件/目标应用均为整体替换语义（先删后插）")
    @PutMapping("/api/app-access/policies/{id}")
    public PolicyVO update(@PathVariable Long id, @Valid @RequestBody PolicyUpdateRequest request) {
        return policyService.update(id, request);
    }

    /**
     * 启用策略规则。
     *
     * @param id 策略 id
     * @return 更新后的策略规则详情
     */
    @Operation(summary = "启用策略规则", description = "启用后其产生的策略授权记录立即计入最终生效权限，无需重新执行")
    @PutMapping("/api/app-access/policies/{id}/enable")
    public PolicyVO enable(@PathVariable Long id) {
        return policyService.enable(id);
    }

    /**
     * 停用策略规则。
     *
     * @param id 策略 id
     * @return 更新后的策略规则详情
     */
    @Operation(summary = "停用策略规则", description = "停用后其产生的策略授权记录立即不再计入最终生效权限，无需清空记录")
    @PutMapping("/api/app-access/policies/{id}/disable")
    public PolicyVO disable(@PathVariable Long id) {
        return policyService.disable(id);
    }

    /**
     * 删除策略规则，级联清理其产生的全部策略授权记录。
     *
     * @param id 策略 id
     * @return 无业务数据的成功响应
     */
    @Operation(summary = "删除策略规则", description = "级联删除该策略已产生的全部策略授权记录，不影响人工例外记录")
    @DeleteMapping("/api/app-access/policies/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        policyService.delete(id);
        return Result.success();
    }

    /**
     * 手动执行策略规则：重新计算命中用户并整体重建该策略产生的策略授权记录。
     *
     * @param id 策略 id
     * @return 执行后的策略规则详情
     */
    @Operation(summary = "执行策略规则", description = "重新计算命中用户，整体重建该策略产生的策略授权记录，不影响人工例外记录")
    @PostMapping("/api/app-access/policies/{id}/execute")
    public PolicyVO execute(@PathVariable Long id) {
        return policyExecutionService.execute(id);
    }
}
