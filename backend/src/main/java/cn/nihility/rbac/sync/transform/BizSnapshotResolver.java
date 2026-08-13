package cn.nihility.rbac.sync.transform;

import cn.nihility.rbac.app.mapper.AppMapper;
import cn.nihility.rbac.app.sync.constant.SyncDomain;
import cn.nihility.rbac.org.mapper.OrgMapper;
import cn.nihility.rbac.role.mapper.RoleMapper;
import cn.nihility.rbac.sync.event.DomainSnapshotSupport;
import cn.nihility.rbac.user.mapper.UserMapper;
import cn.nihility.rbac.user.mapper.UserPositionMapper;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 业务对象当前快照解析器（fix-app-sync-pull-live-data change design.md Decision 1）：
 * 按 {@code dataType} 分发到组织/用户/任职/应用/角色对应业务表的 {@code selectById}，
 * 取代拉取接口原先直接解析 {@code tab_app_data_change_log.data_snapshot}
 * （变更发生那一刻冻结的历史快照）的做法，让拉取接口返回的 {@code data} 反映业务表的
 * 当前真实状态。单独抽成组件而不是塞进 {@code SyncPullServiceImpl}，是为了不让该类
 * 同时承担"拉取编排逻辑"和"业务表访问细节"两种职责，也便于单测只 mock 本组件。
 */
@Component
@RequiredArgsConstructor
public class BizSnapshotResolver {

    /** 组织业务表数据访问接口。 */
    private final OrgMapper orgMapper;

    /** 用户业务表数据访问接口。 */
    private final UserMapper userMapper;

    /** 用户任职记录业务表数据访问接口。 */
    private final UserPositionMapper userPositionMapper;

    /** 应用业务表数据访问接口。 */
    private final AppMapper appMapper;

    /** 角色业务表数据访问接口。 */
    private final RoleMapper roleMapper;

    /**
     * 按数据域和业务主键现查对应业务表，返回该行当前的字段快照。
     *
     * @param dataType 数据域，取值范围为 {@link SyncDomain#CHANGE_LOG_DOMAINS}
     *                 （ORG/USER/POSITION/APP/ROLE），由调用方（{@code SyncPullServiceImpl
     *                 #assertValidDataType}）保证合法，{@code default} 分支仅作穷尽性兜底，
     *                 不会在正常业务路径上被触发
     * @param bizId    业务主键 id
     * @return 该行当前的字段快照 Map（key 为实体属性名），业务表查不到该行时返回
     *         {@code null}（四张业务表均为逻辑删除，理论上不会物理消失，此处仅作防御性兜底）
     */
    public Map<String, Object> resolve(String dataType, Long bizId) {
        Object entity = switch (dataType) {
            case SyncDomain.ORG -> orgMapper.selectById(bizId);
            case SyncDomain.USER -> userMapper.selectById(bizId);
            case SyncDomain.POSITION -> userPositionMapper.selectById(bizId);
            case SyncDomain.APP -> appMapper.selectById(bizId);
            case SyncDomain.ROLE -> roleMapper.selectById(bizId);
            default -> null;
        };
        return entity == null ? null : DomainSnapshotSupport.snapshot(entity);
    }
}
