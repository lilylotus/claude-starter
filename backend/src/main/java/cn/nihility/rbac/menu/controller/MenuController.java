package cn.nihility.rbac.menu.controller;

import cn.nihility.rbac.common.result.PageResult;
import cn.nihility.rbac.common.result.Result;
import cn.nihility.rbac.menu.dto.MenuCreateRequest;
import cn.nihility.rbac.menu.dto.MenuTreeNodeVO;
import cn.nihility.rbac.menu.dto.MenuUpdateRequest;
import cn.nihility.rbac.menu.dto.MenuVO;
import cn.nihility.rbac.menu.service.MenuService;
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
 * 菜单管理接口，提供资源（菜单/按钮/API）树查询、维护、启停用和逻辑删除能力。
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "菜单管理", description = "菜单/按钮/API 资源维护接口")
public class MenuController {

    /** 资源业务逻辑接口。 */
    private final MenuService menuService;

    /**
     * 查询完整的资源树，排除已逻辑删除的资源。
     *
     * @return 资源树根节点列表
     */
    @Operation(summary = "查询资源树", description = "返回完整的资源树形结构，排除已逻辑删除的资源")
    @GetMapping("/api/menus/tree")
    public List<MenuTreeNodeVO> tree() {
        return menuService.getTree();
    }

    /**
     * 分页查询某个资源的直属子资源，供右侧表格使用。
     *
     * @param parentId 上级资源 id，不传时默认为 0（顶级）
     * @param page     页码，默认第 1 页
     * @param pageSize 每页条数，默认 10 条
     * @return 直属子资源的分页结果
     */
    @Operation(summary = "分页查询直属子资源", description = "仅返回指定上级资源的直属子资源（不包含孙子级）的分页结果，供右侧表格使用")
    @GetMapping("/api/menus/children")
    public PageResult<MenuVO> children(
            @Parameter(description = "上级资源 id，默认为 0（顶级）")
            @RequestParam(required = false, defaultValue = "0") Long parentId,
            @Parameter(description = "页码，默认第 1 页")
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @Parameter(description = "每页条数，默认 10 条")
            @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        return menuService.getChildren(parentId, page, pageSize);
    }

    /**
     * 不分页查询某个资源的全部直属子资源，专供左侧资源树逐层懒加载展开使用；与
     * {@link #tree()}（一次性返回完整嵌套树，供弹窗"上级资源"选择器使用）和
     * {@link #children(Long, Integer, Integer)}（分页返回，供右侧表格使用）用途不同，
     * 不要混用。
     *
     * @param parentId 上级资源 id，不传时默认为 0（顶级）
     * @return 直属子资源树节点列表，每个节点的 children 固定为空数组
     */
    @Operation(summary = "查询资源树懒加载子节点", description = "不分页返回指定上级资源的全部直属子资源，专供左侧资源树逐层懒加载展开使用，"
            + "区别于分页版 /api/menus/children（供表格使用）和全量树 /api/menus/tree（供弹窗上级资源选择器使用）")
    @GetMapping("/api/menus/tree/children")
    public List<MenuTreeNodeVO> treeChildren(
            @Parameter(description = "上级资源 id，默认为 0（顶级）")
            @RequestParam(required = false, defaultValue = "0") Long parentId) {
        return menuService.getChildrenTreeNodes(parentId);
    }

    /**
     * 查询资源详情。
     *
     * @param id 资源 id
     * @return 资源详情
     */
    @Operation(summary = "查询资源详情")
    @GetMapping("/api/menus/{id}")
    public MenuVO detail(@PathVariable Long id) {
        return menuService.getById(id);
    }

    /**
     * 创建资源。
     *
     * @param request 创建请求
     * @return 创建后的资源详情
     */
    @Operation(summary = "创建资源")
    @PostMapping("/api/menus")
    public MenuVO create(@Valid @RequestBody MenuCreateRequest request) {
        return menuService.create(request);
    }

    /**
     * 更新资源基础信息，状态字段不通过本接口修改。
     *
     * @param id      资源 id
     * @param request 更新请求
     * @return 更新后的资源详情
     */
    @Operation(summary = "更新资源", description = "更新资源基础信息，状态请使用启用/停用接口")
    @PutMapping("/api/menus/{id}")
    public MenuVO update(@PathVariable Long id, @Valid @RequestBody MenuUpdateRequest request) {
        return menuService.update(id, request);
    }

    /**
     * 启用资源。
     *
     * @param id 资源 id
     * @return 更新后的资源详情
     */
    @Operation(summary = "启用资源")
    @PutMapping("/api/menus/{id}/enable")
    public MenuVO enable(@PathVariable Long id) {
        return menuService.enable(id);
    }

    /**
     * 停用资源。
     *
     * @param id 资源 id
     * @return 更新后的资源详情
     */
    @Operation(summary = "停用资源")
    @PutMapping("/api/menus/{id}/disable")
    public MenuVO disable(@PathVariable Long id) {
        return menuService.disable(id);
    }

    /**
     * 逻辑删除资源，删除前会校验是否存在未删除的下级资源。
     *
     * @param id 资源 id
     * @return 无业务数据的成功响应
     */
    @Operation(summary = "删除资源", description = "逻辑删除，若存在未删除的下级资源则拒绝删除")
    @DeleteMapping("/api/menus/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        menuService.delete(id);
        return Result.success();
    }
}
