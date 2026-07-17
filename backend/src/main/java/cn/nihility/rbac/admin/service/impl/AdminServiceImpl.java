package cn.nihility.rbac.admin.service.impl;

import cn.nihility.rbac.admin.constant.AdminStatus;
import cn.nihility.rbac.admin.dto.AdminCreateRequest;
import cn.nihility.rbac.admin.dto.AdminOrgScopeRequest;
import cn.nihility.rbac.admin.dto.AdminUpdateRequest;
import cn.nihility.rbac.admin.dto.AdminVO;
import cn.nihility.rbac.admin.entity.AdminEntity;
import cn.nihility.rbac.admin.entity.AdminOrgScopeEntity;
import cn.nihility.rbac.admin.entity.AdminRoleEntity;
import cn.nihility.rbac.admin.mapper.AdminMapper;
import cn.nihility.rbac.admin.mapper.AdminOrgScopeMapper;
import cn.nihility.rbac.admin.mapper.AdminRoleMapper;
import cn.nihility.rbac.admin.mapstruct.AdminConvert;
import cn.nihility.rbac.admin.service.AdminService;
import cn.nihility.rbac.common.PageResult;
import cn.nihility.rbac.common.exception.BusinessException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 管理员管理业务逻辑实现。角色关联（{@code tab_admin_role}）与组织管辖范围
 * （{@code tab_admin_org_scope}）在每次创建/更新时采用"先删后插"的整体同步策略，
 * 不做按行 diff（两张关联表都是纯关联/配置行，没有独立业务详情需要跨版本保留）。
 */
@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    /** 当前项目尚未接入登录鉴权，创建人/更新人暂时固定为该值。 */
    private static final String DEFAULT_OPERATOR = "admin";

    /** 管理员数据访问接口。 */
    private final AdminMapper adminMapper;

    /** 管理员角色关联数据访问接口。 */
    private final AdminRoleMapper adminRoleMapper;

    /** 管理员组织管辖范围数据访问接口。 */
    private final AdminOrgScopeMapper adminOrgScopeMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResult<AdminVO> getPage(Integer page, Integer pageSize) {
        IPage<AdminVO> resultPage = adminMapper.selectAdminPage(new Page<>(page, pageSize), AdminStatus.DELETED);
        return PageResult.of(resultPage.getRecords(), resultPage);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AdminVO getById(Long id) {
        return getExistingVO(id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AdminVO create(AdminCreateRequest request) {
        checkCodeUnique(request.getCode(), null);
        checkUserIdUnique(request.getUserId(), null);

        AdminEntity entity = AdminConvert.INSTANCE.toEntity(request);
        LocalDateTime now = LocalDateTime.now();
        entity.setStatus(AdminStatus.ENABLED);
        entity.setCreateBy(DEFAULT_OPERATOR);
        entity.setCreateTime(now);
        entity.setUpdateBy(DEFAULT_OPERATOR);
        entity.setUpdateTime(now);
        adminMapper.insert(entity);

        syncRoles(entity.getId(), request.getRoleIds());
        syncOrgScopes(entity.getId(), request.getOrgScopes());

        return getById(entity.getId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AdminVO update(Long id, AdminUpdateRequest request) {
        AdminEntity entity = getExistingEntity(id);
        checkCodeUnique(request.getCode(), id);
        checkUserIdUnique(request.getUserId(), id);

        AdminConvert.INSTANCE.updateEntity(request, entity);
        entity.setUpdateBy(DEFAULT_OPERATOR);
        entity.setUpdateTime(LocalDateTime.now());
        adminMapper.updateById(entity);

        syncRoles(id, request.getRoleIds());
        syncOrgScopes(id, request.getOrgScopes());

        return getById(id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AdminVO enable(Long id) {
        return changeStatus(id, AdminStatus.ENABLED);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AdminVO disable(Long id) {
        return changeStatus(id, AdminStatus.DISABLED);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void delete(Long id) {
        AdminEntity entity = getExistingEntity(id);
        entity.setStatus(AdminStatus.DELETED);
        entity.setUpdateBy(DEFAULT_OPERATOR);
        entity.setUpdateTime(LocalDateTime.now());
        adminMapper.updateById(entity);
    }

    /**
     * 变更管理员状态（启用/停用）并返回更新后的详情，不影响角色关联与组织管辖范围。
     *
     * @param id     管理员 id
     * @param status 目标状态
     * @return 更新后的管理员详情
     */
    private AdminVO changeStatus(Long id, int status) {
        AdminEntity entity = getExistingEntity(id);
        entity.setStatus(status);
        entity.setUpdateBy(DEFAULT_OPERATOR);
        entity.setUpdateTime(LocalDateTime.now());
        adminMapper.updateById(entity);
        return getById(id);
    }

    /**
     * 查询一个未被逻辑删除的管理员，不存在时抛出业务异常。
     *
     * @param id 管理员 id
     * @return 管理员实体
     */
    private AdminEntity getExistingEntity(Long id) {
        AdminEntity entity = adminMapper.selectById(id);
        if (entity == null || Objects.equals(entity.getStatus(), AdminStatus.DELETED)) {
            throw new BusinessException("管理员不存在");
        }
        return entity;
    }

    /**
     * 查询一个未被逻辑删除的管理员详情，含关联用户姓名、全部角色、全部管辖组织范围，
     * 不存在时抛出业务异常。
     *
     * @param id 管理员 id
     * @return 管理员详情视图对象
     */
    private AdminVO getExistingVO(Long id) {
        AdminVO vo = adminMapper.selectAdminDetail(id, AdminStatus.DELETED);
        if (vo == null) {
            throw new BusinessException("管理员不存在");
        }
        vo.setRoles(adminRoleMapper.selectRolesByAdminId(id));
        vo.setOrgScopes(adminOrgScopeMapper.selectOrgScopesByAdminId(id));
        return vo;
    }

    /**
     * 校验管理员编码在未删除的管理员中是否唯一。
     *
     * @param code      待校验的管理员编码
     * @param excludeId 更新场景下需要排除的自身 id，创建场景传 null
     */
    private void checkCodeUnique(String code, Long excludeId) {
        LambdaQueryWrapper<AdminEntity> wrapper = new LambdaQueryWrapper<AdminEntity>()
                .eq(AdminEntity::getCode, code)
                .ne(AdminEntity::getStatus, AdminStatus.DELETED);
        if (excludeId != null) {
            wrapper.ne(AdminEntity::getId, excludeId);
        }
        Long count = adminMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException("管理员编码[" + code + "]已存在");
        }
    }

    /**
     * 校验关联用户 id 在未删除的管理员中是否唯一（同一用户最多关联一个未删除的管理员身份）。
     *
     * @param userId    待校验的关联用户 id
     * @param excludeId 更新场景下需要排除的自身 id，创建场景传 null
     */
    private void checkUserIdUnique(Long userId, Long excludeId) {
        LambdaQueryWrapper<AdminEntity> wrapper = new LambdaQueryWrapper<AdminEntity>()
                .eq(AdminEntity::getUserId, userId)
                .ne(AdminEntity::getStatus, AdminStatus.DELETED);
        if (excludeId != null) {
            wrapper.ne(AdminEntity::getId, excludeId);
        }
        Long count = adminMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException("用户[" + userId + "]已关联其他管理员");
        }
    }

    /**
     * 把请求中的角色 id 列表与管理员当前的角色关联做整体同步：先物理删除该管理员名下
     * 全部既有角色关联行，再按请求列表整批插入，不做按行 diff。
     *
     * @param adminId 管理员 id
     * @param roleIds 请求中的角色 id 列表，可为空（视为清空全部既有角色关联）
     */
    private void syncRoles(Long adminId, List<Long> roleIds) {
        adminRoleMapper.delete(new LambdaQueryWrapper<AdminRoleEntity>().eq(AdminRoleEntity::getAdminId, adminId));

        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        for (Long roleId : roleIds) {
            AdminRoleEntity entity = AdminRoleEntity.builder()
                    .adminId(adminId)
                    .roleId(roleId)
                    .createBy(DEFAULT_OPERATOR)
                    .createTime(now)
                    .updateBy(DEFAULT_OPERATOR)
                    .updateTime(now)
                    .build();
            adminRoleMapper.insert(entity);
        }
    }

    /**
     * 把请求中的管辖组织范围列表与管理员当前的组织管辖范围做整体同步：先物理删除该管理员
     * 名下全部既有管辖组织范围行，再按请求列表整批插入，不做按行 diff。
     *
     * @param adminId   管理员 id
     * @param orgScopes 请求中的管辖组织范围列表，可为空（视为清空全部既有管辖组织范围）
     */
    private void syncOrgScopes(Long adminId, List<AdminOrgScopeRequest> orgScopes) {
        adminOrgScopeMapper.delete(new LambdaQueryWrapper<AdminOrgScopeEntity>()
                .eq(AdminOrgScopeEntity::getAdminId, adminId));

        if (orgScopes == null || orgScopes.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        for (AdminOrgScopeRequest request : orgScopes) {
            AdminOrgScopeEntity entity = AdminOrgScopeEntity.builder()
                    .adminId(adminId)
                    .orgId(request.getOrgId())
                    .includeChildren(request.getIncludeChildren())
                    .createBy(DEFAULT_OPERATOR)
                    .createTime(now)
                    .updateBy(DEFAULT_OPERATOR)
                    .updateTime(now)
                    .build();
            adminOrgScopeMapper.insert(entity);
        }
    }
}
