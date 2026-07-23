package cn.nihility.rbac.menu.service;

import cn.nihility.rbac.common.result.PageResult;
import cn.nihility.rbac.menu.dto.MenuCreateRequest;
import cn.nihility.rbac.menu.dto.MenuTreeNodeVO;
import cn.nihility.rbac.menu.dto.MenuUpdateRequest;
import cn.nihility.rbac.menu.dto.MenuVO;
import java.util.List;

/**
 * 资源（菜单/按钮/API）业务逻辑接口。
 */
public interface MenuService {

    /**
     * 查询完整的资源树（排除已逻辑删除的资源）。
     *
     * @return 资源树根节点列表
     */
    List<MenuTreeNodeVO> getTree();

    /**
     * 分页查询某个资源的直属子资源（排除已逻辑删除的资源）。
     *
     * @param parentId 上级资源 id，为空时视为 0（顶级）
     * @param page     页码，从 1 开始
     * @param pageSize 每页条数
     * @return 直属子资源的分页结果
     */
    PageResult<MenuVO> getChildren(Long parentId, Integer page, Integer pageSize);

    /**
     * 不分页查询某个资源的全部直属子资源，专供前端资源树懒加载展开使用（排除已逻辑删除的资源）。
     * 仅返回下一层级，不包含更深层级的子孙资源，返回节点的 {@code children} 固定为空列表。
     *
     * @param parentId 上级资源 id，为空时视为 0（顶级）
     * @return 直属子资源树节点列表
     */
    List<MenuTreeNodeVO> getChildrenTreeNodes(Long parentId);

    /**
     * 查询资源详情。
     *
     * @param id 资源 id
     * @return 资源详情
     */
    MenuVO getById(Long id);

    /**
     * 创建资源。
     *
     * @param request 创建请求
     * @return 创建后的资源详情
     */
    MenuVO create(MenuCreateRequest request);

    /**
     * 更新资源基础信息。
     *
     * @param id      资源 id
     * @param request 更新请求
     * @return 更新后的资源详情
     */
    MenuVO update(Long id, MenuUpdateRequest request);

    /**
     * 启用资源。
     *
     * @param id 资源 id
     * @return 更新后的资源详情
     */
    MenuVO enable(Long id);

    /**
     * 停用资源。
     *
     * @param id 资源 id
     * @return 更新后的资源详情
     */
    MenuVO disable(Long id);

    /**
     * 逻辑删除资源，删除前需确保没有未删除的下级资源。
     *
     * @param id 资源 id
     */
    void delete(Long id);
}
