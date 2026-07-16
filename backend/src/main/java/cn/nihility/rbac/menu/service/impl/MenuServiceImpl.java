package cn.nihility.rbac.menu.service.impl;

import cn.nihility.rbac.common.PageResult;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.menu.constant.MenuResourceType;
import cn.nihility.rbac.menu.constant.MenuStatus;
import cn.nihility.rbac.menu.dto.MenuCreateRequest;
import cn.nihility.rbac.menu.dto.MenuTreeNodeVO;
import cn.nihility.rbac.menu.dto.MenuUpdateRequest;
import cn.nihility.rbac.menu.dto.MenuVO;
import cn.nihility.rbac.menu.entity.MenuEntity;
import cn.nihility.rbac.menu.mapper.MenuMapper;
import cn.nihility.rbac.menu.mapstruct.MenuConvert;
import cn.nihility.rbac.menu.service.MenuService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 资源（菜单/按钮/API）业务逻辑实现。
 */
@Service
@RequiredArgsConstructor
public class MenuServiceImpl implements MenuService {

    /** 顶级资源的上级 id。 */
    private static final long ROOT_PARENT_ID = 0L;

    /** 当前项目尚未接入登录鉴权，创建人/更新人暂时固定为该值。 */
    private static final String DEFAULT_OPERATOR = "admin";

    /** 资源数据访问接口。 */
    private final MenuMapper menuMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    public List<MenuTreeNodeVO> getTree() {
        List<MenuEntity> entities = listAllUndeletedOrdered();

        Map<Long, MenuTreeNodeVO> nodeMap = new LinkedHashMap<>();
        for (MenuEntity entity : entities) {
            nodeMap.put(entity.getId(), MenuConvert.INSTANCE.toTreeNode(entity));
        }

        List<MenuTreeNodeVO> roots = new ArrayList<>();
        for (MenuEntity entity : entities) {
            MenuTreeNodeVO node = nodeMap.get(entity.getId());
            MenuTreeNodeVO parentNode = nodeMap.get(entity.getParentId());
            if (parentNode != null) {
                parentNode.getChildren().add(node);
            } else {
                roots.add(node);
            }
        }
        return roots;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResult<MenuVO> getChildren(Long parentId, Integer page, Integer pageSize) {
        long effectiveParentId = parentId != null ? parentId : ROOT_PARENT_ID;
        Page<MenuEntity> queryPage = new Page<>(page, pageSize);
        Page<MenuEntity> resultPage = menuMapper.selectPage(queryPage, childrenQueryWrapper(effectiveParentId));
        List<MenuVO> records = toVOListWithParentName(resultPage.getRecords());
        return PageResult.of(records, resultPage);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<MenuTreeNodeVO> getChildrenTreeNodes(Long parentId) {
        long effectiveParentId = parentId != null ? parentId : ROOT_PARENT_ID;
        List<MenuEntity> entities = menuMapper.selectList(childrenQueryWrapper(effectiveParentId));

        List<MenuTreeNodeVO> nodes = new ArrayList<>();
        for (MenuEntity entity : entities) {
            MenuTreeNodeVO node = MenuConvert.INSTANCE.toTreeNode(entity);
            node.setChildren(new ArrayList<>());
            nodes.add(node);
        }
        return nodes;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MenuVO getById(Long id) {
        MenuEntity entity = getExistingEntity(id);
        return toVOListWithParentName(List.of(entity)).get(0);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MenuVO create(MenuCreateRequest request) {
        checkResourceTypeValid(request.getResourceType());
        checkCodeUnique(request.getCode(), null);

        MenuEntity entity = MenuConvert.INSTANCE.toEntity(request);
        LocalDateTime now = LocalDateTime.now();
        entity.setStatus(MenuStatus.ENABLED);
        entity.setCreateBy(DEFAULT_OPERATOR);
        entity.setCreateTime(now);
        entity.setUpdateBy(DEFAULT_OPERATOR);
        entity.setUpdateTime(now);
        menuMapper.insert(entity);

        return getById(entity.getId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MenuVO update(Long id, MenuUpdateRequest request) {
        checkResourceTypeValid(request.getResourceType());
        MenuEntity entity = getExistingEntity(id);
        checkCodeUnique(request.getCode(), id);

        MenuConvert.INSTANCE.updateEntity(request, entity);
        entity.setUpdateBy(DEFAULT_OPERATOR);
        entity.setUpdateTime(LocalDateTime.now());
        menuMapper.updateById(entity);

        return getById(id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MenuVO enable(Long id) {
        return changeStatus(id, MenuStatus.ENABLED);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MenuVO disable(Long id) {
        return changeStatus(id, MenuStatus.DISABLED);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void delete(Long id) {
        MenuEntity entity = getExistingEntity(id);

        Long childCount = menuMapper.selectCount(new LambdaQueryWrapper<MenuEntity>()
                .eq(MenuEntity::getParentId, id)
                .ne(MenuEntity::getStatus, MenuStatus.DELETED));
        if (childCount != null && childCount > 0) {
            throw new BusinessException("该资源下存在未删除的下级资源，无法删除");
        }

        entity.setStatus(MenuStatus.DELETED);
        entity.setUpdateBy(DEFAULT_OPERATOR);
        entity.setUpdateTime(LocalDateTime.now());
        menuMapper.updateById(entity);
    }

    /**
     * 变更资源状态（启用/停用）并返回更新后的详情。
     *
     * @param id     资源 id
     * @param status 目标状态
     * @return 更新后的资源详情
     */
    private MenuVO changeStatus(Long id, int status) {
        MenuEntity entity = getExistingEntity(id);
        entity.setStatus(status);
        entity.setUpdateBy(DEFAULT_OPERATOR);
        entity.setUpdateTime(LocalDateTime.now());
        menuMapper.updateById(entity);
        return getById(id);
    }

    /**
     * 查询全部未删除的资源，按显示序号降序、id 升序排列。
     *
     * @return 未删除的资源实体列表
     */
    private List<MenuEntity> listAllUndeletedOrdered() {
        return menuMapper.selectList(new LambdaQueryWrapper<MenuEntity>()
                .ne(MenuEntity::getStatus, MenuStatus.DELETED)
                .orderByDesc(MenuEntity::getShowOrder)
                .orderByAsc(MenuEntity::getId));
    }

    /**
     * 构造查询某个上级资源下未删除直属子资源的条件，按显示序号降序、id 升序排列。
     * 供分页查询（{@link #getChildren}）与树懒加载查询（{@link #getChildrenTreeNodes}）共用。
     *
     * @param parentId 上级资源 id
     * @return 查询条件
     */
    private LambdaQueryWrapper<MenuEntity> childrenQueryWrapper(long parentId) {
        return new LambdaQueryWrapper<MenuEntity>()
                .eq(MenuEntity::getParentId, parentId)
                .ne(MenuEntity::getStatus, MenuStatus.DELETED)
                .orderByDesc(MenuEntity::getShowOrder)
                .orderByAsc(MenuEntity::getId);
    }

    /**
     * 查询一个未被逻辑删除的资源，不存在时抛出业务异常。
     *
     * @param id 资源 id
     * @return 资源实体
     */
    private MenuEntity getExistingEntity(Long id) {
        MenuEntity entity = menuMapper.selectById(id);
        if (entity == null || Objects.equals(entity.getStatus(), MenuStatus.DELETED)) {
            throw new BusinessException("资源不存在");
        }
        return entity;
    }

    /**
     * 校验资源编码在未删除的资源中是否唯一。
     *
     * @param code      待校验的资源编码
     * @param excludeId 更新场景下需要排除的自身 id，创建场景传 null
     */
    private void checkCodeUnique(String code, Long excludeId) {
        LambdaQueryWrapper<MenuEntity> wrapper = new LambdaQueryWrapper<MenuEntity>()
                .eq(MenuEntity::getCode, code)
                .ne(MenuEntity::getStatus, MenuStatus.DELETED);
        if (excludeId != null) {
            wrapper.ne(MenuEntity::getId, excludeId);
        }
        Long count = menuMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException("资源编码[" + code + "]已存在");
        }
    }

    /**
     * 校验资源类型取值是否合法（菜单/按钮/API 三者之一）。
     *
     * @param resourceType 待校验的资源类型
     */
    private void checkResourceTypeValid(Integer resourceType) {
        if (!MenuResourceType.isValid(resourceType)) {
            throw new BusinessException("资源类型[" + resourceType + "]不合法");
        }
    }

    /**
     * 把资源实体列表转换为详情视图对象列表，并批量解析上级资源名称。
     *
     * @param entities 资源实体列表
     * @return 详情视图对象列表
     */
    private List<MenuVO> toVOListWithParentName(List<MenuEntity> entities) {
        Set<Long> parentIds = entities.stream()
                .map(MenuEntity::getParentId)
                .filter(parentId -> parentId != null && parentId != ROOT_PARENT_ID)
                .collect(Collectors.toSet());

        Map<Long, String> parentNameMap;
        if (parentIds.isEmpty()) {
            parentNameMap = Map.of();
        } else {
            List<MenuEntity> parents = menuMapper.selectList(new LambdaQueryWrapper<MenuEntity>()
                    .in(MenuEntity::getId, parentIds));
            parentNameMap = parents.stream()
                    .collect(Collectors.toMap(MenuEntity::getId, MenuEntity::getName, (left, right) -> left));
        }

        List<MenuVO> result = MenuConvert.INSTANCE.toVOList(entities);
        for (MenuVO vo : result) {
            vo.setParentName(parentNameMap.get(vo.getParentId()));
        }
        return result;
    }
}
