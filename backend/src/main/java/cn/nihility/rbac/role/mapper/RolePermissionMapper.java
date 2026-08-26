package cn.nihility.rbac.role.mapper;

import cn.nihility.rbac.permission.dto.PermissionOptionVO;
import cn.nihility.rbac.role.entity.RolePermissionEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 角色权限点关联数据访问接口。按角色 id 删除复用 {@link BaseMapper}，整体同步用的批量插入
 * 和关联 {@code tab_permission} 回填名称/编码的查询写在
 * {@code resources/mybatis/mapper/RolePermissionMapper.xml} 里。
 */
@Mapper
public interface RolePermissionMapper extends BaseMapper<RolePermissionEntity> {

    /**
     * 使用单条多值插入语句批量保存角色权限点关联。
     *
     * @param entities 待保存的角色权限点关联实体列表，不能为空
     * @return 插入的关联记录数
     */
    int insertBatch(@Param("entities") List<RolePermissionEntity> entities);

    /**
     * 按角色 id 查询其全部权限点关联，关联回填权限点名称、编码；权限点若已被逻辑删除则不返回
     * （INNER JOIN 语义，脏关联数据没有展示价值）。
     *
     * @param roleId 角色 id
     * @return 权限点选项视图对象列表，按权限点显示序号降序、id 升序排列
     */
    List<PermissionOptionVO> selectPermissionsByRoleId(@Param("roleId") Long roleId);
}
