package cn.nihility.rbac.sync.changelog.mapper;

import cn.nihility.rbac.sync.changelog.entity.AppDataChangeLogEntity;
import cn.nihility.rbac.sync.scope.ScopePrefix;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 全局应用数据变更流水 Mapper。{@link #selectChanges}、{@link #selectMaxChangeSeq}、
 * {@link #selectExpiredChangeSeqBatch} 的 SQL 写在
 * {@code resources/mybatis/mapper/AppDataChangeLogMapper.xml} 里。
 */
@Mapper
public interface AppDataChangeLogMapper extends BaseMapper<AppDataChangeLogEntity> {

    /**
     * 按数据类型、游标下限游标式批量查询变更流水，供 {@code /changes} 增量拉取接口使用
     * （app-sync-changelog-pull change design.md Decision 4）。{@code prefixes} 为
     * {@code null} 或空列表时不附加组织范围路径过滤（USER/APP/ROLE 数据域，或 ORG/POSITION
     * 数据域未配置组织范围限制时均传 {@code null}）；非空时对
     * {@code org_scope_path_before}/{@code org_scope_path_after} 两列分别应用边界安全的
     * {@code path = prefix OR path LIKE CONCAT(prefix, '/%')} 条件，前后任一命中即可，多个
     * 前缀之间取并集。
     *
     * @param entityType 数据类型：ORG/USER/POSITION/APP/ROLE
     * @param sinceSeq   游标下限（不含），只返回 {@code change_seq > sinceSeq} 的记录
     * @param limit      本批最多查询的记录数
     * @param prefixes   组织范围路径前缀列表，{@code null}/空列表表示不限制
     * @return 本批查询结果，按 {@code change_seq} 升序排列，空列表表示已扫描到末尾
     */
    List<AppDataChangeLogEntity> selectChanges(@Param("entityType") String entityType,
            @Param("sinceSeq") Long sinceSeq, @Param("limit") int limit,
            @Param("prefixes") List<ScopePrefix> prefixes);

    /**
     * 查询当前变更流水表的最大 {@code change_seq}，供对账摘要接口返回当前水位号使用
     * （app-sync-changelog-pull change design.md Decision 10）。
     *
     * @return 最大 {@code change_seq}，表为空时返回 {@code null}
     */
    Long selectMaxChangeSeq();

    /**
     * 按变更发生时间上限、游标升序查询一批已过期的 {@code change_seq}，供保留窗口清理任务
     * 使用（app-sync-changelog-pull change design.md Decision 8）：调用方先取得这批
     * {@code change_seq} 列表，再据此删除并计算本批最大值用于推进保留窗口下界游标，避免
     * "先按 change_time 删除、再另行查询 MAX(change_seq)"两步之间因 change_time 与
     * change_seq 不完全单调（理论上罕见但不可排除）产生的偏差。
     *
     * @param cutoff 变更发生时间上限（不含边界之外，即只返回早于该时间的记录）
     * @param limit  本批最多返回的记录数
     * @return 本批过期记录的 {@code change_seq} 列表，按升序排列，空列表表示已无过期记录
     */
    List<Long> selectExpiredChangeSeqBatch(@Param("cutoff") LocalDateTime cutoff, @Param("limit") int limit);
}
