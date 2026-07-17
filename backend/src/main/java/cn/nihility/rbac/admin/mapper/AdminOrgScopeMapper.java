package cn.nihility.rbac.admin.mapper;

import cn.nihility.rbac.admin.dto.AdminOrgScopeVO;
import cn.nihility.rbac.admin.entity.AdminOrgScopeEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 管理员组织管辖范围数据访问接口。单表 CRUD（整体同步用的批量插入/按管理员 id 删除）
 * 直接复用 {@link BaseMapper}；按管理员 id 查询管辖组织范围需要关联 {@code tab_org}
 * 回填组织名称，SQL 写在 {@code resources/mybatis/mapper/AdminOrgScopeMapper.xml} 里，
 * 用单条 JOIN 完成。
 */
@Mapper
public interface AdminOrgScopeMapper extends BaseMapper<AdminOrgScopeEntity> {

    /**
     * 按管理员 id 查询其全部组织管辖范围，关联回填组织名称；组织若已被逻辑删除则不返回
     * （INNER JOIN 语义，脏关联数据没有展示价值）。
     *
     * @param adminId 管理员 id
     * @return 组织管辖范围视图对象列表，按组织显示序号降序、id 升序排列
     */
    List<AdminOrgScopeVO> selectOrgScopesByAdminId(@Param("adminId") Long adminId);
}
