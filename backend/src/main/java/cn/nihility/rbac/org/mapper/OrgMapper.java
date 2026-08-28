package cn.nihility.rbac.org.mapper;

import cn.nihility.rbac.org.constant.OrgStatus;
import cn.nihility.rbac.org.dto.OrgPathVersionRow;
import cn.nihility.rbac.org.entity.OrgEntity;
import cn.nihility.rbac.common.mapper.VersionedBaseMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 组织机构 MyBatis-Plus 数据访问接口，单表 CRUD 直接复用 {@link BaseMapper}，
 * 不在此处编写 SQL；{@link #countByColumnValue}、{@link #selectSyncPullPage} 是例外，
 * SQL 写在 {@code resources/mybatis/mapper/OrgMapper.xml} 里。
 */
@Mapper
public interface OrgMapper extends VersionedBaseMapper<OrgEntity> {

    /**
     * 按旧路径前缀级联替换当前组织及其全部子孙组织的 id 路径，同时把每个受影响组织的
     * {@code version} 原子递增 1（app-sync-changelog-pull change design.md Decision 2，
     * 子孙组织"路径变了但没人直接操作它"也必须体现在版本号上，否则客户端会因为版本号未变
     * 而忽略这次路径迁移）。调用方应在调用前后分别用 {@link #selectPathAndVersionByPrefix}
     * 查询一次受影响组织的旧/新路径与版本，为每个 id 各自发布一条携带正确前后路径与递增后
     * 版本的事件。
     */
    int cascadeUpdateOrgPath(@Param("oldPrefix") String oldPrefix,
            @Param("newPrefix") String newPrefix,
            @Param("updateBy") String updateBy,
            @Param("updateTime") LocalDateTime updateTime);

    /**
     * 按边界安全的路径前缀（{@code org_path = prefix} 或 {@code prefix} 后紧跟一个 {@code /}
     * 分隔符）查询自身与全部子孙组织当前的路径与版本，供 {@link #cascadeUpdateOrgPath}
     * 级联更新前后分别调用一次，采集"受影响组织 id -> 旧/新路径、旧/新版本"的快照
     * （design.md Decision 2）。不会把 {@code /1/12} 误命中为 {@code /1/123} 的前缀。
     *
     * @param prefix 组织路径前缀（通常是被迁移组织自身当前的 {@code org_path}）
     * @return 自身与全部子孙组织的 id/路径/版本投影列表
     */
    List<OrgPathVersionRow> selectPathAndVersionByPrefix(@Param("prefix") String prefix);

    /** 按旧名称路径前缀级联替换当前组织及其全部子孙组织的名称路径。 */
    int cascadeUpdateOrgNamePath(@Param("oldPrefix") String oldPrefix,
            @Param("newPrefix") String newPrefix,
            @Param("updateBy") String updateBy,
            @Param("updateTime") LocalDateTime updateTime);

    /**
     * 统计未被逻辑删除的组织中，指定列等于给定值的记录数，供"表单字段定义"驱动的
     * 非锁定字段唯一性校验使用。{@code column} 只接受调用方从
     * {@code tab_metadata_field} 目录解析得到、并经白名单校验的合法列名，不接受
     * 任意字符串拼接，避免 SQL 注入（design.md Decision 8）。
     *
     * @param column    目标列名，仅接受白名单内的合法列名
     * @param value     待比对的值
     * @param excludeId 需要排除的自身 id，创建场景传 {@code null}
     * @return 命中的记录数
     */
    int countByColumnValue(@Param("column") String column, @Param("value") String value,
            @Param("excludeId") Long excludeId);

    /**
     * 当某组织自身的 {@code code} 发生变化时，把其全部未被逻辑删除的直属子组织的
     * {@code parentCode} 批量更新为新值，只下沉一层，不递归到孙级（孙级的
     * {@code parentCode} 指向子级的 {@code code}，未发生变化）（org-add-parent-code
     * change design.md Decision 2）。
     *
     * @param parentId    上级组织 id
     * @param newParentCode 新的上级组织编码（即上级组织变更后的 {@code code}）
     * @return 受影响的行数
     */
    default int updateChildrenParentCode(Long parentId, String newParentCode) {
        LambdaUpdateWrapper<OrgEntity> wrapper = Wrappers.<OrgEntity>lambdaUpdate()
                .eq(OrgEntity::getParentId, parentId)
                .ne(OrgEntity::getStatus, OrgStatus.DELETED)
                .set(OrgEntity::getParentCode, newParentCode);
        return update(null, wrapper);
    }

    /**
     * 分页拉取组织当前数据，不过滤 {@code status}（停用/已删除记录原样返回），按
     * {@code update_time ASC, id ASC} 排序（app-sync-drop-changelog change design.md
     * Decision 1/2/4）。
     *
     * @param offset         偏移量
     * @param limit          每页大小
     * @param updateTimeFrom 更新时间范围起点（含），可为空
     * @param updateTimeTo   更新时间范围终点（含），可为空
     * @param ids            主键 id 列表精确过滤，{@code null} 表示不过滤
     * @param codes          组织编码列表精确过滤，{@code null} 表示不过滤
     * @param allowedOrgIds  组织范围过滤下推的允许组织 id 全集，{@code null} 表示不限制
     * @return 查询结果列表
     */
    List<OrgEntity> selectSyncPullPage(@Param("offset") int offset, @Param("limit") int limit,
            @Param("updateTimeFrom") LocalDateTime updateTimeFrom, @Param("updateTimeTo") LocalDateTime updateTimeTo,
            @Param("ids") List<Long> ids, @Param("codes") List<String> codes,
            @Param("allowedOrgIds") Set<Long> allowedOrgIds);

    /**
     * 按 {@code id} 升序游标式批量查询组织当前数据，供对账摘要接口流式扫描全部可见记录使用
     * （app-sync-changelog-pull change design.md Decision 10）。
     *
     * @param lastId        上一批最后一条记录的 id，{@code null} 表示从头开始
     * @param batchSize     本批最多查询的记录数
     * @param allowedOrgIds 组织范围过滤下推的允许组织 id 全集，{@code null} 表示不限制
     * @return 本批查询结果，按 id 升序排列
     */
    List<OrgEntity> selectDigestBatch(@Param("lastId") Long lastId, @Param("batchSize") int batchSize,
            @Param("allowedOrgIds") Set<Long> allowedOrgIds);
}
