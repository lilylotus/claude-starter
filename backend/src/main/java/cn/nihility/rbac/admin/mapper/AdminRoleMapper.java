package cn.nihility.rbac.admin.mapper;

import cn.nihility.rbac.admin.dto.AdminRoleVO;
import cn.nihility.rbac.admin.entity.AdminRoleEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 管理员角色关联数据访问接口。单表 CRUD（整体同步用的批量插入/按管理员 id 删除）直接复用
 * {@link BaseMapper}；按管理员 id 查询关联角色需要关联 {@code tab_role} 回填角色名称，
 * SQL 写在 {@code resources/mybatis/mapper/AdminRoleMapper.xml} 里，用单条 JOIN 完成。
 */
@Mapper
public interface AdminRoleMapper extends BaseMapper<AdminRoleEntity> {

    /**
     * 按管理员 id 查询其全部角色关联，关联回填角色名称；角色若已被逻辑删除则不返回
     * （INNER JOIN 语义，脏关联数据没有展示价值）。
     *
     * @param adminId 管理员 id
     * @return 角色关联视图对象列表，按角色显示序号降序、id 升序排列
     */
    List<AdminRoleVO> selectRolesByAdminId(@Param("adminId") Long adminId);
}
