package cn.nihility.rbac.sync.changelog.mapper;

import cn.nihility.rbac.sync.changelog.entity.AppDataChangeLogEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 应用数据变更记录 MyBatis-Plus 数据访问接口。单条插入直接复用 {@link BaseMapper}；
 * "按 dataType+bizIds 查每个 id 最新一条记录"、"按 sequence 游标批量查询"两个查询涉及
 * 窗口函数/动态 IN 条件，SQL 写在 {@code AppDataChangeLogMapper.xml} 里（仓库既有约定）。
 */
@Mapper
public interface AppDataChangeLogMapper extends BaseMapper<AppDataChangeLogEntity> {

    /**
     * 按应用 id + 数据类型 + 一批变更对象 id，查询每个 id 命中的最新一条变更记录
     * （app-sync-notify-pull-api change design.md Decision 9；按应用过滤见
     * app-sync-org-scope-and-app-change-log change design.md Decision 7）。
     *
     * @param appRefId 调用方应用 id（{@code tab_app.id}）
     * @param dataType 数据类型
     * @param bizIds   变更对象 id 列表
     * @return 每个 id 最新一条变更记录列表（未命中的 id 不出现在结果中）
     */
    List<AppDataChangeLogEntity> selectLatestByBizIds(@Param("appRefId") Long appRefId,
            @Param("dataType") String dataType, @Param("bizIds") List<Long> bizIds);

    /**
     * 按应用 id + 数据类型集合 + 起始序列号，升序批量查询变更记录，最多返回 {@code limit} 条
     * （app-sync-notify-pull-api change design.md Decision 9；按应用过滤见
     * app-sync-org-scope-and-app-change-log change design.md Decision 7）。
     *
     * @param appRefId     调用方应用 id（{@code tab_app.id}）
     * @param dataTypes    数据类型集合（调用方已按应用允许同步的数据域过滤好）
     * @param fromSequence 起始序列号，只返回大于该值的记录
     * @param limit        最多返回条数
     * @return 变更记录列表，按 {@code id} 升序排列
     */
    List<AppDataChangeLogEntity> selectBySequence(@Param("appRefId") Long appRefId,
            @Param("dataTypes") Collection<String> dataTypes, @Param("fromSequence") Long fromSequence,
            @Param("limit") int limit);
}
