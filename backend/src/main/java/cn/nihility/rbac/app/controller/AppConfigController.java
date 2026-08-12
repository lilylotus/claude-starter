package cn.nihility.rbac.app.controller;

import cn.nihility.rbac.app.dto.AppConfigVO;
import cn.nihility.rbac.app.dto.SecretKeyVO;
import cn.nihility.rbac.app.dto.SignAlgorithmUpdateRequest;
import cn.nihility.rbac.app.dto.SyncConfigUpdateRequest;
import cn.nihility.rbac.app.service.AppConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 应用配置接口，提供每个应用一对一的对外接口凭证（AppId/AccessKey/SecretKey）与配置
 * （签名算法、同步范围开关）的查询、修改、重置能力。本模块只负责管理后台的配置维护，不实现
 * 任何真正对外开放的签名验签或数据同步接口（app-api-credentials-config change proposal.md
 * Non-Goals）。
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "应用配置", description = "应用对外接口凭证与配置维护接口")
public class AppConfigController {

    /** 应用配置业务逻辑接口。 */
    private final AppConfigService appConfigService;

    /**
     * 查询应用配置详情。
     *
     * @param id 应用 id（{@code tab_app.id}）
     * @return 应用配置详情
     */
    @Operation(summary = "查询应用配置", description = "不返回 SecretKey 明文/密文")
    @GetMapping("/api/apps/{id}/config")
    public AppConfigVO getConfig(@PathVariable Long id) {
        return appConfigService.getByAppId(id);
    }

    /**
     * 修改应用的接口签名算法。
     *
     * @param id      应用 id（{@code tab_app.id}）
     * @param request 签名算法修改请求
     * @return 修改后的应用配置详情
     */
    @Operation(summary = "修改签名算法", description = "只允许 SHA256 或 SM3")
    @PutMapping("/api/apps/{id}/config/sign-algorithm")
    public AppConfigVO updateSignAlgorithm(@PathVariable Long id,
            @Valid @RequestBody SignAlgorithmUpdateRequest request) {
        return appConfigService.updateSignAlgorithm(id, request);
    }

    /**
     * 修改应用的数据同步范围开关。
     *
     * @param id      应用 id（{@code tab_app.id}）
     * @param request 同步配置修改请求
     * @return 修改后的应用配置详情
     */
    @Operation(summary = "修改同步配置",
            description = "整个应用一份的同步方式（NOTIFY/PULL）、通知模式下的回调接口地址与自定义参数；"
                    + "组织/用户/应用/角色/字典五个数据域各自的启用开关、拉取分页大小与字段级同步映射，"
                    + "改由 AppSyncConfigController 的数据域配置/字段映射接口单独维护")
    @PutMapping("/api/apps/{id}/config/sync")
    public AppConfigVO updateSyncConfig(@PathVariable Long id, @Valid @RequestBody SyncConfigUpdateRequest request) {
        return appConfigService.updateSyncConfig(id, request);
    }

    /**
     * 重置应用的 SecretKey，旧值立即失效，响应体单次返回新值明文。
     *
     * @param id 应用 id（{@code tab_app.id}）
     * @return 携带新 SecretKey 明文的响应对象
     */
    @Operation(summary = "重置 SecretKey", description = "生成新值替换当前值，响应体仅本次返回明文，此后不可再查看")
    @PostMapping("/api/apps/{id}/config/secret-key/reset")
    public SecretKeyVO resetSecretKey(@PathVariable Long id) {
        return appConfigService.resetSecretKey(id);
    }
}
