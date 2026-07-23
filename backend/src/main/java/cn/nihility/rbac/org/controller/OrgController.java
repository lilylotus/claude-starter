package cn.nihility.rbac.org.controller;

import cn.nihility.rbac.common.result.PageResult;
import cn.nihility.rbac.common.result.Result;
import cn.nihility.rbac.org.dto.OrgCreateRequest;
import cn.nihility.rbac.org.dto.OrgTreeNodeVO;
import cn.nihility.rbac.org.dto.OrgUpdateRequest;
import cn.nihility.rbac.org.dto.OrgVO;
import cn.nihility.rbac.org.service.OrgService;
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
 * 组织管理接口，提供组织树查询、维护、启停用和逻辑删除能力。
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "组织管理", description = "组织机构维护接口")
public class OrgController {

    /** 组织业务逻辑接口。 */
    private final OrgService orgService;

    /**
     * 查询完整的组织树，排除已逻辑删除的组织。
     *
     * @return 组织树根节点列表
     */
    @Operation(summary = "查询组织树", description = "返回完整的组织树形结构，排除已逻辑删除的组织")
    @GetMapping("/api/orgs/tree")
    public List<OrgTreeNodeVO> tree() {
        return orgService.getTree();
    }

    /**
     * 分页查询某个组织的直属子组织，供右侧表格使用。
     *
     * @param parentId 上级组织 id，不传时默认为 0（顶级）
     * @param page     页码，默认第 1 页
     * @param pageSize 每页条数，默认 10 条
     * @return 直属子组织的分页结果
     */
    @Operation(summary = "分页查询直属子组织", description = "仅返回指定上级组织的直属子组织（不包含孙子级）的分页结果，供右侧表格使用")
    @GetMapping("/api/orgs/children")
    public PageResult<OrgVO> children(
            @Parameter(description = "上级组织 id，默认为 0（顶级）")
            @RequestParam(required = false, defaultValue = "0") Long parentId,
            @Parameter(description = "页码，默认第 1 页")
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @Parameter(description = "每页条数，默认 10 条")
            @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        return orgService.getChildren(parentId, page, pageSize);
    }

    /**
     * 不分页查询某个组织的全部直属子组织，专供左侧组织树逐层懒加载展开使用；与
     * {@link #tree()}（一次性返回完整嵌套树，供弹窗"上级组织"选择器使用）和
     * {@link #children(Long, Integer, Integer)}（分页返回，供右侧表格使用）用途不同，
     * 不要混用。
     *
     * @param parentId 上级组织 id，不传时默认为 0（顶级）
     * @return 直属子组织树节点列表，每个节点的 children 固定为空数组
     */
    @Operation(summary = "查询组织树懒加载子节点", description = "不分页返回指定上级组织的全部直属子组织，专供左侧组织树逐层懒加载展开使用，"
            + "区别于分页版 /api/orgs/children（供表格使用）和全量树 /api/orgs/tree（供弹窗上级组织选择器使用）")
    @GetMapping("/api/orgs/tree/children")
    public List<OrgTreeNodeVO> treeChildren(
            @Parameter(description = "上级组织 id，默认为 0（顶级）")
            @RequestParam(required = false, defaultValue = "0") Long parentId) {
        return orgService.getChildrenTreeNodes(parentId);
    }

    /**
     * 查询组织详情。
     *
     * @param id 组织 id
     * @return 组织详情
     */
    @Operation(summary = "查询组织详情")
    @GetMapping("/api/orgs/{id}")
    public OrgVO detail(@PathVariable Long id) {
        return orgService.getById(id);
    }

    /**
     * 创建组织。
     *
     * @param request 创建请求
     * @return 创建后的组织详情
     */
    @Operation(summary = "创建组织")
    @PostMapping("/api/orgs")
    public OrgVO create(@Valid @RequestBody OrgCreateRequest request) {
        return orgService.create(request);
    }

    /**
     * 更新组织基础信息，状态字段不通过本接口修改。
     *
     * @param id      组织 id
     * @param request 更新请求
     * @return 更新后的组织详情
     */
    @Operation(summary = "更新组织", description = "更新组织基础信息，状态请使用启用/停用接口")
    @PutMapping("/api/orgs/{id}")
    public OrgVO update(@PathVariable Long id, @Valid @RequestBody OrgUpdateRequest request) {
        return orgService.update(id, request);
    }

    /**
     * 启用组织。
     *
     * @param id 组织 id
     * @return 更新后的组织详情
     */
    @Operation(summary = "启用组织")
    @PutMapping("/api/orgs/{id}/enable")
    public OrgVO enable(@PathVariable Long id) {
        return orgService.enable(id);
    }

    /**
     * 停用组织。
     *
     * @param id 组织 id
     * @return 更新后的组织详情
     */
    @Operation(summary = "停用组织")
    @PutMapping("/api/orgs/{id}/disable")
    public OrgVO disable(@PathVariable Long id) {
        return orgService.disable(id);
    }

    /**
     * 逻辑删除组织，删除前会校验是否存在未删除的下级组织。
     *
     * @param id 组织 id
     * @return 无业务数据的成功响应
     */
    @Operation(summary = "删除组织", description = "逻辑删除，若存在未删除的下级组织则拒绝删除")
    @DeleteMapping("/api/orgs/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        orgService.delete(id);
        return Result.success();
    }
}
