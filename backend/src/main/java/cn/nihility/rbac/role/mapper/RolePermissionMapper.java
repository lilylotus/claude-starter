package cn.nihility.rbac.role.mapper;

import cn.nihility.rbac.permission.dto.PermissionOptionVO;
import cn.nihility.rbac.role.entity.RolePermissionEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 角色权限点关联数据访问接口。单表 CRUD（整体同步用的批量插入/按角色 id 删除）直接复用
 * {@link BaseMapper}；按角色 id 查询关联权限点需要关联 {@code tab_permission} 回填名称/编码，
 * SQL 写在 {@code resources/mybatis/mapper/RolePermissionMapper.xml} 里，用单条 JOIN 完成。
 */
@Mapper
public interface RolePermissionMapper extends BaseMapper<RolePermissionEntity> {

    /**
     * 按角色 id 查询其全部权限点关联，关联回填权限点名称、编码；权限点若已被逻辑删除则不返回
     * （INNER JOIN 语义，脏关联数据没有展示价值）。
     *
     * @param roleId 角色 id
     * @return 权限点选项视图对象列表，按权限点显示序号降序、id 升序排列
     */
    List<PermissionOptionVO> selectPermissionsByRoleId(@Param("roleId") Long roleId);
}
