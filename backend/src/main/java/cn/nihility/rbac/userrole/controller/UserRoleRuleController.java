package cn.nihility.rbac.userrole.controller;

import cn.nihility.rbac.common.result.PageResult;
import cn.nihility.rbac.common.result.Result;
import cn.nihility.rbac.userrole.dto.UserRoleMatchedUserVO;
import cn.nihility.rbac.userrole.dto.UserRoleRuleCreateRequest;
import cn.nihility.rbac.userrole.dto.UserRoleRulePreviewRequest;
import cn.nihility.rbac.userrole.dto.UserRoleRuleUpdateRequest;
import cn.nihility.rbac.userrole.dto.UserRoleRuleVO;
import cn.nihility.rbac.userrole.service.UserRoleRuleService;
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
 * 用户角色规则接口：把角色批量打到匹配用户身上的能力从一次性操作改为持久规则维护，规则
 * 保存（新增/编辑）后立即执行一次，此外组织/用户/任职变更后系统会自动对全部规则重新执行
 * （见 {@code cn.nihility.rbac.sync.event.support.DomainChangeEventProcessor}）。前端调用
 * 本接口需在 {@code menu} 请求头携带 {@code RoleManagement:role:batchAssignUser} 权限点
 * 编码，由 {@code cn.nihility.rbac.auth.filter.IdentityAuthFilter} 统一校验，本 Controller
 * 不重复实现权限判断。
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "用户角色规则", description = "角色管理页面批量规则的查询、预览、新增、编辑、删除接口")
public class UserRoleRuleController {

    /** 用户角色规则业务逻辑接口。 */
    private final UserRoleRuleService userRoleRuleService;

    /**
     * 按角色 id 查询其全部规则的摘要列表。
     *
     * @param roleId 目标角色 id
     * @return 规则摘要列表
     */
    @Operation(summary = "查询角色的用户角色规则列表",
            description = "返回该角色的全部规则摘要（id/名称/备注/最近执行时间/最近执行人/当前命中人数），"
                    + "不含组织范围/用户属性条件明细，明细请调用查询规则详情接口")
    @GetMapping("/api/user-role-rules")
    public List<UserRoleRuleVO> list(
            @Parameter(description = "目标角色 id", required = true)
            @RequestParam Long roleId) {
        return userRoleRuleService.listByRoleId(roleId);
    }

    /**
     * 查询单条规则详情，含其全部组织范围条件、用户属性条件明细。
     *
     * @param id 规则 id
     * @return 规则详情
     */
    @Operation(summary = "查询用户角色规则详情", description = "含全部组织范围条件、用户属性条件明细，供编辑表单回填")
    @GetMapping("/api/user-role-rules/{id}")
    public UserRoleRuleVO detail(@PathVariable Long id) {
        return userRoleRuleService.getById(id);
    }

    /**
     * 按给定条件预览命中用户，不依赖已保存的规则、不写库。
     *
     * @param request 预览请求参数
     * @return 命中用户的分页结果
     */
    @Operation(summary = "预览用户角色规则命中用户", description = "不依赖已保存的规则，直接用给定条件现算命中用户分页列表，不写库；"
            + "组织范围、用户属性条件至少配置一类，均未配置时拒绝请求")
    @PostMapping("/api/user-role-rules/preview")
    public PageResult<UserRoleMatchedUserVO> preview(@Valid @RequestBody UserRoleRulePreviewRequest request) {
        return userRoleRuleService.preview(request);
    }

    /**
     * 新增用户角色规则，保存成功后立即按当前条件执行一次。
     *
     * @param request 新增请求参数
     * @return 新建后的规则详情（含本次命中人数）
     */
    @Operation(summary = "新增用户角色规则", description = "组织范围、用户属性条件至少配置一类，保存成功后立即执行一次并返回命中人数")
    @PostMapping("/api/user-role-rules")
    public UserRoleRuleVO create(@Valid @RequestBody UserRoleRuleCreateRequest request) {
        return userRoleRuleService.create(request);
    }

    /**
     * 编辑用户角色规则，保存成功后立即按新条件重新执行一次。
     *
     * @param id      规则 id
     * @param request 编辑请求参数
     * @return 更新后的规则详情
     */
    @Operation(summary = "编辑用户角色规则", description = "条件子表整体替换，保存成功后立即按新条件重新执行一次")
    @PutMapping("/api/user-role-rules/{id}")
    public UserRoleRuleVO update(@PathVariable Long id, @Valid @RequestBody UserRoleRuleUpdateRequest request) {
        return userRoleRuleService.update(id, request);
    }

    /**
     * 删除用户角色规则，级联收回该规则已产生的全部角色关联。
     *
     * @param id 规则 id
     * @return 无业务数据的成功响应
     */
    @Operation(summary = "删除用户角色规则", description = "先收回该规则已产生的全部角色关联（触发联动停用检查），再物理删除规则本身")
    @DeleteMapping("/api/user-role-rules/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        userRoleRuleService.delete(id);
        return Result.success();
    }
}
