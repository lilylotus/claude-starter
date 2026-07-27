package cn.nihility.rbac.permission.mapper;

import cn.nihility.rbac.permission.entity.PermissionEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.Set;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 权限点 MyBatis-Plus 数据访问接口。单表 CRUD 直接复用 {@link BaseMapper}，不在此处编写 SQL；
 * 按用户 id 查询其当前拥有的有效权限编码集合需要跨 {@code tab_admin}/{@code tab_admin_role}/
 * {@code tab_role}/{@code tab_role_permission} 关联，SQL 写在
 * {@code resources/mybatis/mapper/PermissionMapper.xml} 里，用单条 JOIN 完成
 * （rbac-permission-authorization change design.md Decision 4）。
 */
@Mapper
public interface PermissionMapper extends BaseMapper<PermissionEntity> {

    /**
     * 查询某个用户当前拥有的全部有效权限编码集合：该用户存在启用状态的管理员身份，
     * 且经由其关联的启用状态角色、启用状态权限点关联查得的权限编码；用户没有启用状态的
     * 管理员身份、或其角色/权限点均未启用时，返回空集合。
     *
     * @param userId 用户 id
     * @return 有效权限编码集合，可能为空集合但不会为 {@code null}
     */
    Set<String> selectGrantedPermissionCodesByUserId(@Param("userId") Long userId);
}
