package cn.nihility.rbac.formfield.controller;

import cn.nihility.rbac.common.result.PageResult;
import cn.nihility.rbac.common.result.Result;
import cn.nihility.rbac.formfield.dto.FormFieldDefinitionCreateRequest;
import cn.nihility.rbac.formfield.dto.FormFieldDefinitionUpdateRequest;
import cn.nihility.rbac.formfield.dto.FormFieldDefinitionVO;
import cn.nihility.rbac.formfield.dto.FormFieldRenderItemVO;
import cn.nihility.rbac.formfield.service.FormFieldDefinitionService;
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
 * 表单字段定义接口，在元数据字段配置目录的基础上，绑定业务侧关心的展示/校验属性，
 * 驱动组织/用户/任职/应用四个管理页面的动态渲染。
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "表单字段定义", description = "组织/用户/任职/应用四类业务对象的表单字段定义维护接口")
public class FormFieldDefinitionController {

    /** 表单字段定义业务逻辑接口。 */
    private final FormFieldDefinitionService formFieldDefinitionService;

    /**
     * 按业务对象类型分页查询字段定义。
     *
     * @param bizType  业务对象类型，可选
     * @param page     页码，默认第 1 页
     * @param pageSize 每页条数，默认 10 条
     * @return 字段定义的分页结果
     */
    @Operation(summary = "分页查询表单字段定义", description = "支持按业务对象类型（ORG/USER/POSITION/APP）过滤，排除已逻辑删除的定义")
    @GetMapping("/api/form-fields")
    public PageResult<FormFieldDefinitionVO> page(
            @Parameter(description = "业务对象类型：ORG/USER/POSITION/APP")
            @RequestParam(required = false) String bizType,
            @Parameter(description = "页码，默认第 1 页")
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @Parameter(description = "每页条数，默认 10 条")
            @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        return formFieldDefinitionService.getPage(bizType, page, pageSize);
    }

    /**
     * 查询指定业务对象类型下全部启用状态的字段定义渲染元数据，供前端动态渲染
     * 列表列与新增/编辑表单。
     *
     * @param bizType 业务对象类型，必填
     * @return 渲染元数据列表，按显示序号降序排列
     */
    @Operation(summary = "查询动态字段渲染元数据", description = "返回指定业务对象类型下全部启用状态的字段定义，按显示序号降序排列；"
            + "控件类型为下拉单选字典或多选字典下拉的定义内嵌 dictOptions")
    @GetMapping("/api/form-fields/render-schema")
    public List<FormFieldRenderItemVO> renderSchema(
            @Parameter(description = "业务对象类型：ORG/USER/POSITION/APP", required = true)
            @RequestParam String bizType) {
        return formFieldDefinitionService.buildRenderSchema(bizType);
    }

    /**
     * 查询字段定义详情。
     *
     * @param id 字段定义 id
     * @return 字段定义详情
     */
    @Operation(summary = "查询表单字段定义详情")
    @GetMapping("/api/form-fields/{id}")
    public FormFieldDefinitionVO detail(@PathVariable Long id) {
        return formFieldDefinitionService.getById(id);
    }

    /**
     * 创建字段定义。
     *
     * @param request 创建请求
     * @return 创建后的字段定义详情
     */
    @Operation(summary = "创建表单字段定义", description = "metadataFieldId 必须指向一个当前状态为启用、且未被其他有效定义绑定的元数据字段")
    @PostMapping("/api/form-fields")
    public FormFieldDefinitionVO create(@Valid @RequestBody FormFieldDefinitionCreateRequest request) {
        return formFieldDefinitionService.create(request);
    }

    /**
     * 更新字段定义，绑定承重字段的定义不允许改绑；非锁定定义允许改绑到同一业务
     * 对象类型下另一个可用的元数据字段。
     *
     * @param id      字段定义 id
     * @param request 更新请求
     * @return 更新后的字段定义详情
     */
    @Operation(summary = "更新表单字段定义", description = "业务对象类型创建后不可修改；绑定承重字段（locked=true）的定义拒绝改绑"
            + "metadataFieldId，也拒绝将必填/新增表单展示/编辑表单展示配置为否；非锁定定义允许把 metadataFieldId 改绑到"
            + "同一业务对象类型下另一个状态启用且未被占用的元数据字段")
    @PutMapping("/api/form-fields/{id}")
    public FormFieldDefinitionVO update(@PathVariable Long id,
            @Valid @RequestBody FormFieldDefinitionUpdateRequest request) {
        return formFieldDefinitionService.update(id, request);
    }

    /**
     * 启用字段定义。
     *
     * @param id 字段定义 id
     * @return 更新后的字段定义详情
     */
    @Operation(summary = "启用表单字段定义")
    @PutMapping("/api/form-fields/{id}/enable")
    public FormFieldDefinitionVO enable(@PathVariable Long id) {
        return formFieldDefinitionService.enable(id);
    }

    /**
     * 停用字段定义，绑定承重字段的定义拒绝停用。
     *
     * @param id 字段定义 id
     * @return 更新后的字段定义详情
     */
    @Operation(summary = "停用表单字段定义", description = "绑定承重字段（name/code）的定义拒绝停用")
    @PutMapping("/api/form-fields/{id}/disable")
    public FormFieldDefinitionVO disable(@PathVariable Long id) {
        return formFieldDefinitionService.disable(id);
    }

    /**
     * 逻辑删除字段定义，绑定承重字段的定义拒绝删除。
     *
     * @param id 字段定义 id
     * @return 无业务数据的成功响应
     */
    @Operation(summary = "删除表单字段定义", description = "逻辑删除，绑定承重字段（name/code）的定义拒绝删除，其余释放绑定的元数据字段")
    @DeleteMapping("/api/form-fields/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        formFieldDefinitionService.delete(id);
        return Result.success();
    }
}
