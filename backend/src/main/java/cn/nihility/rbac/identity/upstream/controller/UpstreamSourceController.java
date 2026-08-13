package cn.nihility.rbac.identity.upstream.controller;

import cn.nihility.rbac.common.result.Result;
import cn.nihility.rbac.identity.upstream.dto.UpstreamConnectionConfigRequest;
import cn.nihility.rbac.identity.upstream.dto.UpstreamDomainConfigUpdateRequest;
import cn.nihility.rbac.identity.upstream.dto.UpstreamDomainConfigVO;
import cn.nihility.rbac.identity.upstream.dto.UpstreamFieldMappingSaveRequest;
import cn.nihility.rbac.identity.upstream.dto.UpstreamFieldMappingVO;
import cn.nihility.rbac.identity.upstream.dto.UpstreamScheduleConfigRequest;
import cn.nihility.rbac.identity.upstream.dto.UpstreamSourceCreateRequest;
import cn.nihility.rbac.identity.upstream.dto.UpstreamSourceUpdateRequest;
import cn.nihility.rbac.identity.upstream.dto.UpstreamSourceVO;
import cn.nihility.rbac.identity.upstream.dto.UpstreamSyncRecordVO;
import cn.nihility.rbac.identity.upstream.service.UpstreamDomainConfigService;
import cn.nihility.rbac.identity.upstream.service.UpstreamFieldMappingService;
import cn.nihility.rbac.identity.upstream.service.UpstreamSourceService;
import cn.nihility.rbac.identity.upstream.service.UpstreamSyncRecordService;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * 上游数据管理接口，提供上游数据源的增删改查、启用停用、连接配置/调度配置维护、
 * 数据域配置维护、字段映射维护、同步执行记录查询、手动触发同步能力（proposal.md）。
 * 本能力面向管理端，不对外暴露 OpenAPI（design.md Non-Goals）。
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "上游数据管理", description = "上游数据源配置管理、数据域/字段映射维护、同步执行记录查询、手动触发同步接口")
public class UpstreamSourceController {

    /** 上游数据源业务逻辑接口。 */
    private final UpstreamSourceService upstreamSourceService;

    /** 上游数据源数据域配置业务逻辑接口。 */
    private final UpstreamDomainConfigService upstreamDomainConfigService;

    /** 上游字段映射业务逻辑接口。 */
    private final UpstreamFieldMappingService upstreamFieldMappingService;

    /** 上游数据同步执行记录业务逻辑接口。 */
    private final UpstreamSyncRecordService upstreamSyncRecordService;

    /**
     * 查询全部上游数据源。
     *
     * @return 上游数据源列表
     */
    @Operation(summary = "查询上游数据源列表")
    @GetMapping("/api/identity/upstream-sources")
    public List<UpstreamSourceVO> list() {
        return upstreamSourceService.list();
    }

    /**
     * 查询上游数据源详情。
     *
     * @param id 上游数据源 id
     * @return 上游数据源详情
     */
    @Operation(summary = "查询上游数据源详情")
    @GetMapping("/api/identity/upstream-sources/{id}")
    public UpstreamSourceVO detail(@PathVariable Long id) {
        return upstreamSourceService.getById(id);
    }

    /**
     * 创建上游数据源，{@code enabled} 默认固定为 {@code false}。
     *
     * @param request 创建请求
     * @return 创建后的上游数据源详情
     */
    @Operation(summary = "创建上游数据源", description = "enabled 默认固定为 false，需管理员确认配置无误后手工启用")
    @PostMapping("/api/identity/upstream-sources")
    public UpstreamSourceVO create(@Valid @RequestBody UpstreamSourceCreateRequest request) {
        return upstreamSourceService.create(request);
    }

    /**
     * 更新上游数据源基础信息（名称、同步方式）。
     *
     * @param id      上游数据源 id
     * @param request 更新请求
     * @return 更新后的上游数据源详情
     */
    @Operation(summary = "更新上游数据源基础信息", description = "更新名称、同步方式，连接配置/调度配置请使用各自专用接口")
    @PutMapping("/api/identity/upstream-sources/{id}")
    public UpstreamSourceVO updateBasicInfo(@PathVariable Long id,
            @Valid @RequestBody UpstreamSourceUpdateRequest request) {
        return upstreamSourceService.updateBasicInfo(id, request);
    }

    /**
     * 启用上游数据源。
     *
     * @param id 上游数据源 id
     * @return 更新后的上游数据源详情
     */
    @Operation(summary = "启用上游数据源")
    @PutMapping("/api/identity/upstream-sources/{id}/enable")
    public UpstreamSourceVO enable(@PathVariable Long id) {
        return upstreamSourceService.enable(id);
    }

    /**
     * 停用上游数据源。
     *
     * @param id 上游数据源 id
     * @return 更新后的上游数据源详情
     */
    @Operation(summary = "停用上游数据源")
    @PutMapping("/api/identity/upstream-sources/{id}/disable")
    public UpstreamSourceVO disable(@PathVariable Long id) {
        return upstreamSourceService.disable(id);
    }

    /**
     * 删除上游数据源，级联删除其下的数据域配置、字段映射配置与同步执行记录。
     *
     * @param id 上游数据源 id
     * @return 无业务数据的成功响应
     */
    @Operation(summary = "删除上游数据源", description = "级联删除数据域配置、字段映射配置与同步执行记录")
    @DeleteMapping("/api/identity/upstream-sources/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        upstreamSourceService.delete(id);
        return Result.success();
    }

    /**
     * 更新上游数据源连接配置。
     *
     * @param id      上游数据源 id
     * @param request 连接配置更新请求
     * @return 更新后的上游数据源详情
     */
    @Operation(summary = "更新上游数据源连接配置", description = "按数据源当前同步方式二选一生效："
            + "API 模式更新自定义请求头（整体替换），DB_TABLE 模式更新 JDBC 连接信息（密码留空表示不修改）")
    @PutMapping("/api/identity/upstream-sources/{id}/connection")
    public UpstreamSourceVO updateConnectionConfig(@PathVariable Long id,
            @Valid @RequestBody UpstreamConnectionConfigRequest request) {
        return upstreamSourceService.updateConnectionConfig(id, request);
    }

    /**
     * 更新上游数据源调度配置。
     *
     * @param id      上游数据源 id
     * @param request 调度配置更新请求
     * @return 更新后的上游数据源详情
     */
    @Operation(summary = "更新上游数据源调度配置", description = "按间隔或按每日固定时间点二选一")
    @PutMapping("/api/identity/upstream-sources/{id}/schedule")
    public UpstreamSourceVO updateScheduleConfig(@PathVariable Long id,
            @Valid @RequestBody UpstreamScheduleConfigRequest request) {
        return upstreamSourceService.updateScheduleConfig(id, request);
    }

    /**
     * 手动触发一次同步。
     *
     * @param id 上游数据源 id
     * @return 无业务数据的成功响应
     */
    @Operation(summary = "立即同步一次", description = "效果与定时到期自动触发一致，但不影响下次定时触发的判定基准")
    @PostMapping("/api/identity/upstream-sources/{id}/sync")
    public Result<Void> manualSync(@PathVariable Long id) {
        upstreamSourceService.manualSync(id);
        return Result.success();
    }

    /**
     * 查询上游数据源的 3 个数据域配置。
     *
     * @param id 上游数据源 id
     * @return 数据域配置列表
     */
    @Operation(summary = "查询数据域配置", description = "返回组织/用户/任职 3 行数据域配置")
    @GetMapping("/api/identity/upstream-sources/{id}/domains")
    public List<UpstreamDomainConfigVO> listDomainConfigs(@PathVariable Long id) {
        return upstreamDomainConfigService.listBySource(id);
    }

    /**
     * 修改上游数据源某个数据域的启用开关与取数来源配置。
     *
     * @param id       上游数据源 id
     * @param dataType 数据域：ORG/USER/POSITION
     * @param request  数据域配置修改请求
     * @return 修改后的数据域配置
     */
    @Operation(summary = "修改数据域配置", description = "修改指定数据域的启用开关与取数来源（接口地址/方式或数据库查询 SQL）")
    @PutMapping("/api/identity/upstream-sources/{id}/domains/{dataType}")
    public UpstreamDomainConfigVO updateDomainConfig(@PathVariable Long id,
            @Parameter(description = "数据域：ORG/USER/POSITION") @PathVariable String dataType,
            @Valid @RequestBody UpstreamDomainConfigUpdateRequest request) {
        return upstreamDomainConfigService.update(id, dataType, request);
    }

    /**
     * 查询上游数据源某个数据域的字段映射列表。
     *
     * @param id       上游数据源 id
     * @param dataType 数据域：ORG/USER/POSITION
     * @return 字段映射列表
     */
    @Operation(summary = "查询字段映射", description = "查询组织/用户/任职数据域的上游字段映射列表")
    @GetMapping("/api/identity/upstream-sources/{id}/domains/{dataType}/field-mappings")
    public List<UpstreamFieldMappingVO> listFieldMappings(@PathVariable Long id,
            @Parameter(description = "数据域：ORG/USER/POSITION") @PathVariable String dataType) {
        return upstreamFieldMappingService.list(id, dataType);
    }

    /**
     * 整体替换上游数据源某个数据域的字段映射列表。
     *
     * @param id       上游数据源 id
     * @param dataType 数据域：ORG/USER/POSITION
     * @param requests 本次提交的完整字段映射行列表
     * @return 保存后的字段映射列表
     */
    @Operation(summary = "保存字段映射", description = "整体替换语义：提交当前数据域的完整字段映射行列表，先删后插；"
            + "转换方式为转换脚本时保存前做 JavaScript 语法校验（不执行）")
    @PutMapping("/api/identity/upstream-sources/{id}/domains/{dataType}/field-mappings")
    public List<UpstreamFieldMappingVO> replaceFieldMappings(@PathVariable Long id,
            @Parameter(description = "数据域：ORG/USER/POSITION") @PathVariable String dataType,
            @Valid @RequestBody List<UpstreamFieldMappingSaveRequest> requests) {
        return upstreamFieldMappingService.replace(id, dataType, requests);
    }

    /**
     * 查询上游数据源的同步执行记录列表，按时间倒序返回。
     *
     * @param id 上游数据源 id
     * @return 同步执行记录列表
     */
    @Operation(summary = "查询同步执行记录", description = "按时间倒序返回该数据源全部数据域的同步执行记录")
    @GetMapping("/api/identity/upstream-sources/{id}/sync-records")
    public List<UpstreamSyncRecordVO> listSyncRecords(@PathVariable Long id) {
        return upstreamSyncRecordService.listBySource(id);
    }
}
