package cn.nihility.rbac.operationlog.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.operationlog.dto.OperationLogQueryRequest;
import cn.nihility.rbac.operationlog.dto.OperationLogVO;
import cn.nihility.rbac.operationlog.entity.OperationLogEntity;
import cn.nihility.rbac.operationlog.mapper.OperationLogMapper;
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
 * {@link OperationLogQueryServiceImpl} 的单元测试，重点覆盖 {@code targetId} 筛选参数
 * 透传给 {@link OperationLogMapper#selectOperationLogPage} 的行为（与 resourceType 组合、
 * 单独出现两种场景），SQL 层的实际过滤效果由 XML 里与其他筛选字段一致的 {@code <if>}
 * 结构决定，不在本单元测试范围内。
 */
@ExtendWith(MockitoExtension.class)
class OperationLogQueryServiceImplTest {

    /** 被测服务的操作日志数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private OperationLogMapper operationLogMapper;

    /** 被测服务实例，序列化组件使用 {@link cn.nihility.rbac.common.util.JacksonUtils}，不做打桩。 */
    private OperationLogQueryServiceImpl operationLogQueryService;

    /**
     * 每个用例执行前重新构造被测服务。
     */
    @BeforeEach
    void setUp() {
        operationLogQueryService = new OperationLogQueryServiceImpl(operationLogMapper);
    }

    /**
     * {@code resourceType} + {@code targetId} 组合筛选时，两个字段均应原样透传给 mapper。
     */
    @Test
    void getPage_shouldPassResourceTypeAndTargetIdToMapper() {
        when(operationLogMapper.selectOperationLogPage(any(), any())).thenReturn(new Page<OperationLogEntity>());

        OperationLogQueryRequest request = new OperationLogQueryRequest();
        request.setResourceType("org");
        request.setTargetId(5L);
        request.setPage(1);
        request.setPageSize(10);

        operationLogQueryService.getPage(request);

        ArgumentCaptor<OperationLogQueryRequest> captor = ArgumentCaptor.forClass(OperationLogQueryRequest.class);
        verify(operationLogMapper).selectOperationLogPage(any(), captor.capture());
        assertThat(captor.getValue().getResourceType()).isEqualTo("org");
        assertThat(captor.getValue().getTargetId()).isEqualTo(5L);
    }

    /**
     * {@code targetId} 单独出现（不携带 {@code resourceType}）时同样原样透传给 mapper，
     * 不做强制关联校验。
     */
    @Test
    void getPage_shouldPassTargetIdAlone_whenResourceTypeAbsent() {
        when(operationLogMapper.selectOperationLogPage(any(), any())).thenReturn(new Page<OperationLogEntity>());

        OperationLogQueryRequest request = new OperationLogQueryRequest();
        request.setTargetId(5L);
        request.setPage(1);
        request.setPageSize(10);

        operationLogQueryService.getPage(request);

        ArgumentCaptor<OperationLogQueryRequest> captor = ArgumentCaptor.forClass(OperationLogQueryRequest.class);
        verify(operationLogMapper).selectOperationLogPage(any(), captor.capture());
        assertThat(captor.getValue().getResourceType()).isNull();
        assertThat(captor.getValue().getTargetId()).isEqualTo(5L);
    }

    /**
     * 未携带 {@code targetId} 时该字段保持为 {@code null} 透传，不影响其余筛选条件。
     */
    @Test
    void getPage_shouldLeaveTargetIdNull_whenNotProvided() {
        when(operationLogMapper.selectOperationLogPage(any(), any())).thenReturn(new Page<OperationLogEntity>());

        OperationLogQueryRequest request = new OperationLogQueryRequest();
        request.setModule("组织管理");
        request.setPage(1);
        request.setPageSize(10);

        operationLogQueryService.getPage(request);

        ArgumentCaptor<OperationLogQueryRequest> captor = ArgumentCaptor.forClass(OperationLogQueryRequest.class);
        verify(operationLogMapper).selectOperationLogPage(any(), captor.capture());
        assertThat(captor.getValue().getModule()).isEqualTo("组织管理");
        assertThat(captor.getValue().getTargetId()).isNull();
    }

    /**
     * 分页记录里持久化的 {@code changeDetail} JSON 字符串应被反序列化为结构化列表，
     * 设置到对应行的 {@link OperationLogVO#getChangeDetail()} 上。
     */
    @Test
    void getPage_shouldParseChangeDetailForEachRecord() {
        OperationLogEntity entity = OperationLogEntity.builder()
                .id(1L)
                .module("组织管理")
                .resourceType("org")
                .operationType(2)
                .targetId(5L)
                .createBy("admin")
                .createTime(LocalDateTime.now())
                .changeDetail("[{\"field\":\"组织名称\",\"oldValue\":\"旧名称\",\"newValue\":\"新名称\"}]")
                .build();
        Page<OperationLogEntity> page = new Page<>();
        page.setRecords(List.of(entity));
        when(operationLogMapper.selectOperationLogPage(any(), any())).thenReturn(page);

        OperationLogQueryRequest request = new OperationLogQueryRequest();
        request.setPage(1);
        request.setPageSize(10);

        var result = operationLogQueryService.getPage(request);

        assertThat(result.getRecords()).hasSize(1);
        OperationLogVO vo = result.getRecords().get(0);
        assertThat(vo.getChangeDetail()).hasSize(1);
        assertThat(vo.getChangeDetail().get(0).getField()).isEqualTo("组织名称");
        assertThat(vo.getChangeDetail().get(0).getOldValue()).isEqualTo("旧名称");
        assertThat(vo.getChangeDetail().get(0).getNewValue()).isEqualTo("新名称");
    }

    /**
     * 未持久化字段变更详情（{@code changeDetail} 为空字符串）时，反序列化结果应为空列表，
     * 而非 {@code null}，方便前端直接渲染。
     */
    @Test
    void getPage_shouldReturnEmptyChangeDetail_whenBlank() {
        OperationLogEntity entity = OperationLogEntity.builder()
                .id(1L)
                .module("组织管理")
                .resourceType("org")
                .operationType(1)
                .targetId(5L)
                .createBy("admin")
                .createTime(LocalDateTime.now())
                .changeDetail("")
                .build();
        Page<OperationLogEntity> page = new Page<>();
        page.setRecords(List.of(entity));
        when(operationLogMapper.selectOperationLogPage(any(), any())).thenReturn(page);

        OperationLogQueryRequest request = new OperationLogQueryRequest();
        request.setPage(1);
        request.setPageSize(10);

        var result = operationLogQueryService.getPage(request);

        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getChangeDetail()).isEmpty();
    }
}
