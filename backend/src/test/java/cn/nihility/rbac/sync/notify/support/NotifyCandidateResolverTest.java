package cn.nihility.rbac.sync.notify.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.app.sync.constant.SyncDomain;
import cn.nihility.rbac.operationlog.constant.OperationType;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link NotifyCandidateResolver} 的单元测试，覆盖候选应用为空时提前返回、ORG/POSITION/USER
 * 三个数据域的组织范围过滤、APP/ROLE 数据域不过滤、任职记录查不到时保守返回空列表
 * （app-sync-drop-changelog change design.md Decision 6，组织范围过滤逻辑原样迁移自原
 * {@code AppDataChangeLogServiceImpl.filterByOrgScope}）。
 */
@ExtendWith(MockitoExtension.class)
class NotifyCandidateResolverTest {

    @Mock
    private NotifyTargetMapper notifyTargetMapper;

    @Mock
    private AppSyncOrgScopeResolver appSyncOrgScopeResolver;

    @Mock
    private UserPositionMapper userPositionMapper;

    private NotifyCandidateResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new NotifyCandidateResolver(notifyTargetMapper, appSyncOrgScopeResolver, userPositionMapper);
    }

    /**
     * 候选应用为空时应直接返回空列表，不做任何组织范围过滤。
     */
    @Test
    void resolveCandidateAppRefIds_shouldReturnEmpty_whenNoCandidates() {
        when(notifyTargetMapper.selectCandidateAppRefIds(SyncDomain.ORG)).thenReturn(List.of());

        List<Long> result = resolver.resolveCandidateAppRefIds(sampleEvent(SyncDomain.ORG, 1L));

        assertThat(result).isEmpty();
        verify(appSyncOrgScopeResolver, never()).isOrgIdWithinScope(any(), any(), any());
    }

    /**
     * ORG 数据域应按变更对象自身 id 校验每个候选应用的组织范围，不落在范围内的应用被剔除。
     */
    @Test
    void resolveCandidateAppRefIds_shouldFilterOrgDomainByOrgScope() {
        when(notifyTargetMapper.selectCandidateAppRefIds(SyncDomain.ORG)).thenReturn(List.of(1L, 2L));
        when(appSyncOrgScopeResolver.isOrgIdWithinScope(1L, SyncDomain.ORG, 10L)).thenReturn(true);
        when(appSyncOrgScopeResolver.isOrgIdWithinScope(2L, SyncDomain.ORG, 10L)).thenReturn(false);

        List<Long> result = resolver.resolveCandidateAppRefIds(sampleEvent(SyncDomain.ORG, 10L));

        assertThat(result).containsExactly(1L);
    }

    /**
     * POSITION 数据域应按任职记录归属的组织 id（而非任职记录自身 id）校验组织范围。
     */
    @Test
    void resolveCandidateAppRefIds_shouldFilterPositionDomainByOwningOrg() {
        when(notifyTargetMapper.selectCandidateAppRefIds(SyncDomain.POSITION)).thenReturn(List.of(1L));
        when(userPositionMapper.selectById(50L)).thenReturn(UserPositionEntity.builder().id(50L).orgId(20L).build());
        when(appSyncOrgScopeResolver.isOrgIdWithinScope(1L, SyncDomain.POSITION, 20L)).thenReturn(true);

        List<Long> result = resolver.resolveCandidateAppRefIds(sampleEvent(SyncDomain.POSITION, 50L));

        assertThat(result).containsExactly(1L);
    }

    /**
     * POSITION 数据域对应的任职记录查不到时，应保守返回空列表，不给任何候选应用触发通知。
     */
    @Test
    void resolveCandidateAppRefIds_shouldReturnEmpty_whenPositionNotFound() {
        when(notifyTargetMapper.selectCandidateAppRefIds(SyncDomain.POSITION)).thenReturn(List.of(1L));
        when(userPositionMapper.selectById(99L)).thenReturn(null);

        List<Long> result = resolver.resolveCandidateAppRefIds(sampleEvent(SyncDomain.POSITION, 99L));

        assertThat(result).isEmpty();
    }

    /**
     * USER 数据域应委托 {@link AppSyncOrgScopeResolver#isUserWithinScope} 判断。
     */
    @Test
    void resolveCandidateAppRefIds_shouldFilterUserDomainByUserScope() {
        when(notifyTargetMapper.selectCandidateAppRefIds(SyncDomain.USER)).thenReturn(List.of(1L, 2L));
        when(appSyncOrgScopeResolver.isUserWithinScope(1L, 100L)).thenReturn(true);
        when(appSyncOrgScopeResolver.isUserWithinScope(2L, 100L)).thenReturn(false);

        List<Long> result = resolver.resolveCandidateAppRefIds(sampleEvent(SyncDomain.USER, 100L));

        assertThat(result).containsExactly(1L);
    }

    /**
     * APP/ROLE 数据域不做组织范围过滤，候选应用列表原样保留。
     */
    @Test
    void resolveCandidateAppRefIds_shouldNotFilterAppDomain() {
        when(notifyTargetMapper.selectCandidateAppRefIds(SyncDomain.APP)).thenReturn(List.of(1L, 2L));

        List<Long> result = resolver.resolveCandidateAppRefIds(sampleEvent(SyncDomain.APP, 200L));

        assertThat(result).containsExactlyInAnyOrder(1L, 2L);
        verify(appSyncOrgScopeResolver, never()).isOrgIdWithinScope(any(), eq(SyncDomain.APP), any());
    }

    /**
     * 构造一个示例领域变更事件。
     *
     * @param dataType 数据类型
     * @param bizId    变更对象 id
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
