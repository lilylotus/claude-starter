package cn.nihility.rbac.user.controller;

import cn.nihility.rbac.common.result.PageResult;
import cn.nihility.rbac.common.result.Result;
import cn.nihility.rbac.user.dto.PositionCreateRequest;
import cn.nihility.rbac.user.dto.PositionUpdateRequest;
import cn.nihility.rbac.user.dto.PositionVO;
import cn.nihility.rbac.user.service.PositionService;
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
 * 任职管理接口，以组织为导航维度提供任职记录的独立查询、维护、启停用、逻辑删除能力，
 * 复用用户管理模块既有的 {@code tab_user_position} 表/实体/Mapper。
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "任职管理", description = "以组织为导航维度的任职记录维护接口")
public class PositionController {

    /** 任职管理业务逻辑接口。 */
    private final PositionService positionService;

    /**
     * 按所属组织 id 分页查询任职记录，{@code orgId} 必填。
     *
     * @param orgId    所属组织 id，必填
     * @param page     页码，默认第 1 页
     * @param pageSize 每页条数，默认 10 条
     * @return 任职记录的分页结果
     */
    @Operation(summary = "分页查询任职记录", description = "按所属组织 id 分页查询未逻辑删除的任职记录，orgId 必填，不做默认聚合查询")
    @GetMapping("/api/positions")
    public PageResult<PositionVO> page(
            @Parameter(description = "所属组织 id，必填")
            @RequestParam(required = false) Long orgId,
            @Parameter(description = "页码，默认第 1 页")
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @Parameter(description = "每页条数，默认 10 条")
            @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        return positionService.getPage(orgId, page, pageSize);
    }

    /**
     * 查询任职记录详情。
     *
     * @param id 任职记录 id
     * @return 任职记录详情
     */
    @Operation(summary = "查询任职记录详情")
    @GetMapping("/api/positions/{id}")
    public PositionVO detail(@PathVariable Long id) {
        return positionService.getById(id);
    }

    /**
     * 创建任职记录，须指定一个已存在的用户。
     *
     * @param request 创建请求
     * @return 创建后的任职记录详情
     */
    @Operation(summary = "创建任职记录", description = "须指定一个已存在的用户，新建记录默认状态为启用")
    @PostMapping("/api/positions")
    public PositionVO create(@Valid @RequestBody PositionCreateRequest request) {
        return positionService.create(request);
    }

    /**
     * 更新任职记录，所属用户与状态字段不通过本接口修改。
     *
     * @param id      任职记录 id
     * @param request 更新请求
     * @return 更新后的任职记录详情
     */
    @Operation(summary = "更新任职记录", description = "更新所属组织/任职类型/任职地址/任职电话/显示序号/备注，所属用户及状态请勿通过本接口修改")
    @PutMapping("/api/positions/{id}")
    public PositionVO update(@PathVariable Long id, @Valid @RequestBody PositionUpdateRequest request) {
        return positionService.update(id, request);
    }

    /**
     * 启用任职记录。
     *
     * @param id 任职记录 id
     * @return 更新后的任职记录详情
     */
    @Operation(summary = "启用任职记录")
    @PutMapping("/api/positions/{id}/enable")
    public PositionVO enable(@PathVariable Long id) {
        return positionService.enable(id);
    }

    /**
     * 停用任职记录。
     *
     * @param id 任职记录 id
     * @return 更新后的任职记录详情
     */
    @Operation(summary = "停用任职记录")
    @PutMapping("/api/positions/{id}/disable")
    public PositionVO disable(@PathVariable Long id) {
        return positionService.disable(id);
    }

    /**
     * 逻辑删除任职记录，不做物理删除。
     *
     * @param id 任职记录 id
     * @return 无业务数据的成功响应
     */
    @Operation(summary = "删除任职记录", description = "逻辑删除，不做物理删除")
    @DeleteMapping("/api/positions/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        positionService.delete(id);
        return Result.success();
    }
}
