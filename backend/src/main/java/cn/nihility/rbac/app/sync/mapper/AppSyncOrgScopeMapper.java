package cn.nihility.rbac.app.sync.mapper;

import cn.nihility.rbac.app.sync.dto.AppSyncOrgScopeVO;
import cn.nihility.rbac.app.sync.entity.AppSyncOrgScopeEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 应用同步组织范围数据访问接口。单表 CRUD（整体替换用的批量插入/按
 * {@code (appRefId, syncDomain)} 删除）直接复用 {@link BaseMapper}；按应用 id + 数据域查询
 * 需要关联 {@code tab_org} 回填组织名称，SQL 写在
 * {@code resources/mybatis/mapper/AppSyncOrgScopeMapper.xml} 里，用单条 JOIN 完成
 * （参照 {@code cn.nihility.rbac.admin.mapper.AdminOrgScopeMapper} 的既有写法）。
 */
@Mapper
public interface AppSyncOrgScopeMapper extends BaseMapper<AppSyncOrgScopeEntity> {

    /**
     * 按应用 id + 数据域查询其全部组织同步范围，关联回填组织名称；组织若已被逻辑删除则不
     * 返回（INNER JOIN 语义，脏关联数据没有展示价值）。
     *
     * @param appRefId   应用 id（{@code tab_app.id}）
     * @param syncDomain 数据域
     * @return 组织同步范围视图对象列表，按组织显示序号降序、id 升序排列
     */
    List<AppSyncOrgScopeVO> selectByAppRefIdAndDomain(@Param("appRefId") Long appRefId,
            @Param("syncDomain") String syncDomain);
}
