package cn.nihility.rbac.sync.event.support;

import cn.nihility.rbac.app.mapper.AppMapper;
import cn.nihility.rbac.app.sync.constant.SyncDomain;
import cn.nihility.rbac.dict.mapper.DictItemMapper;
import cn.nihility.rbac.org.mapper.OrgMapper;
import cn.nihility.rbac.role.mapper.RoleMapper;
import cn.nihility.rbac.sync.event.DomainChangeEvent;
import cn.nihility.rbac.user.mapper.UserMapper;
import cn.nihility.rbac.user.mapper.UserPositionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 在事务提交后从业务表解析事件对应的最终实体版本。 */
@Component
@RequiredArgsConstructor
public class DomainEntityVersionResolver {

    private final OrgMapper orgMapper;
    private final UserMapper userMapper;
    private final UserPositionMapper userPositionMapper;
    private final AppMapper appMapper;
    private final RoleMapper roleMapper;
    private final DictItemMapper dictItemMapper;

    /** 返回事件已携带的版本，或从对应业务表读取当前版本。 */
    public Long resolve(DomainChangeEvent event) {
        if (event.getEntityVersion() != null) {
            return event.getEntityVersion();
        }
        return switch (event.getDataType()) {
            case SyncDomain.ORG -> orgMapper.selectById(event.getBizId()).getVersion();
            case SyncDomain.USER -> userMapper.selectById(event.getBizId()).getVersion();
            case SyncDomain.POSITION -> userPositionMapper.selectById(event.getBizId()).getVersion();
            case SyncDomain.APP -> appMapper.selectById(event.getBizId()).getVersion();
            case SyncDomain.ROLE -> roleMapper.selectById(event.getBizId()).getVersion();
            case SyncDomain.DICT -> dictItemMapper.selectById(event.getBizId()).getVersion();
            default -> throw new IllegalArgumentException("不支持版本解析的数据域: " + event.getDataType());
        };
    }
}
