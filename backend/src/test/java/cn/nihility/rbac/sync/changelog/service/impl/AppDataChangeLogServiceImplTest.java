package cn.nihility.rbac.sync.changelog.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.app.sync.constant.SyncDomain;
import cn.nihility.rbac.operationlog.constant.OperationType;
import cn.nihility.rbac.sync.changelog.entity.AppDataChangeLogEntity;
import cn.nihility.rbac.sync.changelog.mapper.AppDataChangeLogMapper;
import cn.nihility.rbac.sync.event.DomainChangeEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link AppDataChangeLogServiceImpl} 的单元测试，重点覆盖事件落库字段映射、
 * 空 id 列表/空数据类型集合的短路分支（design.md Decision 6/9）。
 */
@ExtendWith(MockitoExtension.class)
class AppDataChangeLogServiceImplTest {

    @Mock
    private AppDataChangeLogMapper appDataChangeLogMapper;

    private AppDataChangeLogServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AppDataChangeLogServiceImpl(appDataChangeLogMapper);
    }

    /**
     * {@code record} 应把事件字段正确映射到变更记录实体并调用 {@code insert}。
     */
    @Test
    void record_shouldMapEventFieldsAndInsert() {
        DomainChangeEvent event = DomainChangeEvent.builder()
                .dataType(SyncDomain.ORG)
                .bizId(88L)
                .operationType(OperationType.CREATE)
                .snapshot(Map.of("code", "ORG001"))
                .operator("1")
                .occurredAt(LocalDateTime.now())
                .build();

        service.record(event);

        ArgumentCaptor<AppDataChangeLogEntity> captor = ArgumentCaptor.forClass(AppDataChangeLogEntity.class);
        verify(appDataChangeLogMapper).insert(captor.capture());
        AppDataChangeLogEntity entity = captor.getValue();
        assertThat(entity.getDataType()).isEqualTo(SyncDomain.ORG);
        assertThat(entity.getBizId()).isEqualTo(88L);
        assertThat(entity.getOperationType()).isEqualTo(OperationType.CREATE);
        assertThat(entity.getDataSnapshot()).contains("ORG001");
        assertThat(entity.getCreateBy()).isEqualTo("1");
    }

    /**
     * {@code bizIds} 为空时应直接返回空列表，不查询数据库。
     */
    @Test
    void selectLatestByBizIds_shouldReturnEmptyWithoutQueryWhenBizIdsEmpty() {
        List<AppDataChangeLogEntity> result = service.selectLatestByBizIds(SyncDomain.ORG, List.of());

        assertThat(result).isEmpty();
        verify(appDataChangeLogMapper, never()).selectLatestByBizIds(any(), any());
    }

    /**
     * {@code bizIds} 非空时应委托给 Mapper 查询。
     */
    @Test
    void selectLatestByBizIds_shouldDelegateToMapper() {
        when(appDataChangeLogMapper.selectLatestByBizIds(SyncDomain.ORG, List.of(1L, 2L)))
                .thenReturn(List.of(AppDataChangeLogEntity.builder().id(10L).build()));

        List<AppDataChangeLogEntity> result = service.selectLatestByBizIds(SyncDomain.ORG, List.of(1L, 2L));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(10L);
    }

    /**
     * 数据类型集合为空时应直接返回空列表，不查询数据库。
     */
    @Test
    void selectBySequence_shouldReturnEmptyWithoutQueryWhenDataTypesEmpty() {
        List<AppDataChangeLogEntity> result = service.selectBySequence(List.of(), 100L, 20);

        assertThat(result).isEmpty();
        verify(appDataChangeLogMapper, never()).selectBySequence(any(), any(), anyInt());
    }
}
