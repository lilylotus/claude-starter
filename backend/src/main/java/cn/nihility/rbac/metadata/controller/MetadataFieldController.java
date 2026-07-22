package cn.nihility.rbac.metadata.controller;

import cn.nihility.rbac.common.PageResult;
import cn.nihility.rbac.metadata.dto.MetadataFieldUpdateRequest;
import cn.nihility.rbac.metadata.dto.MetadataFieldVO;
import cn.nihility.rbac.metadata.service.MetadataFieldService;
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
 * 元数据字段配置接口，记录组织/用户/任职/应用四类业务对象"可开放配置"的表字段
 * 目录。目录只能通过数据库迁移预置，本接口不提供新增/删除能力，仅支持查询、
 * 编辑（字段名称、字段标识）、启用/停用。
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "元数据字段配置", description = "组织/用户/任职/应用四类业务对象可开放配置的表字段目录维护接口")
public class MetadataFieldController {

    /** 元数据字段配置业务逻辑接口。 */
    private final MetadataFieldService metadataFieldService;

    /**
     * 按业务对象类型分页查询元数据字段。
     *
     * @param bizType  业务对象类型，可选
     * @param page     页码，默认第 1 页
     * @param pageSize 每页条数，默认 10 条
     * @return 元数据字段的分页结果
     */
    @Operation(summary = "分页查询元数据字段", description = "支持按业务对象类型（ORG/USER/POSITION/APP）过滤，不传则查询全部")
    @GetMapping("/api/metadata-fields")
    public PageResult<MetadataFieldVO> page(
            @Parameter(description = "业务对象类型：ORG/USER/POSITION/APP")
            @RequestParam(required = false) String bizType,
            @Parameter(description = "页码，默认第 1 页")
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @Parameter(description = "每页条数，默认 10 条")
            @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        return metadataFieldService.getPage(bizType, page, pageSize);
    }

    /**
     * 查询指定业务对象类型下"可用"的元数据字段：状态为启用、且未被任何有效表单字段
     * 定义绑定，供"表单管理"新增/编辑字段定义时选择。
     *
     * @param bizType             业务对象类型，必填
     * @param excludeDefinitionId 编辑场景下当前正在编辑的表单字段定义 id，可选；传入时
     *                            额外把该定义当前绑定的元数据字段（若启用）一并纳入
     *                            返回列表，供"改绑"下拉框展示当前绑定项
     * @return 可用元数据字段列表
     */
    @Operation(summary = "查询可用元数据字段", description = "返回指定业务对象类型下状态为启用、且未被任何有效表单字段定义绑定的元数据字段列表；"
            + "传入 excludeDefinitionId 时，额外把该表单字段定义当前绑定的元数据字段（若启用）一并纳入返回列表，"
            + "供编辑场景下的改绑下拉框使用，excludeDefinitionId 不存在或对应定义已删除时按普通可用查询处理")
    @GetMapping("/api/metadata-fields/available")
    public List<MetadataFieldVO> available(
            @Parameter(description = "业务对象类型：ORG/USER/POSITION/APP", required = true)
            @RequestParam String bizType,
            @Parameter(description = "编辑场景下当前表单字段定义 id，传入时把其当前绑定的元数据字段一并纳入结果")
            @RequestParam(required = false) Long excludeDefinitionId) {
        return metadataFieldService.listAvailable(bizType, excludeDefinitionId);
    }

    /**
     * 查询元数据字段详情。
     *
     * @param id 元数据字段 id
     * @return 元数据字段详情
     */
    @Operation(summary = "查询元数据字段详情")
    @GetMapping("/api/metadata-fields/{id}")
    public MetadataFieldVO detail(@PathVariable Long id) {
        return metadataFieldService.getById(id);
    }

    /**
     * 更新元数据字段，字段名称、字段标识均可通过本接口修改。
     *
     * @param id      元数据字段 id
     * @param request 更新请求
     * @return 更新后的元数据字段详情
     */
    @Operation(summary = "更新元数据字段", description = "字段名称、字段标识均可通过本接口修改（字段标识需在同一业务对象类型下保持唯一），"
            + "表名称/字段列名/字段类型/业务对象类型创建后不可变")
    @PutMapping("/api/metadata-fields/{id}")
    public MetadataFieldVO update(@PathVariable Long id, @Valid @RequestBody MetadataFieldUpdateRequest request) {
        return metadataFieldService.update(id, request);
    }

    /**
     * 启用元数据字段。
     *
     * @param id 元数据字段 id
     * @return 更新后的元数据字段详情
     */
    @Operation(summary = "启用元数据字段")
    @PutMapping("/api/metadata-fields/{id}/enable")
    public MetadataFieldVO enable(@PathVariable Long id) {
        return metadataFieldService.enable(id);
    }

    /**
     * 停用元数据字段，若该字段当前被有效表单字段定义绑定则拒绝停用。
     *
     * @param id 元数据字段 id
     * @return 更新后的元数据字段详情
     */
    @Operation(summary = "停用元数据字段", description = "若该字段当前被至少一条有效表单字段定义绑定，拒绝停用")
    @PutMapping("/api/metadata-fields/{id}/disable")
    public MetadataFieldVO disable(@PathVariable Long id) {
        return metadataFieldService.disable(id);
    }
}
