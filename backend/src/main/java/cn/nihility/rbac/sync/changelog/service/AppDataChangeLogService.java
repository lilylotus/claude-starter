package cn.nihility.rbac.sync.changelog.service;

import cn.nihility.rbac.sync.changelog.entity.AppDataChangeLogEntity;
import cn.nihility.rbac.sync.event.DomainChangeEvent;
import java.util.Collection;
import java.util.List;

/**
 * 应用数据变更记录业务逻辑接口：把领域变更事件落库为变更记录，并提供拉取接口所需的
 * 两种查询（按 id / 按序列号）（app-sync-notify-pull-api change design.md Decision 6/9）。
 */
public interface AppDataChangeLogService {

    /**
     * 把一条领域变更事件落库为一条变更记录。
     *
     * @param event 领域变更事件
     * @return 落库后的变更记录实体（{@code id} 已回填，即对外序列号）
     */
    AppDataChangeLogEntity record(DomainChangeEvent event);

    /**
     * 按数据类型 + 一批变更对象 id，查询每个 id 命中的最新一条变更记录。
     *
     * @param dataType 数据类型
     * @param bizIds   变更对象 id 列表
     * @return 每个 id 最新一条变更记录列表
     */
    List<AppDataChangeLogEntity> selectLatestByBizIds(String dataType, List<Long> bizIds);

    /**
     * 按数据类型集合 + 起始序列号，升序批量查询变更记录。
     *
     * @param dataTypes    数据类型集合
     * @param fromSequence 起始序列号
     * @param limit        最多返回条数
     * @return 变更记录列表，按序列号升序排列
     */
    List<AppDataChangeLogEntity> selectBySequence(Collection<String> dataTypes, Long fromSequence, int limit);
}
