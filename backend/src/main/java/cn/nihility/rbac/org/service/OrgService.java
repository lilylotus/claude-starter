package cn.nihility.rbac.org.service;

import cn.nihility.rbac.common.PageResult;
import cn.nihility.rbac.org.dto.OrgCreateRequest;
import cn.nihility.rbac.org.dto.OrgTreeNodeVO;
import cn.nihility.rbac.org.dto.OrgUpdateRequest;
import cn.nihility.rbac.org.dto.OrgVO;
import java.util.List;

/**
 * 组织机构业务逻辑接口。
 */
public interface OrgService {

    /**
     * 查询完整的组织树（排除已逻辑删除的组织）。
     *
     * @return 组织树根节点列表
     */
    List<OrgTreeNodeVO> getTree();

    /**
     * 分页查询某个组织的直属子组织（排除已逻辑删除的组织）。
     *
     * @param parentId 上级组织 id，为空时视为 0（顶级）
     * @param page     页码，从 1 开始
     * @param pageSize 每页条数
     * @return 直属子组织的分页结果
     */
    PageResult<OrgVO> getChildren(Long parentId, Integer page, Integer pageSize);

    /**
     * 不分页查询某个组织的全部直属子组织，专供前端组织树懒加载展开使用（排除已逻辑删除的组织）。
     * 仅返回下一层级，不包含更深层级的子孙组织，返回节点的 {@code children} 固定为空列表。
     *
     * @param parentId 上级组织 id，为空时视为 0（顶级）
     * @return 直属子组织树节点列表
     */
    List<OrgTreeNodeVO> getChildrenTreeNodes(Long parentId);

    /**
     * 查询组织详情。
     *
     * @param id 组织 id
     * @return 组织详情
     */
    OrgVO getById(Long id);

    /**
     * 创建组织。
     *
     * @param request 创建请求
     * @return 创建后的组织详情
     */
    OrgVO create(OrgCreateRequest request);

    /**
     * 更新组织基础信息。
     *
     * @param id      组织 id
     * @param request 更新请求
     * @return 更新后的组织详情
     */
    OrgVO update(Long id, OrgUpdateRequest request);

    /**
     * 启用组织。
     *
     * @param id 组织 id
     * @return 更新后的组织详情
     */
    OrgVO enable(Long id);

    /**
     * 停用组织。
     *
     * @param id 组织 id
     * @return 更新后的组织详情
     */
    OrgVO disable(Long id);

    /**
     * 逻辑删除组织，删除前需确保没有未删除的下级组织。
     *
     * @param id 组织 id
     */
    void delete(Long id);
}
