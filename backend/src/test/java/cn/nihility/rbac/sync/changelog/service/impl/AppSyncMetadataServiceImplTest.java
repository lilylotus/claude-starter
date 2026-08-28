package cn.nihility.rbac.sync.changelog.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.sync.changelog.entity.AppSyncMetadataEntity;
import cn.nihility.rbac.sync.changelog.mapper.AppSyncMetadataMapper;
import cn.nihility.rbac.sync.changelog.service.AppSyncMetadataService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link AppSyncMetadataServiceImpl} 的单元测试，覆盖正常解析、元数据行不存在、元数据值非法
 * 三种场景均防御性返回 0（app-sync-changelog-pull change design.md Decision 8/9），以及
 * {@link AppSyncMetadataServiceImpl#advanceRetentionFloorSeq} 原样透传新值给原子 GREATEST
 * 更新（tasks.md 3.3，实际"不倒退"由 SQL 层 {@code GREATEST} 保证，此处只验证调用参数）。
 */
@ExtendWith(MockitoExtension.class)
class AppSyncMetadataServiceImplTest {

    /** 被测服务的数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private AppSyncMetadataMapper mapper;

    /** 被测服务实例。 */
    private AppSyncMetadataServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AppSyncMetadataServiceImpl(mapper);
    }

    /**
     * 元数据行存在且值合法时，应正确解析为 {@code long}。
     */
    @Test
    void getRetentionFloorSeq_shouldParseValue_whenRowExists() {
        when(mapper.selectById(AppSyncMetadataService.CHANGE_LOG_RETENTION_FLOOR_SEQ_KEY))
                .thenReturn(AppSyncMetadataEntity.builder().metadataValue("12345").build());

        assertThat(service.getRetentionFloorSeq()).isEqualTo(12345L);
    }

    /**
     * 元数据行不存在时应防御性返回 0，不抛异常。
     */
    @Test
    void getRetentionFloorSeq_shouldReturnZero_whenRowNotFound() {
        when(mapper.selectById(AppSyncMetadataService.CHANGE_LOG_RETENTION_FLOOR_SEQ_KEY)).thenReturn(null);

        assertThat(service.getRetentionFloorSeq()).isZero();
    }

    /**
     * 元数据值不是合法数字时应防御性返回 0，不抛异常。
     */
    @Test
    void getRetentionFloorSeq_shouldReturnZero_whenValueNotNumeric() {
        when(mapper.selectById(AppSyncMetadataService.CHANGE_LOG_RETENTION_FLOOR_SEQ_KEY))
                .thenReturn(AppSyncMetadataEntity.builder().metadataValue("not-a-number").build());

        assertThat(service.getRetentionFloorSeq()).isZero();
    }

    /**
     * 应把新值按十进制字符串原样传给 mapper 的原子 {@code GREATEST} 更新方法。
     */
    @Test
    void advanceRetentionFloorSeq_shouldDelegateToMapperWithDecimalString() {
        service.advanceRetentionFloorSeq(12345L);

        verify(mapper).advanceRetentionFloorSeqIfGreater(
                eq(AppSyncMetadataService.CHANGE_LOG_RETENTION_FLOOR_SEQ_KEY), eq("12345"), any(LocalDateTime.class));
    }

    /**
     * mapper 返回 0（未影响任何行，如元数据键被误删）时不应抛出异常，只记录 WARN 日志。
     */
    @Test
    void advanceRetentionFloorSeq_shouldNotThrow_whenNoRowAffected() {
        when(mapper.advanceRetentionFloorSeqIfGreater(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), any(LocalDateTime.class))).thenReturn(0);

        org.assertj.core.api.Assertions.assertThatCode(() -> service.advanceRetentionFloorSeq(1L))
                .doesNotThrowAnyException();
    }
}
