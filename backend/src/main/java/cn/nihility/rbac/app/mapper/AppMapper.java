package cn.nihility.rbac.app.mapper;

import cn.nihility.rbac.app.entity.AppEntity;
import cn.nihility.rbac.common.mapper.VersionedBaseMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 应用 MyBatis-Plus 数据访问接口，单表 CRUD 直接复用 {@link BaseMapper}，不在此处
 * 编写 SQL；{@link #countByColumnValue}、{@link #selectSyncPullPage} 是例外，SQL 写在
 * {@code resources/mybatis/mapper/AppMapper.xml} 里。
 */
@Mapper
public interface AppMapper extends VersionedBaseMapper<AppEntity> {

    /**
     * 统计未被逻辑删除的应用中，指定列等于给定值的记录数，供"表单字段定义"驱动的
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
     * 分页拉取应用当前数据，不过滤 {@code status}（停用/已删除记录原样返回），按
     * {@code update_time ASC, id ASC} 排序，不做组织范围过滤（app-sync-drop-changelog
     * change design.md Decision 1/2/4）。
     *
     * @param offset         偏移量
     * @param limit          每页大小
     * @param updateTimeFrom 更新时间范围起点（含），可为空
     * @param updateTimeTo   更新时间范围终点（含），可为空
     * @param ids            主键 id 列表精确过滤，{@code null} 表示不过滤
     * @param codes          应用编码列表精确过滤，{@code null} 表示不过滤
     * @return 查询结果列表
     */
    List<AppEntity> selectSyncPullPage(@Param("offset") int offset, @Param("limit") int limit,
            @Param("updateTimeFrom") LocalDateTime updateTimeFrom, @Param("updateTimeTo") LocalDateTime updateTimeTo,
            @Param("ids") List<Long> ids, @Param("codes") List<String> codes);

    /**
     * 按 {@code id} 升序游标式批量查询应用当前数据，供对账摘要接口流式扫描全部可见记录使用
     * （app-sync-changelog-pull change design.md Decision 10）。
     *
     * @param lastId    上一批最后一条记录的 id，{@code null} 表示从头开始
     * @param batchSize 本批最多查询的记录数
     * @return 本批查询结果，按 id 升序排列
     */
    List<AppEntity> selectDigestBatch(@Param("lastId") Long lastId, @Param("batchSize") int batchSize);
}
