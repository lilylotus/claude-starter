package cn.nihility.rbac.sync.notify.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 通知候选应用查询数据访问接口：联表 {@code tab_app_config}/{@code tab_app}/
 * {@code tab_app_sync_domain_config} 查询"某数据类型下已启用同步（{@code sync_enabled=1}）、
 * 应用同步总开关开启（{@code sync_master_enabled=1}）、当前同步方式为通知
 * （{@code sync_mode='NOTIFY'}）、且应用当前启用（{@code status=2000}）"的候选应用 id 列表，
 * 供 {@code NotifyCandidateResolver} 判定一条领域变更事件应该直接触发哪些应用的通知
 * （app-sync-drop-changelog change design.md Decision 6）。同步方式为"拉取"的应用不需要
 * 参与候选匹配——拉取行为完全由其自行按需发起的分页查询决定，不需要"被匹配"这个概念。
 * SQL 写在 {@code NotifyTargetMapper.xml} 里（仓库既有约定：多表 JOIN 查询写在 MyBatis
 * XML，不在 Java 侧循环查询后手工合并）。
 */
@Mapper
public interface NotifyTargetMapper {

    /**
     * 查询指定数据类型下当前满足通知触发条件的候选应用 id 列表。
     *
     * @param dataType 数据类型
     * @return 候选应用 id（{@code tab_app.id}）列表
     */
    List<Long> selectCandidateAppRefIds(@Param("dataType") String dataType);
}
