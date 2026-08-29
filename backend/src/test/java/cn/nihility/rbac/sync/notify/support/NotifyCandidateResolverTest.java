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
import cn.nihility.rbac.sync.scope.ScopePrefix;
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

    private NotifyCandidateResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new NotifyCandidateResolver(notifyTargetMapper, appSyncOrgScopeResolver);
    }

    /**
     * 候选应用为空时应直接返回空列表，不做任何组织范围过滤。
     */
    @Test
    void resolveCandidateAppRefIds_shouldReturnEmpty_whenNoCandidates() {
        when(notifyTargetMapper.selectCandidateAppRefIds(SyncDomain.ORG)).thenReturn(List.of());

        List<Long> result = resolver.resolveCandidateAppRefIds(sampleEvent(SyncDomain.ORG, 1L));

        assertThat(result).isEmpty();
        verify(appSyncOrgScopeResolver, never()).resolveScopePrefixes(any(), any());
    }

    /**
     * ORG 数据域应按变更对象自身 id 校验每个候选应用的组织范围，不落在范围内的应用被剔除。
     */
    @Test
    void resolveCandidateAppRefIds_shouldMatchOrgBeforeOrAfterPathWithBoundary() {
        when(notifyTargetMapper.selectCandidateAppRefIds(SyncDomain.ORG)).thenReturn(List.of(1L, 2L));
        when(appSyncOrgScopeResolver.resolveScopePrefixes(1L, SyncDomain.ORG))
                .thenReturn(List.of(new ScopePrefix("1/12", true)));
        when(appSyncOrgScopeResolver.resolveScopePrefixes(2L, SyncDomain.ORG))
                .thenReturn(List.of(new ScopePrefix("1/123", true)));
        DomainChangeEvent event = sampleEvent(SyncDomain.ORG, 10L).toBuilder()
                .orgScopePathBefore("1/12/10").orgScopePathAfter("9/10").build();

        List<Long> result = resolver.resolveCandidateAppRefIds(event);

        assertThat(result).containsExactly(1L);
    }

    /**
     * POSITION 数据域应按任职记录归属的组织 id（而非任职记录自身 id）校验组织范围。
     */
    @Test
    void resolveCandidateAppRefIds_shouldUseBeforePathForPhysicallyDeletedPosition() {
        when(notifyTargetMapper.selectCandidateAppRefIds(SyncDomain.POSITION)).thenReturn(List.of(1L));
        when(appSyncOrgScopeResolver.resolveScopePrefixes(1L, SyncDomain.POSITION))
                .thenReturn(List.of(new ScopePrefix("1/20", false)));
        DomainChangeEvent event = sampleEvent(SyncDomain.POSITION, 50L).toBuilder()
                .operationType(OperationType.DELETE).orgScopePathBefore("1/20").build();

        List<Long> result = resolver.resolveCandidateAppRefIds(event);

        assertThat(result).containsExactly(1L);
    }

    /**
     * POSITION 数据域对应的任职记录查不到时，应保守返回空列表，不给任何候选应用触发通知。
     */
    @Test
    void resolveCandidateAppRefIds_shouldReturnEmpty_whenScopeConfiguredButEventPathsMissing() {
        when(notifyTargetMapper.selectCandidateAppRefIds(SyncDomain.POSITION)).thenReturn(List.of(1L));
        when(appSyncOrgScopeResolver.resolveScopePrefixes(1L, SyncDomain.POSITION))
                .thenReturn(List.of(new ScopePrefix("1/20", true)));

        List<Long> result = resolver.resolveCandidateAppRefIds(sampleEvent(SyncDomain.POSITION, 99L));

        assertThat(result).isEmpty();
    }

    /** 未配置范围时 ORG/POSITION 不受路径缺失影响。 */
    @Test
    void resolveCandidateAppRefIds_shouldKeepCandidate_whenScopeIsUnrestricted() {
        when(notifyTargetMapper.selectCandidateAppRefIds(SyncDomain.ORG)).thenReturn(List.of(1L));
        when(appSyncOrgScopeResolver.resolveScopePrefixes(1L, SyncDomain.ORG)).thenReturn(List.of());

        assertThat(resolver.resolveCandidateAppRefIds(sampleEvent(SyncDomain.ORG, 10L))).containsExactly(1L);
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
        verify(appSyncOrgScopeResolver, never()).resolveScopePrefixes(any(), eq(SyncDomain.APP));
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
