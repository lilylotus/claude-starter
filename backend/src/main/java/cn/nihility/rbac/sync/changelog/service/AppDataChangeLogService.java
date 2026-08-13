package cn.nihility.rbac.sync.changelog.service;

import cn.nihility.rbac.sync.changelog.entity.AppDataChangeLogEntity;
import cn.nihility.rbac.sync.event.DomainChangeEvent;
import java.util.Collection;
import java.util.List;

/**
 * 应用数据变更记录业务逻辑接口：把领域变更事件按候选应用（组织/用户/任职三个数据域再按
 * 应用同步范围过滤）物化为多条变更记录，并提供拉取接口所需的两种查询（按 id / 按序列号）
 * （app-sync-notify-pull-api change design.md Decision 6/9；按应用物化见
 * app-sync-org-scope-and-app-change-log change design.md Decision 1）。
 */
public interface AppDataChangeLogService {

    /**
     * 把一条领域变更事件物化为多条变更记录：查询该 {@code dataType} 已启用同步的候选应用
     * 列表，若为 ORG/USER/POSITION 数据域再按每个候选应用配置的同步范围过滤一遍，为每个
     * 最终匹配的应用各插入一条变更记录（{@code appRefId} 为该应用 id）。
     *
     * @param event 领域变更事件
     * @return 落库后的变更记录实体列表（{@code id} 均已回填，即对外序列号），候选应用为空
     *         或全部被组织范围过滤掉时返回空列表
     */
    List<AppDataChangeLogEntity> record(DomainChangeEvent event);

    /**
     * 按应用 id + 数据类型 + 一批变更对象 id，查询每个 id 命中的最新一条变更记录。
     *
     * @param appRefId 调用方应用 id（{@code tab_app.id}）
     * @param dataType 数据类型
     * @param bizIds   变更对象 id 列表
     * @return 每个 id 最新一条变更记录列表
     */
    List<AppDataChangeLogEntity> selectLatestByBizIds(Long appRefId, String dataType, List<Long> bizIds);

    /**
     * 按应用 id + 数据类型集合 + 起始序列号，升序批量查询变更记录。
     *
     * @param appRefId     调用方应用 id（{@code tab_app.id}）
     * @param dataTypes    数据类型集合
     * @param fromSequence 起始序列号
     * @param limit        最多返回条数
     * @return 变更记录列表，按序列号升序排列
     */
    List<AppDataChangeLogEntity> selectBySequence(Long appRefId, Collection<String> dataTypes, Long fromSequence,
            int limit);
}
