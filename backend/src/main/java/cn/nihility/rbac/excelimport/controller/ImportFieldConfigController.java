package cn.nihility.rbac.excelimport.controller;

import cn.nihility.rbac.common.result.PageResult;
import cn.nihility.rbac.common.result.Result;
import cn.nihility.rbac.excelimport.dto.ImportFieldConfigCreateRequest;
import cn.nihility.rbac.excelimport.dto.ImportFieldConfigUpdateRequest;
import cn.nihility.rbac.excelimport.dto.ImportFieldConfigVO;
import cn.nihility.rbac.excelimport.service.ImportFieldConfigService;
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
 * Excel 导入字段配置接口，按业务对象类型（ORG/USER/POSITION/APP）维护一组导入列
 * 配置，驱动导入模板生成与批量导入的表头匹配、主键匹配、必填校验。
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Excel 导入字段配置", description = "组织/用户/任职/应用四类业务对象的 Excel 导入字段配置维护接口")
public class ImportFieldConfigController {

    /** Excel 导入字段配置业务逻辑接口。 */
    private final ImportFieldConfigService importFieldConfigService;

    /**
     * 按业务对象类型分页查询导入字段配置。
     *
     * @param bizType  业务对象类型，可选
     * @param page     页码，默认第 1 页
     * @param pageSize 每页条数，默认 10 条
     * @return 导入字段配置的分页结果
     */
    @Operation(summary = "分页查询导入字段配置", description = "支持按业务对象类型（ORG/USER/POSITION/APP）过滤，按显示序号升序排列")
    @GetMapping("/api/import-field-configs")
    public PageResult<ImportFieldConfigVO> page(
            @Parameter(description = "业务对象类型：ORG/USER/POSITION/APP")
            @RequestParam(required = false) String bizType,
            @Parameter(description = "页码，默认第 1 页")
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @Parameter(description = "每页条数，默认 10 条")
            @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        return importFieldConfigService.getPage(bizType, page, pageSize);
    }

    /**
     * 查询导入字段配置详情。
     *
     * @param id 导入字段配置 id
     * @return 导入字段配置详情
     */
    @Operation(summary = "查询导入字段配置详情")
    @GetMapping("/api/import-field-configs/{id}")
    public ImportFieldConfigVO detail(@PathVariable Long id) {
        return importFieldConfigService.getById(id);
    }

    /**
     * 创建导入字段配置。
     *
     * @param request 创建请求
     * @return 创建后的导入字段配置详情
     */
    @Operation(summary = "创建导入字段配置", description = "formFieldDefinitionId 非空时须指向一个存在且启用、bizType 一致的表单字段定义；"
            + "为空时须直接提交 fieldCode。同 bizType 下 fieldCode 须唯一")
    @PostMapping("/api/import-field-configs")
    public ImportFieldConfigVO create(@Valid @RequestBody ImportFieldConfigCreateRequest request) {
        return importFieldConfigService.create(request);
    }

    /**
     * 更新导入字段配置，锁定（系统保护）配置拒绝改绑/取消主键或必填标记。
     *
     * @param id      导入字段配置 id
     * @param request 更新请求
     * @return 更新后的导入字段配置详情
     */
    @Operation(summary = "更新导入字段配置", description = "业务对象类型创建后不可修改；系统保护的导入字段配置（POSITION 的 __userCode/"
            + "__orgCode）拒绝改绑表单字段定义，也拒绝将 isPrimaryKey/isRequired 改为 false")
    @PutMapping("/api/import-field-configs/{id}")
    public ImportFieldConfigVO update(@PathVariable Long id,
            @Valid @RequestBody ImportFieldConfigUpdateRequest request) {
        return importFieldConfigService.update(id, request);
    }

    /**
     * 逻辑删除导入字段配置，系统保护的配置拒绝删除。
     *
     * @param id 导入字段配置 id
     * @return 无业务数据的成功响应
     */
    @Operation(summary = "删除导入字段配置", description = "逻辑删除，系统保护的导入字段配置（POSITION 的 __userCode/__orgCode）拒绝删除")
    @DeleteMapping("/api/import-field-configs/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        importFieldConfigService.delete(id);
        return Result.success();
    }
}
