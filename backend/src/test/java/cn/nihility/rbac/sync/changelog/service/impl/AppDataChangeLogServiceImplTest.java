package cn.nihility.rbac.sync.changelog.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.app.sync.constant.SyncDomain;
import cn.nihility.rbac.operationlog.constant.OperationType;
import cn.nihility.rbac.sync.changelog.entity.AppDataChangeLogEntity;
import cn.nihility.rbac.sync.changelog.mapper.AppDataChangeLogMapper;
import cn.nihility.rbac.sync.event.DomainChangeEvent;
import cn.nihility.rbac.sync.notify.mapper.NotifyTargetMapper;
import cn.nihility.rbac.sync.scope.AppSyncOrgScopeResolver;
import cn.nihility.rbac.user.entity.UserPositionEntity;
import cn.nihility.rbac.user.mapper.UserPositionMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link AppDataChangeLogServiceImpl} 的单元测试，重点覆盖候选应用为空时不落库、
 * ORG/USER/POSITION 数据域按应用同步范围过滤、APP/ROLE 数据域不做范围过滤，以及按应用
 * 过滤的查询方法委托（app-sync-org-scope-and-app-change-log change design.md
 * Decision 1/4/7）。
 */
@ExtendWith(MockitoExtension.class)
class AppDataChangeLogServiceImplTest {

    /** 被测服务的应用数据变更记录数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private AppDataChangeLogMapper appDataChangeLogMapper;

    /** 被测服务的同步候选应用查询依赖，使用 Mockito 打桩。 */
    @Mock
    private NotifyTargetMapper notifyTargetMapper;

    /** 被测服务的应用同步组织范围解析依赖，使用 Mockito 打桩。 */
    @Mock
    private AppSyncOrgScopeResolver appSyncOrgScopeResolver;

    /** 被测服务的用户任职记录数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private UserPositionMapper userPositionMapper;

    /** 被测服务实例。 */
    private AppDataChangeLogServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AppDataChangeLogServiceImpl(appDataChangeLogMapper, notifyTargetMapper,
                appSyncOrgScopeResolver, userPositionMapper);
    }

    /**
     * 候选应用为空时应直接返回空列表，不落库任何记录。
     */
    @Test
    void record_shouldReturnEmpty_whenNoCandidateApps() {
        when(notifyTargetMapper.selectCandidateAppRefIds(SyncDomain.ORG)).thenReturn(List.of());

        List<AppDataChangeLogEntity> result = service.record(sampleEvent(SyncDomain.ORG, 88L));

        assertThat(result).isEmpty();
        verify(appDataChangeLogMapper, never()).insert(any(AppDataChangeLogEntity.class));
    }

    /**
     * {@code APP}/{@code ROLE} 数据域不做组织范围过滤，全部候选应用各落库一条记录。
     */
    @Test
    void record_shouldInsertForAllCandidates_whenDomainNotOrgScoped() {
        when(notifyTargetMapper.selectCandidateAppRefIds(SyncDomain.APP)).thenReturn(List.of(1L, 2L));

        List<AppDataChangeLogEntity> result = service.record(sampleEvent(SyncDomain.APP, 88L));

        assertThat(result).hasSize(2);
        ArgumentCaptor<AppDataChangeLogEntity> captor = ArgumentCaptor.forClass(AppDataChangeLogEntity.class);
        verify(appDataChangeLogMapper, times(2)).insert(captor.capture());
        assertThat(captor.getAllValues()).extracting(AppDataChangeLogEntity::getAppRefId)
                .containsExactlyInAnyOrder(1L, 2L);
        assertThat(captor.getAllValues()).allSatisfy(entity -> {
            assertThat(entity.getDataType()).isEqualTo(SyncDomain.APP);
            assertThat(entity.getBizId()).isEqualTo(88L);
        });
    }

    /**
     * {@code ORG} 数据域按 {@code bizId}（组织自身 id）过滤候选应用，落库范围外的应用应被剔除。
     */
    @Test
    void record_shouldFilterByOrgScope_whenDomainIsOrg() {
        when(notifyTargetMapper.selectCandidateAppRefIds(SyncDomain.ORG)).thenReturn(List.of(1L, 2L));
        when(appSyncOrgScopeResolver.isOrgIdWithinScope(1L, SyncDomain.ORG, 88L)).thenReturn(true);
        when(appSyncOrgScopeResolver.isOrgIdWithinScope(2L, SyncDomain.ORG, 88L)).thenReturn(false);

        List<AppDataChangeLogEntity> result = service.record(sampleEvent(SyncDomain.ORG, 88L));

        assertThat(result).hasSize(1);
        ArgumentCaptor<AppDataChangeLogEntity> captor = ArgumentCaptor.forClass(AppDataChangeLogEntity.class);
        verify(appDataChangeLogMapper, times(1)).insert(captor.capture());
        assertThat(captor.getValue().getAppRefId()).isEqualTo(1L);
    }

    /**
     * {@code POSITION} 数据域先按 {@code bizId}（任职记录 id）反查所属组织 id，再按该组织 id
     * 过滤候选应用。
     */
    @Test
    void record_shouldFilterByOrgScope_whenDomainIsPosition() {
        when(notifyTargetMapper.selectCandidateAppRefIds(SyncDomain.POSITION)).thenReturn(List.of(1L, 2L));
        when(userPositionMapper.selectById(50L)).thenReturn(UserPositionEntity.builder().id(50L).orgId(10L).build());
        when(appSyncOrgScopeResolver.isOrgIdWithinScope(1L, SyncDomain.POSITION, 10L)).thenReturn(true);
        when(appSyncOrgScopeResolver.isOrgIdWithinScope(2L, SyncDomain.POSITION, 10L)).thenReturn(false);

        List<AppDataChangeLogEntity> result = service.record(sampleEvent(SyncDomain.POSITION, 50L));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAppRefId()).isEqualTo(1L);
    }

    /**
     * {@code POSITION} 数据域对应的任职记录已查不到时，应保守处理为不匹配任何候选应用
     * （design.md Decision 4）。
     */
    @Test
    void record_shouldReturnEmpty_whenPositionNotFound() {
        when(notifyTargetMapper.selectCandidateAppRefIds(SyncDomain.POSITION)).thenReturn(List.of(1L));
        when(userPositionMapper.selectById(50L)).thenReturn(null);

        List<AppDataChangeLogEntity> result = service.record(sampleEvent(SyncDomain.POSITION, 50L));

        assertThat(result).isEmpty();
        verify(appDataChangeLogMapper, never()).insert(any(AppDataChangeLogEntity.class));
        verify(appSyncOrgScopeResolver, never()).isOrgIdWithinScope(anyLong(), any(), anyLong());
    }

    /**
     * {@code USER} 数据域按 {@code bizId}（用户 id）委托 {@code isUserWithinScope} 判断，
     * 过滤候选应用。
     */
    @Test
    void record_shouldFilterByOrgScope_whenDomainIsUser() {
        when(notifyTargetMapper.selectCandidateAppRefIds(SyncDomain.USER)).thenReturn(List.of(1L, 2L));
        when(appSyncOrgScopeResolver.isUserWithinScope(1L, 88L)).thenReturn(true);
        when(appSyncOrgScopeResolver.isUserWithinScope(2L, 88L)).thenReturn(false);

        List<AppDataChangeLogEntity> result = service.record(sampleEvent(SyncDomain.USER, 88L));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAppRefId()).isEqualTo(1L);
    }

    /**
     * {@code bizIds} 为空时应直接返回空列表，不查询数据库。
     */
    @Test
    void selectLatestByBizIds_shouldReturnEmptyWithoutQueryWhenBizIdsEmpty() {
        List<AppDataChangeLogEntity> result = service.selectLatestByBizIds(1L, SyncDomain.ORG, List.of());

        assertThat(result).isEmpty();
        verify(appDataChangeLogMapper, never()).selectLatestByBizIds(any(), any(), any());
    }

    /**
     * {@code bizIds} 非空时应把 {@code appRefId} 一并传给 Mapper 查询。
     */
    @Test
    void selectLatestByBizIds_shouldDelegateToMapperWithAppRefId() {
        when(appDataChangeLogMapper.selectLatestByBizIds(1L, SyncDomain.ORG, List.of(1L, 2L)))
                .thenReturn(List.of(AppDataChangeLogEntity.builder().id(10L).appRefId(1L).build()));

        List<AppDataChangeLogEntity> result = service.selectLatestByBizIds(1L, SyncDomain.ORG, List.of(1L, 2L));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(10L);
    }

    /**
     * 数据类型集合为空时应直接返回空列表，不查询数据库。
     */
    @Test
    void selectBySequence_shouldReturnEmptyWithoutQueryWhenDataTypesEmpty() {
        List<AppDataChangeLogEntity> result = service.selectBySequence(1L, List.of(), 100L, 20);

        assertThat(result).isEmpty();
        verify(appDataChangeLogMapper, never()).selectBySequence(any(), any(), any(), anyInt());
    }

    /**
     * 构造一个示例领域变更事件。
     *
     * @param dataType 数据类型
     * @param bizId    变更对象主键 id
     * @return 示例事件
     */
    private DomainChangeEvent sampleEvent(String dataType, Long bizId) {
        return DomainChangeEvent.builder()
                .dataType(dataType)
                .bizId(bizId)
                .operationType(OperationType.CREATE)
                .operator("1")
                .occurredAt(LocalDateTime.now())
                .build();
    }
}
