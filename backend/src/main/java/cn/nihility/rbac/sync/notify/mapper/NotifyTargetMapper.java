package cn.nihility.rbac.sync.notify.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 同步候选应用查询数据访问接口：联表 {@code tab_app_config}/{@code tab_app}/
 * {@code tab_app_sync_domain_config} 查询"某数据类型下已启用同步（{@code sync_enabled=1}）
 * 且应用当前启用（{@code status=2000}）"的候选应用 id 列表，供
 * {@code AppDataChangeLogServiceImpl#record} 落库变更记录时判定"物化给哪些应用"使用
 * （app-sync-org-scope-and-app-change-log change design.md Decision 1/5）。原名
 * {@code selectNotifyTargets} 仅服务于"通知"场景（额外过滤 {@code sync_mode='NOTIFY'}），
 * 现按候选集语义改造：去掉该过滤条件，落库改为"该数据域订阅了同步的应用都需要记录，只是
 * 其中 {@code NOTIFY} 模式的应用后续会额外触发一次 HTTP 通知"，返回内容也从"通知发送所需
 * 字段"精简为落库变更记录所需的最小信息（仅 {@code appRefId}）；通知发送所需字段改由
 * {@code AppNotifyServiceImpl} 按已确定的目标应用 id 直接查询 {@code AppConfigMapper}
 * 获取，不再需要本接口联表返回。SQL 写在 {@code NotifyTargetMapper.xml} 里（仓库既有约定：
 * 多表 JOIN 查询写在 MyBatis XML，不在 Java 侧循环查询后手工合并）。
 */
@Mapper
public interface NotifyTargetMapper {

    /**
     * 查询指定数据类型下已启用同步的候选应用 id 列表。
     *
     * @param dataType 数据类型
     * @return 候选应用 id（{@code tab_app.id}）列表
     */
    List<Long> selectCandidateAppRefIds(@Param("dataType") String dataType);
}
