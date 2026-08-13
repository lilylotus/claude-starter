package cn.nihility.rbac.app.sync.controller;

import cn.nihility.rbac.app.sync.dto.AppSyncDomainConfigUpdateRequest;
import cn.nihility.rbac.app.sync.dto.AppSyncDomainConfigVO;
import cn.nihility.rbac.app.sync.dto.AppSyncFieldMappingSaveRequest;
import cn.nihility.rbac.app.sync.dto.AppSyncFieldMappingVO;
import cn.nihility.rbac.app.sync.dto.AppSyncOrgScopeRequest;
import cn.nihility.rbac.app.sync.dto.AppSyncOrgScopeVO;
import cn.nihility.rbac.app.sync.service.AppSyncConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 应用同步配置接口，提供组织/用户/任职/应用/角色/字典 6 个数据域启用开关+拉取分页大小的查询、
 * 修改，以及组织/用户/任职/应用/角色 5 个数据域字段级同步映射的查询、整体替换保存
 * （app-sync-field-mapping change proposal.md）。写操作复用 {@code AppConfigController}
 * 既有的权限点 {@code AppManagement:app:config:editSync}（前端路由/按钮层面控制，接口层
 * 不新增权限校验注解，与 {@code AppConfigController} 现状一致）。本模块只负责管理后台的
 * 配置维护，不实现真正的数据拉取/转换执行/对外通知接口。
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "应用同步配置", description = "同步数据域配置（启用开关、拉取分页大小）与字段级同步映射维护接口")
public class AppSyncConfigController {

    /** 应用同步配置业务逻辑接口。 */
    private final AppSyncConfigService appSyncConfigService;

    /**
     * 查询应用的 6 个数据域配置。
     *
     * @param id 应用 id（{@code tab_app.id}）
     * @return 数据域配置列表
     */
    @Operation(summary = "查询同步数据域配置", description = "返回组织/用户/任职/应用/角色/字典 6 行数据域配置")
    @GetMapping("/api/apps/{id}/config/sync/domains")
    public List<AppSyncDomainConfigVO> listDomainConfigs(@PathVariable Long id) {
        return appSyncConfigService.listDomainConfigs(id);
    }

    /**
     * 修改应用某个数据域的启用开关与拉取分页大小。
     *
     * @param id         应用 id（{@code tab_app.id}）
     * @param syncDomain 数据域：ORG/USER/POSITION/APP/ROLE/DICT
     * @param request    数据域配置修改请求
     * @return 修改后的数据域配置
     */
    @Operation(summary = "修改同步数据域配置", description = "修改指定数据域的启用开关与拉取分页大小")
    @PutMapping("/api/apps/{id}/config/sync/domains/{syncDomain}")
    public AppSyncDomainConfigVO updateDomainConfig(@PathVariable Long id,
            @Parameter(description = "数据域：ORG/USER/POSITION/APP/ROLE/DICT") @PathVariable String syncDomain,
            @Valid @RequestBody AppSyncDomainConfigUpdateRequest request) {
        return appSyncConfigService.updateDomainConfig(id, syncDomain, request);
    }

    /**
     * 查询应用某个数据域的字段映射列表。
     *
     * @param id     应用 id（{@code tab_app.id}）
     * @param domain 数据域：ORG/USER/POSITION/APP/ROLE，传 DICT 时直接返回空列表
     * @return 字段映射列表
     */
    @Operation(summary = "查询同步字段映射", description = "查询组织/用户/任职/应用/角色数据域的字段级同步映射列表，字典数据域直接返回空列表")
    @GetMapping("/api/apps/{id}/config/sync/field-mappings")
    public List<AppSyncFieldMappingVO> listFieldMappings(@PathVariable Long id,
            @Parameter(description = "数据域：ORG/USER/POSITION/APP/ROLE") @RequestParam String domain) {
        return appSyncConfigService.listFieldMappings(id, domain);
    }

    /**
     * 整体替换应用某个数据域的字段映射列表。
     *
     * @param id       应用 id（{@code tab_app.id}）
     * @param domain   数据域：ORG/USER/POSITION/APP/ROLE（不支持 DICT）
     * @param requests 本次提交的完整字段映射行列表
     * @return 保存后的字段映射列表
     */
    @Operation(summary = "保存同步字段映射", description = "整体替换语义：提交当前数据域的完整字段映射行列表，先删后插，"
            + "不支持字典数据域；转换方式为转换脚本时保存前做 JavaScript 语法校验（不执行）")
    @PutMapping("/api/apps/{id}/config/sync/field-mappings")
    public List<AppSyncFieldMappingVO> replaceFieldMappings(@PathVariable Long id,
            @Parameter(description = "数据域：ORG/USER/POSITION/APP/ROLE") @RequestParam String domain,
            @Valid @RequestBody List<AppSyncFieldMappingSaveRequest> requests) {
        return appSyncConfigService.replaceFieldMappings(id, domain, requests);
    }

    /**
     * 查询应用某个数据域配置的同步范围。
     *
     * @param id         应用 id（{@code tab_app.id}）
     * @param syncDomain 数据域：ORG/USER/POSITION
     * @return 同步范围（组织）列表，空列表表示"全部数据"
     */
    @Operation(summary = "查询同步范围", description = "查询组织/用户/任职数据域按应用配置的同步范围（组织列表），空列表表示全部数据")
    @GetMapping("/api/apps/{id}/config/sync/domains/{syncDomain}/org-scope")
    public List<AppSyncOrgScopeVO> listOrgScope(@PathVariable Long id,
            @Parameter(description = "数据域：ORG/USER/POSITION") @PathVariable String syncDomain) {
        return appSyncConfigService.listOrgScope(id, syncDomain);
    }

    /**
     * 整体替换应用某个数据域配置的同步范围。
     *
     * @param id         应用 id（{@code tab_app.id}）
     * @param syncDomain 数据域：ORG/USER/POSITION
     * @param requests   本次提交的完整同步范围行列表，空列表表示改为"全部数据"
     * @return 保存后的同步范围列表
     */
    @Operation(summary = "保存同步范围", description = "整体替换语义：提交当前数据域的完整同步范围（组织）行列表，先删后插，"
            + "空列表表示改为全部数据，仅支持组织/用户/任职三个数据域")
    @PutMapping("/api/apps/{id}/config/sync/domains/{syncDomain}/org-scope")
    public List<AppSyncOrgScopeVO> replaceOrgScope(@PathVariable Long id,
            @Parameter(description = "数据域：ORG/USER/POSITION") @PathVariable String syncDomain,
            @Valid @RequestBody List<AppSyncOrgScopeRequest> requests) {
        return appSyncConfigService.replaceOrgScope(id, syncDomain, requests);
    }
}
