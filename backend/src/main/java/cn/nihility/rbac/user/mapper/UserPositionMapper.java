package cn.nihility.rbac.user.mapper;

import cn.nihility.rbac.user.dto.PositionVO;
import cn.nihility.rbac.user.entity.UserPositionEntity;
import cn.nihility.rbac.common.mapper.VersionedBaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 用户任职记录数据访问接口。单表 CRUD 直接复用 {@link BaseMapper}；按组织分页查询、
 * 按 id 查询详情需要关联 {@code tab_user}、{@code tab_org} 两张表回填姓名/组织名称，
 * SQL 写在 {@code resources/mybatis/mapper/UserPositionMapper.xml} 里，用单条 JOIN
 * 完成，不在应用层拆成多次查询再拼装。
 */
@Mapper
public interface UserPositionMapper extends VersionedBaseMapper<UserPositionEntity> {

    /**
     * 按所属组织 id 分页查询任职记录，关联回填所属用户姓名、所属组织名称。
     *
     * @param page          分页参数
     * @param orgId         所属组织 id
     * @param deletedStatus 逻辑删除状态值，用于在 SQL 里排除已删除记录
     * @return 分页结果，{@code records} 已包含 {@code userName}/{@code orgName}
     */
    IPage<PositionVO> selectPositionPage(IPage<?> page, @Param("orgId") Long orgId,
            @Param("deletedStatus") int deletedStatus);

    /**
     * 按 id 查询任职记录详情，关联回填所属用户姓名、所属组织名称。
     *
     * @param id            任职记录 id
     * @param deletedStatus 逻辑删除状态值，用于在 SQL 里排除已删除记录
     * @return 任职记录详情，不存在或已被逻辑删除时返回 {@code null}
     */
    PositionVO selectPositionDetail(@Param("id") Long id, @Param("deletedStatus") int deletedStatus);

    /**
     * 按任职 id 批量查询用户名称与组织名称，供通知日志一页内批量回填业务名称。
     *
     * @param ids 任职记录 id 集合
     * @return 任职 id、用户名称、组织名称投影
     */
    List<PositionVO> selectPositionNamesByIds(@Param("ids") Set<Long> ids);

    /**
     * 统计未被逻辑删除的任职记录中，指定列等于给定值的记录数，供"表单字段定义"驱动的
     * 非锁定字段唯一性校验使用。{@code column} 只接受调用方从 {@code tab_metadata_field}
     * 目录解析得到、并经白名单校验的合法列名。
     *
     * @param column    目标列名，仅接受白名单内的合法列名
     * @param value     待比对的值
     * @param excludeId 需要排除的自身 id，创建场景传 {@code null}
     * @return 命中的记录数
     */
    int countByColumnValue(@Param("column") String column, @Param("value") String value,
            @Param("excludeId") Long excludeId);

    /**
     * 分页拉取任职当前数据，不过滤 {@code status}（停用/已删除记录原样返回），按
     * {@code update_time ASC, id ASC} 排序（app-sync-drop-changelog change design.md
     * Decision 1/2/4）。任职数据域没有业务编码字段，不支持 {@code codes} 过滤。
     *
     * @param offset         偏移量
     * @param limit          每页大小
     * @param updateTimeFrom 更新时间范围起点（含），可为空
     * @param updateTimeTo   更新时间范围终点（含），可为空
     * @param ids            主键 id 列表精确过滤，{@code null} 表示不过滤
     * @param allowedOrgIds  组织范围过滤下推的允许组织 id 全集，{@code null} 表示不限制
     * @return 查询结果列表
     */
    List<UserPositionEntity> selectSyncPullPage(@Param("offset") int offset, @Param("limit") int limit,
            @Param("updateTimeFrom") LocalDateTime updateTimeFrom, @Param("updateTimeTo") LocalDateTime updateTimeTo,
            @Param("ids") List<Long> ids, @Param("allowedOrgIds") Set<Long> allowedOrgIds);

    /**
     * 按管辖组织范围查询全部未被逻辑删除的任职记录，关联回填所属用户姓名、所属组织
     * 名称，不分页，供 {@code master-data-excel-export} 能力的任职导出使用
     * （design.md Decision 1）。{@code allowedOrgIds} 为 {@code null} 表示不限制，
     * 非 {@code null}（哪怕是空集合）表示受限，按 {@code org_id IN (:allowedOrgIds)}
     * 过滤。
     *
     * @param allowedOrgIds 管辖组织 id 全集，{@code null} 表示不受限制
     * @param deletedStatus 逻辑删除状态值，用于在 SQL 里排除已删除记录
     * @return 任职记录列表，按 id 升序排列，已包含 {@code userName}/{@code orgName}
     */
    List<PositionVO> selectAllForExport(@Param("allowedOrgIds") Set<Long> allowedOrgIds,
            @Param("deletedStatus") int deletedStatus);

    /**
     * 按 {@code id} 升序游标式批量查询任职当前数据，供对账摘要接口流式扫描全部可见记录使用
     * （app-sync-changelog-pull change design.md Decision 10）。
     *
     * @param lastId        上一批最后一条记录的 id，{@code null} 表示从头开始
     * @param batchSize     本批最多查询的记录数
     * @param allowedOrgIds 组织范围过滤下推的允许组织 id 全集，{@code null} 表示不限制
     * @return 本批查询结果，按 id 升序排列
     */
    List<UserPositionEntity> selectDigestBatch(@Param("lastId") Long lastId, @Param("batchSize") int batchSize,
            @Param("allowedOrgIds") Set<Long> allowedOrgIds);
}
