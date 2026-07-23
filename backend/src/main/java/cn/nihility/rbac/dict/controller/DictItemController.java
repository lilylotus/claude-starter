package cn.nihility.rbac.dict.controller;

import cn.nihility.rbac.common.result.PageResult;
import cn.nihility.rbac.common.result.Result;
import cn.nihility.rbac.dict.dto.DictItemCreateRequest;
import cn.nihility.rbac.dict.dto.DictItemOptionVO;
import cn.nihility.rbac.dict.dto.DictItemUpdateRequest;
import cn.nihility.rbac.dict.dto.DictItemVO;
import cn.nihility.rbac.dict.service.DictItemService;
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
 * 字典项管理接口，提供分页查询、维护、启停用、逻辑删除能力，以及供其他业务模块调用的
 * 按字典类型编码查询启用项的只读接口。
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "字典项管理", description = "字典项维护接口")
public class DictItemController {

    /** 字典项业务逻辑接口。 */
    private final DictItemService dictItemService;

    /**
     * 按所属字典类型分页查询字典项。
     *
     * @param dictTypeId 所属字典类型 id
     * @param page       页码，默认第 1 页
     * @param pageSize   每页条数，默认 10 条
     * @return 字典项的分页结果
     */
    @Operation(summary = "分页查询字典项", description = "按所属字典类型 id 分页查询，排除已逻辑删除的字典项")
    @GetMapping("/api/dict-items")
    public PageResult<DictItemVO> page(
            @Parameter(description = "所属字典类型 id")
            @RequestParam Long dictTypeId,
            @Parameter(description = "页码，默认第 1 页")
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @Parameter(description = "每页条数，默认 10 条")
            @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        return dictItemService.getPage(dictTypeId, page, pageSize);
    }

    /**
     * 查询字典项详情。
     *
     * @param id 字典项 id
     * @return 字典项详情
     */
    @Operation(summary = "查询字典项详情")
    @GetMapping("/api/dict-items/{id}")
    public DictItemVO detail(@PathVariable Long id) {
        return dictItemService.getById(id);
    }

    /**
     * 创建字典项。
     *
     * @param request 创建请求
     * @return 创建后的字典项详情
     */
    @Operation(summary = "创建字典项")
    @PostMapping("/api/dict-items")
    public DictItemVO create(@Valid @RequestBody DictItemCreateRequest request) {
        return dictItemService.create(request);
    }

    /**
     * 更新字典项基础信息，不支持修改所属字典类型，状态字段不通过本接口修改。
     *
     * @param id      字典项 id
     * @param request 更新请求
     * @return 更新后的字典项详情
     */
    @Operation(summary = "更新字典项", description = "更新字典项基础信息，不支持修改所属字典类型，状态请使用启用/停用接口")
    @PutMapping("/api/dict-items/{id}")
    public DictItemVO update(@PathVariable Long id, @Valid @RequestBody DictItemUpdateRequest request) {
        return dictItemService.update(id, request);
    }

    /**
     * 启用字典项。
     *
     * @param id 字典项 id
     * @return 更新后的字典项详情
     */
    @Operation(summary = "启用字典项")
    @PutMapping("/api/dict-items/{id}/enable")
    public DictItemVO enable(@PathVariable Long id) {
        return dictItemService.enable(id);
    }

    /**
     * 停用字典项。
     *
     * @param id 字典项 id
     * @return 更新后的字典项详情
     */
    @Operation(summary = "停用字典项")
    @PutMapping("/api/dict-items/{id}/disable")
    public DictItemVO disable(@PathVariable Long id) {
        return dictItemService.disable(id);
    }

    /**
     * 逻辑删除字典项。
     *
     * @param id 字典项 id
     * @return 无业务数据的成功响应
     */
    @Operation(summary = "删除字典项", description = "逻辑删除")
    @DeleteMapping("/api/dict-items/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        dictItemService.delete(id);
        return Result.success();
    }

    /**
     * 按字典类型编码查询其下全部启用状态的字典项精简列表，供业务模块下拉框场景调用，
     * 不需要感知分页、不需要管理权限。
     *
     * @param typeCode 字典类型编码
     * @return 启用状态的字典项精简列表；{@code typeCode} 不存在、已删除或已停用时返回空列表
     */
    @Operation(summary = "按字典类型编码查询启用项", description = "供业务模块下拉框场景调用；typeCode 不存在、"
            + "对应字典类型已删除或已停用时返回空列表而非报错")
    @GetMapping("/api/dicts/items")
    public List<DictItemOptionVO> options(
            @Parameter(description = "字典类型编码")
            @RequestParam String typeCode) {
        return dictItemService.getEnabledOptions(typeCode);
    }
}
