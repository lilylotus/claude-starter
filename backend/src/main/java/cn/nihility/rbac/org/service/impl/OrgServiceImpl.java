package cn.nihility.rbac.org.service.impl;

import cn.nihility.rbac.common.PageResult;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.org.constant.OrgStatus;
import cn.nihility.rbac.org.dto.OrgCreateRequest;
import cn.nihility.rbac.org.dto.OrgTreeNodeVO;
import cn.nihility.rbac.org.dto.OrgUpdateRequest;
import cn.nihility.rbac.org.dto.OrgVO;
import cn.nihility.rbac.org.entity.OrgEntity;
import cn.nihility.rbac.org.mapper.OrgMapper;
import cn.nihility.rbac.org.mapstruct.OrgConvert;
import cn.nihility.rbac.org.service.OrgService;
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
 * 组织机构业务逻辑实现。
 */
@Service
@RequiredArgsConstructor
public class OrgServiceImpl implements OrgService {

    /** 顶级组织的上级 id。 */
    private static final long ROOT_PARENT_ID = 0L;

    /** 当前项目尚未接入登录鉴权，创建人/更新人暂时固定为该值。 */
    private static final String DEFAULT_OPERATOR = "admin";

    /** 组织数据访问接口。 */
    private final OrgMapper orgMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    public List<OrgTreeNodeVO> getTree() {
        List<OrgEntity> entities = listAllUndeletedOrdered();

        Map<Long, OrgTreeNodeVO> nodeMap = new LinkedHashMap<>();
        for (OrgEntity entity : entities) {
            nodeMap.put(entity.getId(), OrgConvert.INSTANCE.toTreeNode(entity));
        }

        List<OrgTreeNodeVO> roots = new ArrayList<>();
        for (OrgEntity entity : entities) {
            OrgTreeNodeVO node = nodeMap.get(entity.getId());
            OrgTreeNodeVO parentNode = nodeMap.get(entity.getParentId());
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
    public PageResult<OrgVO> getChildren(Long parentId, Integer page, Integer pageSize) {
        long effectiveParentId = parentId != null ? parentId : ROOT_PARENT_ID;
        Page<OrgEntity> queryPage = new Page<>(page, pageSize);
        Page<OrgEntity> resultPage = orgMapper.selectPage(queryPage, childrenQueryWrapper(effectiveParentId));
        List<OrgVO> records = toVOListWithParentName(resultPage.getRecords());
        return PageResult.of(records, resultPage);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<OrgTreeNodeVO> getChildrenTreeNodes(Long parentId) {
        long effectiveParentId = parentId != null ? parentId : ROOT_PARENT_ID;
        List<OrgEntity> entities = orgMapper.selectList(childrenQueryWrapper(effectiveParentId));

        List<OrgTreeNodeVO> nodes = new ArrayList<>();
        for (OrgEntity entity : entities) {
            OrgTreeNodeVO node = OrgConvert.INSTANCE.toTreeNode(entity);
            node.setChildren(new ArrayList<>());
            nodes.add(node);
        }
        return nodes;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OrgVO getById(Long id) {
        OrgEntity entity = getExistingEntity(id);
        return toVOListWithParentName(List.of(entity)).get(0);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OrgVO create(OrgCreateRequest request) {
        checkCodeUnique(request.getCode(), null);

        OrgEntity entity = OrgConvert.INSTANCE.toEntity(request);
        LocalDateTime now = LocalDateTime.now();
        entity.setStatus(OrgStatus.ENABLED);
        entity.setCreateBy(DEFAULT_OPERATOR);
        entity.setCreateTime(now);
        entity.setUpdateBy(DEFAULT_OPERATOR);
        entity.setUpdateTime(now);
        orgMapper.insert(entity);

        return getById(entity.getId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OrgVO update(Long id, OrgUpdateRequest request) {
        OrgEntity entity = getExistingEntity(id);
        checkCodeUnique(request.getCode(), id);

        OrgConvert.INSTANCE.updateEntity(request, entity);
        entity.setUpdateBy(DEFAULT_OPERATOR);
        entity.setUpdateTime(LocalDateTime.now());
        orgMapper.updateById(entity);

        return getById(id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OrgVO enable(Long id) {
        return changeStatus(id, OrgStatus.ENABLED);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OrgVO disable(Long id) {
        return changeStatus(id, OrgStatus.DISABLED);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void delete(Long id) {
        OrgEntity entity = getExistingEntity(id);

        Long childCount = orgMapper.selectCount(new LambdaQueryWrapper<OrgEntity>()
                .eq(OrgEntity::getParentId, id)
                .ne(OrgEntity::getStatus, OrgStatus.DELETED));
        if (childCount != null && childCount > 0) {
            throw new BusinessException("该组织下存在未删除的下级组织，无法删除");
        }

        entity.setStatus(OrgStatus.DELETED);
        entity.setUpdateBy(DEFAULT_OPERATOR);
        entity.setUpdateTime(LocalDateTime.now());
        orgMapper.updateById(entity);
    }

    /**
     * 变更组织状态（启用/停用）并返回更新后的详情。
     *
     * @param id     组织 id
     * @param status 目标状态
     * @return 更新后的组织详情
     */
    private OrgVO changeStatus(Long id, int status) {
        OrgEntity entity = getExistingEntity(id);
        entity.setStatus(status);
        entity.setUpdateBy(DEFAULT_OPERATOR);
        entity.setUpdateTime(LocalDateTime.now());
        orgMapper.updateById(entity);
        return getById(id);
    }

    /**
     * 查询全部未删除的组织，按显示序号降序、id 升序排列。
     *
     * @return 未删除的组织实体列表
     */
    private List<OrgEntity> listAllUndeletedOrdered() {
        return orgMapper.selectList(new LambdaQueryWrapper<OrgEntity>()
                .ne(OrgEntity::getStatus, OrgStatus.DELETED)
                .orderByDesc(OrgEntity::getShowOrder)
                .orderByAsc(OrgEntity::getId));
    }

    /**
     * 构造查询某个上级组织下未删除直属子组织的条件，按显示序号降序、id 升序排列。
     * 供分页查询（{@link #getChildren}）与树懒加载查询（{@link #getChildrenTreeNodes}）共用。
     *
     * @param parentId 上级组织 id
     * @return 查询条件
     */
    private LambdaQueryWrapper<OrgEntity> childrenQueryWrapper(long parentId) {
        return new LambdaQueryWrapper<OrgEntity>()
                .eq(OrgEntity::getParentId, parentId)
                .ne(OrgEntity::getStatus, OrgStatus.DELETED)
                .orderByDesc(OrgEntity::getShowOrder)
                .orderByAsc(OrgEntity::getId);
    }

    /**
     * 查询一个未被逻辑删除的组织，不存在时抛出业务异常。
     *
     * @param id 组织 id
     * @return 组织实体
     */
    private OrgEntity getExistingEntity(Long id) {
        OrgEntity entity = orgMapper.selectById(id);
        if (entity == null || Objects.equals(entity.getStatus(), OrgStatus.DELETED)) {
            throw new BusinessException("组织不存在");
        }
        return entity;
    }

    /**
     * 校验组织编码在未删除的组织中是否唯一。
     *
     * @param code      待校验的组织编码
     * @param excludeId 更新场景下需要排除的自身 id，创建场景传 null
     */
    private void checkCodeUnique(String code, Long excludeId) {
        LambdaQueryWrapper<OrgEntity> wrapper = new LambdaQueryWrapper<OrgEntity>()
                .eq(OrgEntity::getCode, code)
                .ne(OrgEntity::getStatus, OrgStatus.DELETED);
        if (excludeId != null) {
            wrapper.ne(OrgEntity::getId, excludeId);
        }
        Long count = orgMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException("组织编码[" + code + "]已存在");
        }
    }

    /**
     * 把组织实体列表转换为详情视图对象列表，并批量解析上级组织名称。
     *
     * @param entities 组织实体列表
     * @return 详情视图对象列表
     */
    private List<OrgVO> toVOListWithParentName(List<OrgEntity> entities) {
        Set<Long> parentIds = entities.stream()
                .map(OrgEntity::getParentId)
                .filter(parentId -> parentId != null && parentId != ROOT_PARENT_ID)
                .collect(Collectors.toSet());

        Map<Long, String> parentNameMap;
        if (parentIds.isEmpty()) {
            parentNameMap = Map.of();
        } else {
            List<OrgEntity> parents = orgMapper.selectList(new LambdaQueryWrapper<OrgEntity>()
                    .in(OrgEntity::getId, parentIds));
            parentNameMap = parents.stream()
                    .collect(Collectors.toMap(OrgEntity::getId, OrgEntity::getName, (left, right) -> left));
        }

        List<OrgVO> result = OrgConvert.INSTANCE.toVOList(entities);
        for (OrgVO vo : result) {
            vo.setParentName(parentNameMap.get(vo.getParentId()));
        }
        return result;
    }
}
