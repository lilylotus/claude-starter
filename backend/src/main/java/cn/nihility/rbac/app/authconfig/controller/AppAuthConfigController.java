package cn.nihility.rbac.app.authconfig.controller;

import cn.nihility.rbac.app.authconfig.dto.AppAuthConfigUpdateRequest;
import cn.nihility.rbac.app.authconfig.dto.AppAuthConfigVO;
import cn.nihility.rbac.app.authconfig.service.AppAuthConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 应用单点登录协议配置接口，提供协议类型（无/CAS/OAuth2.0）与回跳地址匹配列表的查询、修改
 * （app-auth-protocol-config change proposal.md）。写操作复用 {@code AppConfigController}
 * 既有模式，权限点 {@code AppManagement:app:config:editAuth} 在前端路由/按钮层面控制，
 * 接口层不新增权限校验注解。本模块只负责管理后台的配置维护，不实现真正的 CAS/OAuth2
 * 协议运行时端点（proposal.md Non-Goals）。
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "应用认证管理", description = "应用单点登录协议配置（协议类型、CAS/OAuth2 回跳地址匹配列表）维护接口")
public class AppAuthConfigController {

    /** 应用单点登录协议配置业务逻辑接口。 */
    private final AppAuthConfigService appAuthConfigService;

    /**
     * 查询应用认证配置。
     *
     * @param id 应用 id（{@code tab_app.id}）
     * @return 应用单点登录协议配置详情
     */
    @Operation(summary = "查询认证配置", description = "返回协议类型、匹配列表，以及按该应用 AppId 计算出的协议接口地址")
    @GetMapping("/api/apps/{id}/config/auth")
    public AppAuthConfigVO getAuthConfig(@PathVariable Long id) {
        return appAuthConfigService.getByAppId(id);
    }

    /**
     * 修改应用认证配置。
     *
     * @param id      应用 id（{@code tab_app.id}）
     * @param request 认证配置修改请求
     * @return 修改后的应用单点登录协议配置详情
     */
    @Operation(summary = "修改认证配置", description = "修改协议类型及对应的匹配列表；协议类型为 CAS/OAuth2.0 时对应匹配列表不能为空，"
            + "协议类型为无时两个匹配列表均清空")
    @PutMapping("/api/apps/{id}/config/auth")
    public AppAuthConfigVO updateAuthConfig(@PathVariable Long id,
            @Valid @RequestBody AppAuthConfigUpdateRequest request) {
        return appAuthConfigService.updateConfig(id, request);
    }
}
