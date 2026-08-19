package cn.nihility.rbac.sync.pull.record.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.common.result.PageResult;
import cn.nihility.rbac.sync.pull.record.constant.PullMode;
import cn.nihility.rbac.sync.pull.record.dto.AppPullRecordQueryRequest;
import cn.nihility.rbac.sync.pull.record.dto.AppPullRecordVO;
import cn.nihility.rbac.sync.pull.record.entity.AppPullRecordEntity;
import cn.nihility.rbac.sync.pull.record.mapper.AppPullRecordMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link AppPullRecordServiceImpl} 的单元测试，重点覆盖写入拉取日志时审计字段固定为
 * {@code open-api}、分页查询参数原样透传给 mapper、查询结果按 MapStruct 转换为视图对象
 * （add-app-sync-notify-pull-logs change tasks.md 7.2/7.3）。
 */
@ExtendWith(MockitoExtension.class)
class AppPullRecordServiceImplTest {

    /** 被测服务的拉取变更数据请求记录数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private AppPullRecordMapper appPullRecordMapper;

    /** 被测服务实例。 */
    private AppPullRecordServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AppPullRecordServiceImpl(appPullRecordMapper);
    }

    /**
     * 写入拉取日志时，各字段应原样落到实体上，审计字段固定为 {@code open-api}。
     */
    @Test
    void record_shouldInsertEntityWithOpenApiOperator() {
        service.record(1L, PullMode.BY_ID, "ORG", "请求了 3 个 bizId", 2);

        ArgumentCaptor<AppPullRecordEntity> captor = ArgumentCaptor.forClass(AppPullRecordEntity.class);
        verify(appPullRecordMapper).insert(captor.capture());
        AppPullRecordEntity entity = captor.getValue();
        assertThat(entity.getAppRefId()).isEqualTo(1L);
        assertThat(entity.getPullMode()).isEqualTo(PullMode.BY_ID);
        assertThat(entity.getDataType()).isEqualTo("ORG");
        assertThat(entity.getRequestSummary()).isEqualTo("请求了 3 个 bizId");
        assertThat(entity.getResultCount()).isEqualTo(2);
        assertThat(entity.getCreateBy()).isEqualTo("open-api");
        assertThat(entity.getUpdateBy()).isEqualTo("open-api");
    }

    /**
     * 分页查询参数（应用 id、时间范围）应原样透传给 mapper。
     */
    @Test
    void page_shouldPassQueryParamsToMapper() {
        Page<AppPullRecordEntity> page = new Page<>(1, 10);
        page.setRecords(List.of());
        when(appPullRecordMapper.selectPullRecordPage(any(), any())).thenReturn(page);

        AppPullRecordQueryRequest request = new AppPullRecordQueryRequest();
        request.setAppRefId(1L);
        request.setStartTime(LocalDateTime.of(2026, 1, 1, 0, 0));
        request.setEndTime(LocalDateTime.of(2026, 1, 31, 23, 59));
        request.setPage(1);
        request.setPageSize(10);

        service.page(request);

        ArgumentCaptor<AppPullRecordQueryRequest> captor = ArgumentCaptor.forClass(AppPullRecordQueryRequest.class);
        verify(appPullRecordMapper).selectPullRecordPage(any(), captor.capture());
        assertThat(captor.getValue().getAppRefId()).isEqualTo(1L);
        assertThat(captor.getValue().getStartTime()).isEqualTo(LocalDateTime.of(2026, 1, 1, 0, 0));
        assertThat(captor.getValue().getEndTime()).isEqualTo(LocalDateTime.of(2026, 1, 31, 23, 59));
    }

    /**
     * mapper 返回的实体分页结果应转换为视图对象分页结果，字段一一对应。
     */
    @Test
    void page_shouldConvertEntitiesToVO() {
        AppPullRecordEntity entity = AppPullRecordEntity.builder()
                .id(1L)
                .appRefId(1L)
                .pullMode(PullMode.BY_SEQUENCE)
                .dataType(null)
                .requestSummary("fromSequence=1000, limit=20")
                .resultCount(5)
                .createTime(LocalDateTime.of(2026, 1, 1, 12, 0))
                .build();
        Page<AppPullRecordEntity> page = new Page<>(1, 10);
        page.setRecords(List.of(entity));
        page.setTotal(1);
        when(appPullRecordMapper.selectPullRecordPage(any(), any())).thenReturn(page);

        AppPullRecordQueryRequest request = new AppPullRecordQueryRequest();
        request.setAppRefId(1L);
        request.setPage(1);
        request.setPageSize(10);

        PageResult<AppPullRecordVO> result = service.page(request);

        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getRecords()).hasSize(1);
        AppPullRecordVO vo = result.getRecords().get(0);
        assertThat(vo.getId()).isEqualTo(1L);
        assertThat(vo.getPullMode()).isEqualTo(PullMode.BY_SEQUENCE);
        assertThat(vo.getDataType()).isNull();
        assertThat(vo.getRequestSummary()).isEqualTo("fromSequence=1000, limit=20");
        assertThat(vo.getResultCount()).isEqualTo(5);
    }
}
