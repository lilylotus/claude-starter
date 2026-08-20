package cn.nihility.rbac.appaccess.controller;

import cn.nihility.rbac.appaccess.dto.AppAccessEffectiveItemVO;
import cn.nihility.rbac.appaccess.support.AppAccessEffectivePermissionService;
import cn.nihility.rbac.common.result.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 应用访问授权 - 最终生效权限查询接口：按用户查询其可访问的全部应用、按应用查询其全部
 * 可访问用户，均标明判定依据（spec.md"最终生效权限查询"需求）。
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "应用访问授权-最终生效权限查询", description = "策略授权与人工例外合并计算后的最终生效权限查询，只读")
public class AppAccessQueryController {

    /** 最终生效权限计算与查询业务逻辑接口。 */
    private final AppAccessEffectivePermissionService appAccessEffectivePermissionService;

    /**
     * 按用户查询其当前最终生效可访问的全部应用。
     *
     * @param userId         用户 id
     * @param includeRevoked 是否额外返回被人工收回、但本应命中策略的应用，供审计查看，
     *                       默认 {@code false}
     * @param page           页码，默认第 1 页
     * @param pageSize       每页条数，默认 10 条
     * @return 分页结果
     */
    @Operation(summary = "按用户查询最终生效权限", description = "includeRevoked=true 时额外返回被人工收回、但本应命中策略的应用，标记为 REVOKED")
    @GetMapping("/api/app-access/effective/by-user/{userId}")
    public PageResult<AppAccessEffectiveItemVO> listByUser(@PathVariable Long userId,
            @Parameter(description = "是否包含被人工收回的应用，供审计查看")
            @RequestParam(required = false, defaultValue = "false") Boolean includeRevoked,
            @Parameter(description = "页码，默认第 1 页") @RequestParam(required = false, defaultValue = "1") Integer page,
            @Parameter(description = "每页条数，默认 10 条") @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        return appAccessEffectivePermissionService.listEffectiveByUser(userId, Boolean.TRUE.equals(includeRevoked),
                page, pageSize);
    }

    /**
     * 按应用查询其当前最终生效可访问的全部用户。
     *
     * @param appId    应用 id
     * @param page     页码，默认第 1 页
     * @param pageSize 每页条数，默认 10 条
     * @return 分页结果
     */
    @Operation(summary = "按应用查询最终生效权限")
    @GetMapping("/api/app-access/effective/by-app/{appId}")
    public PageResult<AppAccessEffectiveItemVO> listByApp(@PathVariable Long appId,
            @Parameter(description = "页码，默认第 1 页") @RequestParam(required = false, defaultValue = "1") Integer page,
            @Parameter(description = "每页条数，默认 10 条") @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        return appAccessEffectivePermissionService.listEffectiveByApp(appId, page, pageSize);
    }
}
