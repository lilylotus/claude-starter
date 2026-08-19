package cn.nihility.rbac.sync.notify.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.common.result.PageResult;
import cn.nihility.rbac.sync.notify.constant.NotifyStatus;
import cn.nihility.rbac.sync.notify.dto.AppNotifyRecordQueryRequest;
import cn.nihility.rbac.sync.notify.dto.AppNotifyRecordVO;
import cn.nihility.rbac.sync.notify.entity.AppNotifyRecordEntity;
import cn.nihility.rbac.sync.notify.mapper.AppNotifyRecordMapper;
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
 * {@link AppNotifyRecordServiceImpl} 的单元测试，重点覆盖分页查询参数原样透传给
 * {@link AppNotifyRecordMapper#selectNotifyRecordPage}、查询结果按 MapStruct 转换为
 * 视图对象（add-app-sync-notify-pull-logs change tasks.md 7.3）。
 */
@ExtendWith(MockitoExtension.class)
class AppNotifyRecordServiceImplTest {

    /** 被测服务的通知发送记录数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private AppNotifyRecordMapper appNotifyRecordMapper;

    /** 被测服务实例。 */
    private AppNotifyRecordServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AppNotifyRecordServiceImpl(appNotifyRecordMapper);
    }

    /**
     * 分页查询参数（应用 id、通知状态、时间范围）应原样透传给 mapper。
     */
    @Test
    void page_shouldPassQueryParamsToMapper() {
        Page<AppNotifyRecordEntity> page = new Page<>(1, 10);
        page.setRecords(List.of());
        when(appNotifyRecordMapper.selectNotifyRecordPage(any(), any())).thenReturn(page);

        AppNotifyRecordQueryRequest request = new AppNotifyRecordQueryRequest();
        request.setAppRefId(1L);
        request.setNotifyStatus(NotifyStatus.FAILURE);
        request.setStartTime(LocalDateTime.of(2026, 1, 1, 0, 0));
        request.setEndTime(LocalDateTime.of(2026, 1, 31, 23, 59));
        request.setPage(1);
        request.setPageSize(10);

        service.page(request);

        ArgumentCaptor<AppNotifyRecordQueryRequest> captor =
                ArgumentCaptor.forClass(AppNotifyRecordQueryRequest.class);
        verify(appNotifyRecordMapper).selectNotifyRecordPage(any(), captor.capture());
        assertThat(captor.getValue().getAppRefId()).isEqualTo(1L);
        assertThat(captor.getValue().getNotifyStatus()).isEqualTo(NotifyStatus.FAILURE);
        assertThat(captor.getValue().getStartTime()).isEqualTo(LocalDateTime.of(2026, 1, 1, 0, 0));
        assertThat(captor.getValue().getEndTime()).isEqualTo(LocalDateTime.of(2026, 1, 31, 23, 59));
    }

    /**
     * mapper 返回的实体分页结果应转换为视图对象分页结果，字段一一对应。
     */
    @Test
    void page_shouldConvertEntitiesToVO() {
        AppNotifyRecordEntity entity = AppNotifyRecordEntity.builder()
                .id(1L)
                .changeLogId(100L)
                .appRefId(1L)
                .dataType("ORG")
                .bizId(88L)
                .notifyStatus(NotifyStatus.SUCCESS)
                .httpStatus(200)
                .notifyUrl("http://example.com/notify")
                .createTime(LocalDateTime.of(2026, 1, 1, 12, 0))
                .build();
        Page<AppNotifyRecordEntity> page = new Page<>(1, 10);
        page.setRecords(List.of(entity));
        page.setTotal(1);
        when(appNotifyRecordMapper.selectNotifyRecordPage(any(), any())).thenReturn(page);

        AppNotifyRecordQueryRequest request = new AppNotifyRecordQueryRequest();
        request.setAppRefId(1L);
        request.setPage(1);
        request.setPageSize(10);

        PageResult<AppNotifyRecordVO> result = service.page(request);

        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getRecords()).hasSize(1);
        AppNotifyRecordVO vo = result.getRecords().get(0);
        assertThat(vo.getId()).isEqualTo(1L);
        assertThat(vo.getDataType()).isEqualTo("ORG");
        assertThat(vo.getBizId()).isEqualTo(88L);
        assertThat(vo.getNotifyStatus()).isEqualTo(NotifyStatus.SUCCESS);
        assertThat(vo.getNotifyUrl()).isEqualTo("http://example.com/notify");
    }
}
