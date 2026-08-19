package cn.nihility.rbac.role.mapper;

import cn.nihility.rbac.role.entity.RoleEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 角色 MyBatis-Plus 数据访问接口，单表 CRUD 直接复用 {@link BaseMapper}，不在此处
 * 编写 SQL；{@link #selectSyncPullPage} 是唯一的例外，SQL 写在
 * {@code resources/mybatis/mapper/RoleMapper.xml} 里。
 */
@Mapper
public interface RoleMapper extends BaseMapper<RoleEntity> {

    /**
     * 分页拉取角色当前数据，不过滤 {@code status}（停用/已删除记录原样返回），按
     * {@code update_time ASC, id ASC} 排序，不做组织范围过滤（app-sync-drop-changelog
     * change design.md Decision 1/2/4）。
     *
     * @param offset         偏移量
     * @param limit          每页大小
     * @param updateTimeFrom 更新时间范围起点（含），可为空
     * @param updateTimeTo   更新时间范围终点（含），可为空
     * @param ids            主键 id 列表精确过滤，{@code null} 表示不过滤
     * @param codes          角色编码列表精确过滤，{@code null} 表示不过滤
     * @return 查询结果列表
     */
    List<RoleEntity> selectSyncPullPage(@Param("offset") int offset, @Param("limit") int limit,
            @Param("updateTimeFrom") LocalDateTime updateTimeFrom, @Param("updateTimeTo") LocalDateTime updateTimeTo,
            @Param("ids") List<Long> ids, @Param("codes") List<String> codes);
}
