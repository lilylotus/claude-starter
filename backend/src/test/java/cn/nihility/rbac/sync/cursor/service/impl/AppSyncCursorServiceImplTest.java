package cn.nihility.rbac.sync.cursor.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import cn.nihility.rbac.sync.cursor.mapper.AppSyncCursorMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link AppSyncCursorServiceImpl} 的单元测试，覆盖原子推进水位的参数透传，以及写入异常不
 * 影响调用方（app-sync-changelog-pull change design.md Decision 9，tasks.md 4.4）。真正的
 * "并发/乱序请求不回退"由 {@code ON DUPLICATE KEY UPDATE ... GREATEST(...)} 原子 SQL 保证，
 * 本单元测试只能验证参数正确透传给该原子 SQL，无法在 Mockito 层面验证 SQL 本身的原子性。
 */
@ExtendWith(MockitoExtension.class)
class AppSyncCursorServiceImplTest {

    /** 被测服务的应用同步服务端投递水位数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private AppSyncCursorMapper mapper;

    /** 被测服务实例。 */
    private AppSyncCursorServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AppSyncCursorServiceImpl(mapper);
    }

    /**
     * 应将 appRefId/entityType/nextSeq 原样透传给原子 upsert SQL。
     */
    @Test
    void advance_shouldPassParamsToMapper() {
        service.advance(1L, "ORG", 100L);

        verify(mapper).upsertLastDeliveredSeq(eq(1L), eq("ORG"), eq(100L), any());
    }

    /**
     * 写入异常不应向调用方抛出，只记 WARN 日志，不影响 {@code /changes} 接口本次响应结果。
     */
    @Test
    void advance_shouldNotThrow_whenMapperFails() {
        doThrow(new RuntimeException("db down")).when(mapper).upsertLastDeliveredSeq(anyLong(), any(), anyLong(),
                any());

        service.advance(1L, "ORG", 100L);
    }
}
