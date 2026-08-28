package cn.nihility.rbac.sync.changelog.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.app.sync.constant.SyncDomain;
import cn.nihility.rbac.operationlog.constant.OperationType;
import cn.nihility.rbac.sync.changelog.entity.AppDataChangeLogEntity;
import cn.nihility.rbac.sync.changelog.mapper.AppDataChangeLogMapper;
import cn.nihility.rbac.sync.changelog.service.AppSyncMetadataService;
import cn.nihility.rbac.sync.event.DomainChangeEvent;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link AppDataChangeLogServiceImpl} 的单元测试，重点覆盖领域变更事件到流水实体的字段
 * 映射（雪花 {@code eventId} 原样透传、数据库自增 {@code changeSeq} 由 mapper 插入后回填、
 * 操作类型码值到对外字符串编码的转换）、非法操作类型码值拒绝，"插入后携带的
 * {@code entityType}/{@code changeSeq} 可用于按数据域 + 游标范围查询"这一契约
 * （app-sync-changelog-pull change design.md Decision 1，tasks.md 3.1），以及保留窗口清理
 * 批次操作：删除本批过期记录并原子推进 floor 为本批最大 {@code changeSeq}、空批次不触发
 * 任何删除/推进（tasks.md 3.3）。
 */
@ExtendWith(MockitoExtension.class)
class AppDataChangeLogServiceImplTest {

    /** 被测服务的全局变更流水数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private AppDataChangeLogMapper mapper;

    /** 被测服务的保留窗口下界游标依赖，使用 Mockito 打桩。 */
    @Mock
    private AppSyncMetadataService appSyncMetadataService;

    /** 被测服务实例。 */
    private AppDataChangeLogServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AppDataChangeLogServiceImpl(mapper, appSyncMetadataService);
        // 模拟 MyBatis-Plus insert 后由数据库自增列回填主键（changeSeq）的行为，与真实
        // AUTO_INCREMENT 主键场景一致；lenient 是因为"非法操作类型码值"用例在到达 insert
        // 之前就已经抛出异常，不会消费这条打桩。
        lenient().doAnswer(invocation -> {
            AppDataChangeLogEntity entity = invocation.getArgument(0);
            entity.setChangeSeq(100L);
            return 1;
        }).when(mapper).insert(org.mockito.ArgumentMatchers.any(AppDataChangeLogEntity.class));
    }

    /**
     * 正常场景下应把事件字段原样映射到流水实体并插入，插入后返回的实体携带数据库自增
     * 回填的 {@code changeSeq}，与传入的 {@code entityType} 一起构成"按数据域 + 游标范围
     * 查询"（{@code entityType = ? AND changeSeq > ?}）所需的两个关键字段。
     */
    @Test
    void append_shouldMapEventFieldsAndReturnEntityWithChangeSeq() {
        LocalDateTime occurredAt = LocalDateTime.of(2026, 1, 1, 10, 0);
        DomainChangeEvent event = DomainChangeEvent.builder()
                .eventId(123456789L)
                .entityVersion(3L)
                .dataType(SyncDomain.ORG)
                .bizId(1L)
                .operationType(OperationType.UPDATE)
                .operator("1")
                .orgScopePathBefore("1/2")
                .orgScopePathAfter("1/3")
                .occurredAt(occurredAt)
                .build();

        AppDataChangeLogEntity result = service.append(event);

        ArgumentCaptor<AppDataChangeLogEntity> captor = ArgumentCaptor.forClass(AppDataChangeLogEntity.class);
        verify(mapper).insert(captor.capture());
        AppDataChangeLogEntity inserted = captor.getValue();
        assertThat(inserted.getEventId()).isEqualTo(123456789L);
        assertThat(inserted.getEntityType()).isEqualTo(SyncDomain.ORG);
        assertThat(inserted.getEntityId()).isEqualTo(1L);
        assertThat(inserted.getOperationType()).isEqualTo("UPDATE");
        assertThat(inserted.getEntityVersion()).isEqualTo(3L);
        assertThat(inserted.getOrgScopePathBefore()).isEqualTo("1/2");
        assertThat(inserted.getOrgScopePathAfter()).isEqualTo("1/3");
        assertThat(inserted.getChangeTime()).isEqualTo(occurredAt);
        assertThat(inserted.getCreateBy()).isEqualTo("1");
        assertThat(inserted.getUpdateBy()).isEqualTo("1");

        // 插入后回填的 changeSeq 与 entityType 一起构成"按数据域 + 游标范围查询"的关键字段。
        assertThat(result.getChangeSeq()).isEqualTo(100L);
        assertThat(result.getEntityType()).isEqualTo(SyncDomain.ORG);
    }

    /**
     * CREATE/UPDATE/ENABLE/DISABLE/DELETE 五个操作类型码值应分别映射为对应的对外英文编码。
     */
    @Test
    void append_shouldMapAllOperationTypesToCorrespondingCode() {
        assertThat(appendWithOperationType(OperationType.CREATE).getOperationType()).isEqualTo("CREATE");
        assertThat(appendWithOperationType(OperationType.UPDATE).getOperationType()).isEqualTo("UPDATE");
        assertThat(appendWithOperationType(OperationType.ENABLE).getOperationType()).isEqualTo("ENABLE");
        assertThat(appendWithOperationType(OperationType.DISABLE).getOperationType()).isEqualTo("DISABLE");
        assertThat(appendWithOperationType(OperationType.DELETE).getOperationType()).isEqualTo("DELETE");
    }

    /**
     * 非法的操作类型码值应直接拒绝，不写入一条语义不明的流水记录。
     */
    @Test
    void append_shouldThrowIllegalArgumentException_whenOperationTypeInvalid() {
        DomainChangeEvent event = DomainChangeEvent.builder()
                .eventId(1L)
                .entityVersion(1L)
                .dataType(SyncDomain.ORG)
                .bizId(1L)
                .operationType(999)
                .operator("1")
                .occurredAt(LocalDateTime.now())
                .build();

        assertThatThrownBy(() -> service.append(event)).isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 有过期记录时应删除本批（按查到的 {@code changeSeq} 列表精确删除）并把保留窗口下界
     * 游标推进为本批最大 {@code changeSeq}。
     */
    @Test
    void cleanupExpiredBatch_shouldDeleteBatchAndAdvanceFloor_whenExpiredRecordsExist() {
        LocalDateTime cutoff = LocalDateTime.of(2026, 1, 1, 0, 0);
        when(mapper.selectExpiredChangeSeqBatch(cutoff, 500)).thenReturn(List.of(5L, 8L, 3L));
        when(mapper.delete(org.mockito.ArgumentMatchers.any())).thenReturn(3);

        int deleted = service.cleanupExpiredBatch(cutoff, 500);

        assertThat(deleted).isEqualTo(3);
        verify(mapper).delete(org.mockito.ArgumentMatchers.any());
        // change_seq 允许空洞（8 不是列表长度对应的最大下标，而是数值最大值），floor 应推进
        // 为本批实际删除的最大值 8，而不是按查询顺序取最后一个。
        verify(appSyncMetadataService).advanceRetentionFloorSeq(8L);
    }

    /**
     * 空表/无过期记录时应直接返回 0，不触发任何删除或 floor 推进（tasks.md 3.3）。
     */
    @Test
    void cleanupExpiredBatch_shouldReturnZero_whenNoExpiredRecords() {
        LocalDateTime cutoff = LocalDateTime.of(2026, 1, 1, 0, 0);
        when(mapper.selectExpiredChangeSeqBatch(cutoff, 500)).thenReturn(List.of());

        int deleted = service.cleanupExpiredBatch(cutoff, 500);

        assertThat(deleted).isZero();
        verify(mapper, never()).delete(org.mockito.ArgumentMatchers.any());
        verify(appSyncMetadataService, never()).advanceRetentionFloorSeq(org.mockito.ArgumentMatchers.anyLong());
    }

    /**
     * 构造一条指定操作类型的示例事件并调用 {@code append}，返回插入后的流水实体。
     *
     * @param operationType 操作类型码值
     * @return 插入后的流水实体
     */
    private AppDataChangeLogEntity appendWithOperationType(int operationType) {
        DomainChangeEvent event = DomainChangeEvent.builder()
                .eventId(1L)
                .entityVersion(1L)
                .dataType(SyncDomain.ORG)
                .bizId(1L)
                .operationType(operationType)
                .operator("1")
                .occurredAt(LocalDateTime.now())
                .build();
        return service.append(event);
    }
}
