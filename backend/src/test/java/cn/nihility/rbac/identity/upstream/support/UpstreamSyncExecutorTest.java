package cn.nihility.rbac.identity.upstream.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.app.config.AppSecretProperties;
import cn.nihility.rbac.identity.upstream.constant.UpstreamDataType;
import cn.nihility.rbac.identity.upstream.constant.UpstreamSyncStatus;
import cn.nihility.rbac.identity.upstream.dto.UpstreamFieldMappingRow;
import cn.nihility.rbac.identity.upstream.entity.UpstreamDomainConfigEntity;
import cn.nihility.rbac.identity.upstream.entity.UpstreamSourceEntity;
import cn.nihility.rbac.identity.upstream.entity.UpstreamSyncRecordEntity;
import cn.nihility.rbac.identity.upstream.mapper.UpstreamDomainConfigMapper;
import cn.nihility.rbac.identity.upstream.mapper.UpstreamFieldMappingMapper;
import cn.nihility.rbac.identity.upstream.mapper.UpstreamSourceMapper;
import cn.nihility.rbac.identity.upstream.mapper.UpstreamSyncRecordMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link UpstreamSyncExecutor#syncSource} 的单元测试，覆盖
 * upstream-field-mapping-primary-key change 新增的"数据域未配置主键字段时同步前置判定
 * 失败，不发起取数请求"场景（tasks.md 6.1）。
 */
@ExtendWith(MockitoExtension.class)
class UpstreamSyncExecutorTest {

    /** 被测组件的上游数据源数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private UpstreamSourceMapper upstreamSourceMapper;

    /** 被测组件的数据域配置数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private UpstreamDomainConfigMapper upstreamDomainConfigMapper;

    /** 被测组件的字段映射数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private UpstreamFieldMappingMapper upstreamFieldMappingMapper;

    /** 被测组件的同步执行记录数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private UpstreamSyncRecordMapper upstreamSyncRecordMapper;

    /** 被测组件的接口方式取数依赖，使用 Mockito 打桩，本用例断言其不被调用。 */
    @Mock
    private UpstreamHttpFetcher upstreamHttpFetcher;

    /** 被测组件的数据库表方式取数依赖，使用 Mockito 打桩，本用例断言其不被调用。 */
    @Mock
    private UpstreamJdbcFetcher upstreamJdbcFetcher;

    /** 被测组件的字段映射转换依赖，使用 Mockito 打桩，本用例断言其不被调用。 */
    @Mock
    private UpstreamFieldMappingTransformer upstreamFieldMappingTransformer;

    /** 被测组件的单行落库处理依赖，使用 Mockito 打桩，本用例断言其不被调用。 */
    @Mock
    private UpstreamRowUpserter upstreamRowUpserter;

    /** 被测组件的 SM4 主密钥配置依赖，使用 Mockito 打桩，本用例不涉及解密逻辑。 */
    @Mock
    private AppSecretProperties appSecretProperties;

    /** 被测组件实例。 */
    private UpstreamSyncExecutor upstreamSyncExecutor;

    /**
     * 每个用例执行前重新构造被测组件。
     */
    @BeforeEach
    void setUp() {
        upstreamSyncExecutor = new UpstreamSyncExecutor(upstreamSourceMapper, upstreamDomainConfigMapper,
                upstreamFieldMappingMapper, upstreamSyncRecordMapper, upstreamHttpFetcher, upstreamJdbcFetcher,
                upstreamFieldMappingTransformer, upstreamRowUpserter, appSecretProperties);
    }

    /**
     * 已启用数据域当前的字段映射中没有任何字段标记为"主键标识"时，应在处理任何一行
     * 数据之前就判定本次同步失败，直接写入一条 {@code FAILED}、{@code totalCount=0} 的
     * 执行记录，不发起取数请求（design.md Decision 4），也不会调用字段映射转换/单行
     * 落库处理组件。
     */
    @Test
    void syncSource_shouldFailFast_whenDomainHasNoPrimaryKeyField() {
        UpstreamSourceEntity source = UpstreamSourceEntity.builder().id(1L).build();
        when(upstreamSourceMapper.selectById(1L)).thenReturn(source);

        UpstreamDomainConfigEntity orgDomainConfig = UpstreamDomainConfigEntity.builder()
                .id(10L).sourceId(1L).dataType(UpstreamDataType.ORG).enabled(true).build();
        when(upstreamDomainConfigMapper.selectOne(any()))
                .thenReturn(orgDomainConfig)
                .thenReturn(null)
                .thenReturn(null);

        UpstreamFieldMappingRow mappingWithoutPrimaryKey = UpstreamFieldMappingRow.builder()
                .id(100L).upstreamFieldCode("orgCode").fieldCode("code").isPrimaryKey(false).build();
        when(upstreamFieldMappingMapper.selectBySourceIdAndDataType(1L, UpstreamDataType.ORG))
                .thenReturn(List.of(mappingWithoutPrimaryKey));

        upstreamSyncExecutor.syncSource(1L, "MANUAL");

        ArgumentCaptor<UpstreamSyncRecordEntity> captor = ArgumentCaptor.forClass(UpstreamSyncRecordEntity.class);
        verify(upstreamSyncRecordMapper).insert(captor.capture());
        UpstreamSyncRecordEntity record = captor.getValue();
        assertThat(record.getStatus()).isEqualTo(UpstreamSyncStatus.FAILED);
        assertThat(record.getTotalCount()).isEqualTo(0);
        assertThat(record.getFailSummary()).contains("主键字段");

        verifyNoInteractions(upstreamHttpFetcher, upstreamJdbcFetcher, upstreamFieldMappingTransformer,
                upstreamRowUpserter);
        verify(upstreamDomainConfigMapper, never()).updateById(any(UpstreamDomainConfigEntity.class));
    }
}
