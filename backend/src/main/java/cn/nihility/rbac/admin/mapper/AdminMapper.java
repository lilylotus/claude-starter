package cn.nihility.rbac.admin.mapper;

import cn.nihility.rbac.admin.dto.AdminVO;
import cn.nihility.rbac.admin.entity.AdminEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import java.util.Set;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 管理员数据访问接口。单表 CRUD 直接复用 {@link BaseMapper}；分页查询、按 id 查询详情
 * 需要关联 {@code tab_user} 回填关联用户姓名，SQL 写在
 * {@code resources/mybatis/mapper/AdminMapper.xml} 里，用单条 JOIN 完成，不在应用层
 * 拆成多次查询再拼装。
 */
@Mapper
public interface AdminMapper extends BaseMapper<AdminEntity> {

    /**
     * 分页查询管理员，不支持任何筛选参数，关联回填关联用户姓名。
     *
     * @param page          分页参数
     * @param deletedStatus 逻辑删除状态值，用于在 SQL 里排除已删除记录
     * @return 分页结果，{@code records} 已包含 {@code userName}
     */
    IPage<AdminVO> selectAdminPage(IPage<?> page, @Param("deletedStatus") int deletedStatus);

    /**
     * 按 id 查询管理员详情，关联回填关联用户姓名。
     *
     * @param id            管理员 id
     * @param deletedStatus 逻辑删除状态值，用于在 SQL 里排除已删除记录
     * @return 管理员详情，不存在或已被逻辑删除时返回 {@code null}
     */
    AdminVO selectAdminDetail(@Param("id") Long id, @Param("deletedStatus") int deletedStatus);

    /**
     * 按管辖组织范围统计未被逻辑删除的管理员总数。管理员本身没有直接的组织外键，组织
     * 归属看的是其关联用户（{@code tab_admin.user_id}）自己的任职记录，因此复用与
     * {@code UserMapper#countUsersInScope} 相同的 {@code EXISTS tab_user_position}
     * 判断逻辑，只是主表换成 {@code tab_admin}（scope-dashboard-stats-by-admin-org
     * change design.md Decision 2）。
     *
     * @param allowedOrgIds         管辖组织 id 全集，{@code null} 表示不受限制；非
     *                              {@code null}（哪怕是空集合）表示受限
     * @param deletedStatus         {@code tab_admin} 的逻辑删除状态字面量（{@code AdminStatus.DELETED}）
     * @param positionDeletedStatus {@code tab_user_position} 的逻辑删除状态字面量（{@code PositionStatus.DELETED}）
     * @return 命中的管理员总数
     */
    int countAdminsInScope(@Param("allowedOrgIds") Set<Long> allowedOrgIds, @Param("deletedStatus") int deletedStatus,
            @Param("positionDeletedStatus") int positionDeletedStatus);
}
