package cn.nihility.rbac.dict.controller;

import cn.nihility.rbac.common.PageResult;
import cn.nihility.rbac.common.Result;
import cn.nihility.rbac.dict.dto.DictTypeCreateRequest;
import cn.nihility.rbac.dict.dto.DictTypeUpdateRequest;
import cn.nihility.rbac.dict.dto.DictTypeVO;
import cn.nihility.rbac.dict.service.DictTypeService;
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
 * 字典类型管理接口，提供分页查询、维护、启停用和逻辑删除能力。
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "字典类型管理", description = "字典类型维护接口")
public class DictTypeController {

    /** 字典类型业务逻辑接口。 */
    private final DictTypeService dictTypeService;

    /**
     * 分页查询字典类型，支持按名称或编码模糊搜索。
     *
     * @param keyword  模糊搜索关键字，可为空
     * @param page     页码，默认第 1 页
     * @param pageSize 每页条数，默认 10 条
     * @return 字典类型的分页结果
     */
    @Operation(summary = "分页查询字典类型", description = "支持按名称或编码模糊搜索，排除已逻辑删除的字典类型")
    @GetMapping("/api/dict-types")
    public PageResult<DictTypeVO> page(
            @Parameter(description = "按名称或编码模糊搜索的关键字")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "页码，默认第 1 页")
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @Parameter(description = "每页条数，默认 10 条")
            @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        return dictTypeService.getPage(keyword, page, pageSize);
    }

    /**
     * 查询字典类型详情。
     *
     * @param id 字典类型 id
     * @return 字典类型详情
     */
    @Operation(summary = "查询字典类型详情")
    @GetMapping("/api/dict-types/{id}")
    public DictTypeVO detail(@PathVariable Long id) {
        return dictTypeService.getById(id);
    }

    /**
     * 创建字典类型。
     *
     * @param request 创建请求
     * @return 创建后的字典类型详情
     */
    @Operation(summary = "创建字典类型")
    @PostMapping("/api/dict-types")
    public DictTypeVO create(@Valid @RequestBody DictTypeCreateRequest request) {
        return dictTypeService.create(request);
    }

    /**
     * 更新字典类型基础信息，状态字段不通过本接口修改。
     *
     * @param id      字典类型 id
     * @param request 更新请求
     * @return 更新后的字典类型详情
     */
    @Operation(summary = "更新字典类型", description = "更新字典类型基础信息，状态请使用启用/停用接口")
    @PutMapping("/api/dict-types/{id}")
    public DictTypeVO update(@PathVariable Long id, @Valid @RequestBody DictTypeUpdateRequest request) {
        return dictTypeService.update(id, request);
    }

    /**
     * 启用字典类型。
     *
     * @param id 字典类型 id
     * @return 更新后的字典类型详情
     */
    @Operation(summary = "启用字典类型")
    @PutMapping("/api/dict-types/{id}/enable")
    public DictTypeVO enable(@PathVariable Long id) {
        return dictTypeService.enable(id);
    }

    /**
     * 停用字典类型。
     *
     * @param id 字典类型 id
     * @return 更新后的字典类型详情
     */
    @Operation(summary = "停用字典类型")
    @PutMapping("/api/dict-types/{id}/disable")
    public DictTypeVO disable(@PathVariable Long id) {
        return dictTypeService.disable(id);
    }

    /**
     * 逻辑删除字典类型，删除前会校验是否存在未删除的字典项。
     *
     * @param id 字典类型 id
     * @return 无业务数据的成功响应
     */
    @Operation(summary = "删除字典类型", description = "逻辑删除，若存在未删除的字典项则拒绝删除")
    @DeleteMapping("/api/dict-types/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        dictTypeService.delete(id);
        return Result.success();
    }
}
