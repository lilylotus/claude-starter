package cn.nihility.rbac.auth.service.impl;

import cn.nihility.rbac.auth.service.AuthorizationService;
import cn.nihility.rbac.permission.mapper.PermissionMapper;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 运行时鉴权业务逻辑实现：直接注入 {@code permission} 模块的 {@link PermissionMapper}
 * 查询当前用户拥有的有效权限编码集合，再做一次 {@link Set#contains}判断；不引入缓存，
 * 每次请求实时查库（rbac-permission-authorization change design.md Decision 4）。
 * {@code auth} 模块跨模块直接注入其他模块 Mapper 已有先例（{@code AuthServiceImpl}
 * 注入 {@code user} 模块的 {@code UserMapper}），本实现延续同一模式。
 */
@Service
@RequiredArgsConstructor
public class AuthorizationServiceImpl implements AuthorizationService {

    /** 权限点数据访问接口，用于查询用户当前拥有的有效权限编码集合。 */
    private final PermissionMapper permissionMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasPermission(Long userId, String menuCode) {
        Set<String> grantedCodes = getGrantedPermissionCodes(userId);
        return grantedCodes.contains(menuCode);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<String> getGrantedPermissionCodes(Long userId) {
        Set<String> grantedCodes = permissionMapper.selectGrantedPermissionCodesByUserId(userId);
        return grantedCodes != null ? grantedCodes : Set.of();
    }
}
