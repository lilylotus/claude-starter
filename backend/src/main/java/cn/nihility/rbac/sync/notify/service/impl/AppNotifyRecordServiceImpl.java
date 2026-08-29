package cn.nihility.rbac.sync.notify.service.impl;

import cn.nihility.rbac.app.entity.AppEntity;
import cn.nihility.rbac.app.mapper.AppMapper;
import cn.nihility.rbac.app.sync.constant.SyncDomain;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.common.result.PageResult;
import cn.nihility.rbac.org.entity.OrgEntity;
import cn.nihility.rbac.org.mapper.OrgMapper;
import cn.nihility.rbac.role.entity.RoleEntity;
import cn.nihility.rbac.role.mapper.RoleMapper;
import cn.nihility.rbac.sync.notify.constant.NotifyTaskStatus;
import cn.nihility.rbac.sync.notify.dto.AppNotifyRecordQueryRequest;
import cn.nihility.rbac.sync.notify.dto.AppNotifyRecordVO;
import cn.nihility.rbac.sync.notify.entity.AppNotifyRecordEntity;
import cn.nihility.rbac.sync.notify.mapper.AppNotifyRecordMapper;
import cn.nihility.rbac.sync.notify.mapstruct.AppNotifyRecordConvert;
import cn.nihility.rbac.sync.notify.service.AppNotifyRecordService;
import cn.nihility.rbac.sync.notify.service.AppNotifyTaskService;
import cn.nihility.rbac.sync.notify.support.NotifySendCoordinator;
import cn.nihility.rbac.user.dto.PositionVO;
import cn.nihility.rbac.user.entity.UserEntity;
import cn.nihility.rbac.user.mapper.UserMapper;
import cn.nihility.rbac.user.mapper.UserPositionMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 通知日志查询业务逻辑实现，{@link #retryDeadTask} 额外承担管理端手动重推的编排职责
 * （tasks.md 6.3）。
 */
@Service
@RequiredArgsConstructor
public class AppNotifyRecordServiceImpl implements AppNotifyRecordService {

    /** 应用通知发送记录数据访问接口。 */
    private final AppNotifyRecordMapper appNotifyRecordMapper;

    /** 通知任务落库与状态机流转业务逻辑接口。 */
    private final AppNotifyTaskService appNotifyTaskService;

    /** 通知任务"抢占 + 发送 + 状态流转"编排组件，重置成功后触发一次即时发送优化。 */
    private final NotifySendCoordinator notifySendCoordinator;

    /** 组织批量查询依赖。 */
    private final OrgMapper orgMapper;

    /** 用户批量查询依赖。 */
    private final UserMapper userMapper;

    /** 任职名称关联批量查询依赖。 */
    private final UserPositionMapper userPositionMapper;

    /** 应用批量查询依赖。 */
    private final AppMapper appMapper;

    /** 角色批量查询依赖。 */
    private final RoleMapper roleMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResult<AppNotifyRecordVO> page(AppNotifyRecordQueryRequest request) {
        Page<AppNotifyRecordEntity> queryPage = new Page<>(request.getPage(), request.getPageSize());
        IPage<AppNotifyRecordEntity> resultPage = appNotifyRecordMapper.selectNotifyRecordPage(queryPage, request);
        List<AppNotifyRecordVO> records = AppNotifyRecordConvert.INSTANCE.toVOList(resultPage.getRecords());
        fillBizNames(records);
        return PageResult.of(records, resultPage);
    }

    /** 按数据类型分组批量解析并回填一页通知日志的业务名称。 */
    private void fillBizNames(List<AppNotifyRecordVO> records) {
        Map<String, Set<Long>> idsByType = records.stream()
                .filter(record -> record.getDataType() != null && record.getBizId() != null)
                .collect(Collectors.groupingBy(AppNotifyRecordVO::getDataType,
                        Collectors.mapping(AppNotifyRecordVO::getBizId, Collectors.toSet())));
        Map<String, Map<Long, String>> namesByType = new HashMap<>();
        putEntityNames(namesByType, SyncDomain.ORG, idsByType.get(SyncDomain.ORG), orgMapper::selectByIds,
                OrgEntity::getId, OrgEntity::getName);
        putEntityNames(namesByType, SyncDomain.USER, idsByType.get(SyncDomain.USER), userMapper::selectByIds,
                UserEntity::getId, UserEntity::getName);
        putPositionNames(namesByType, idsByType.get(SyncDomain.POSITION));
        putEntityNames(namesByType, SyncDomain.APP, idsByType.get(SyncDomain.APP), appMapper::selectByIds,
                AppEntity::getId, AppEntity::getName);
        putEntityNames(namesByType, SyncDomain.ROLE, idsByType.get(SyncDomain.ROLE), roleMapper::selectByIds,
                RoleEntity::getId, RoleEntity::getName);
        for (AppNotifyRecordVO record : records) {
            Map<Long, String> names = namesByType.get(record.getDataType());
            if (names != null && record.getBizId() != null) {
                record.setBizName(names.get(record.getBizId()));
            }
        }
    }

    /** 批量查询一种普通实体并构造 id 到名称的映射。 */
    private <T> void putEntityNames(Map<String, Map<Long, String>> namesByType, String dataType, Set<Long> ids,
            Function<Set<Long>, List<T>> query, Function<T, Long> idGetter, Function<T, String> nameGetter) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        Map<Long, String> names = new HashMap<>();
        for (T entity : query.apply(ids)) {
            if (entity != null && idGetter.apply(entity) != null) {
                String name = normalizeName(nameGetter.apply(entity));
                if (name != null) {
                    names.putIfAbsent(idGetter.apply(entity), name);
                }
            }
        }
        namesByType.put(dataType, names);
    }

    /** 批量解析任职名称，用户和组织任一缺失时保留仍可获得的部分。 */
    private void putPositionNames(Map<String, Map<Long, String>> namesByType, Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        Map<Long, String> names = new HashMap<>();
        for (PositionVO position : userPositionMapper.selectPositionNamesByIds(ids)) {
            if (position != null && position.getId() != null) {
                String name = joinPositionName(position);
                if (name != null) {
                    names.putIfAbsent(position.getId(), name);
                }
            }
        }
        namesByType.put(SyncDomain.POSITION, names);
    }

    /** 组合任职用户名称与组织名称。 */
    private String joinPositionName(PositionVO position) {
        String userName = normalizeName(position.getUserName());
        String orgName = normalizeName(position.getOrgName());
        if (userName != null && orgName != null) {
            return userName + "-" + orgName;
        }
        return userName != null ? userName : orgName;
    }

    /** 把空串名称归一化为空。 */
    private String normalizeName(String name) {
        return name == null || name.isBlank() ? null : name;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void retryDeadTask(Long appRefId, Long recordId) {
        AppNotifyRecordEntity record = appNotifyTaskService.getById(recordId);
        if (record == null || !record.getAppRefId().equals(appRefId)) {
            throw new BusinessException("通知记录不存在：id=" + recordId);
        }
        if (!NotifyTaskStatus.DEAD.equals(record.getTaskStatus())) {
            throw new BusinessException("通知记录当前不是死信状态，无法重推：id=" + recordId + ", taskStatus="
                    + record.getTaskStatus());
        }
        if (!appNotifyTaskService.resetDeadToPending(recordId)) {
            throw new BusinessException("通知记录状态已发生变化，重推失败，请刷新后重试：id=" + recordId);
        }
        AppNotifyRecordEntity resetRecord = appNotifyTaskService.getById(recordId);
        if (resetRecord != null) {
            notifySendCoordinator.submitImmediateSend(resetRecord);
        }
    }
}
