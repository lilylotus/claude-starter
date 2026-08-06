package cn.nihility.rbac.operationlog.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.operationlog.dto.OperationLogQueryRequest;
import cn.nihility.rbac.operationlog.dto.OperationLogVO;
import cn.nihility.rbac.operationlog.entity.OperationLogEntity;
import cn.nihility.rbac.operationlog.mapper.OperationLogMapper;
import cn.nihility.rbac.user.entity.UserEntity;
import cn.nihility.rbac.user.mapper.UserMapper;
import cn.nihility.rbac.user.service.UserDisplayService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
 * {@link OperationLogQueryServiceImpl} 的单元测试，重点覆盖 {@code targetId} 筛选参数
 * 透传给 {@link OperationLogMapper#selectOperationLogPage} 的行为（与 resourceType 组合、
 * 单独出现两种场景），SQL 层的实际过滤效果由 XML 里与其他筛选字段一致的 {@code <if>}
 * 结构决定，不在本单元测试范围内；同时覆盖 {@code createBy} 查询条件按账号编码/姓名解析
 * 候选用户 id、解析不到任何用户时直接返回空分页三种场景。
 */
@ExtendWith(MockitoExtension.class)
class OperationLogQueryServiceImplTest {

    /** 被测服务的操作日志数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private OperationLogMapper operationLogMapper;

    /** 被测服务的用户数据访问依赖，用于解析 {@code createBy} 查询条件，使用 Mockito 打桩。 */
    @Mock
    private UserMapper userMapper;

    /** 被测服务的审计字段展示名解析依赖，使用 Mockito 打桩。 */
    @Mock
    private UserDisplayService userDisplayService;

    /** 被测服务实例，序列化组件使用 {@link cn.nihility.rbac.common.util.JacksonUtils}，不做打桩。 */
    private OperationLogQueryServiceImpl operationLogQueryService;

    /**
     * 每个用例执行前重新构造被测服务；{@code userDisplayService} 的展示名解析在多数用例中
     * 不是断言重点，统一用 lenient 打桩返回空 Map，避免每个用例单独打桩。
     */
    @BeforeEach
    void setUp() {
        operationLogQueryService = new OperationLogQueryServiceImpl(operationLogMapper, userMapper, userDisplayService);
        lenient().when(userDisplayService.resolveDisplayNames(any())).thenReturn(Map.of());
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

    /**
     * {@code createBy} 输入账号编码能匹配到用户时，应把候选用户 id 回填到
     * {@code createByUserIds} 并原样透传给 mapper。
     */
    @Test
    void getPage_shouldResolveCreateByUserIds_whenCreateByMatchesUserCode() {
        UserEntity user = UserEntity.builder().id(1L).code("admin").name("管理员").build();
        when(userMapper.selectList(any())).thenReturn(List.of(user));
        when(operationLogMapper.selectOperationLogPage(any(), any())).thenReturn(new Page<OperationLogEntity>());

        OperationLogQueryRequest request = new OperationLogQueryRequest();
        request.setCreateBy("admin");
        request.setPage(1);
        request.setPageSize(10);

        operationLogQueryService.getPage(request);

        ArgumentCaptor<OperationLogQueryRequest> captor = ArgumentCaptor.forClass(OperationLogQueryRequest.class);
        verify(operationLogMapper).selectOperationLogPage(any(), captor.capture());
        assertThat(captor.getValue().getCreateByUserIds()).containsExactly("1");
    }

    /**
     * {@code createBy} 输入姓名能匹配到用户时，同样应把候选用户 id 回填到
     * {@code createByUserIds} 并原样透传给 mapper。
     */
    @Test
    void getPage_shouldResolveCreateByUserIds_whenCreateByMatchesUserName() {
        UserEntity user = UserEntity.builder().id(2L).code("zhangsan").name("张三").build();
        when(userMapper.selectList(any())).thenReturn(List.of(user));
        when(operationLogMapper.selectOperationLogPage(any(), any())).thenReturn(new Page<OperationLogEntity>());

        OperationLogQueryRequest request = new OperationLogQueryRequest();
        request.setCreateBy("张三");
        request.setPage(1);
        request.setPageSize(10);

        operationLogQueryService.getPage(request);

        ArgumentCaptor<OperationLogQueryRequest> captor = ArgumentCaptor.forClass(OperationLogQueryRequest.class);
        verify(operationLogMapper).selectOperationLogPage(any(), captor.capture());
        assertThat(captor.getValue().getCreateByUserIds()).containsExactly("2");
    }

    /**
     * {@code createBy} 输入内容查不到任何用户时，应直接返回空分页，不再调用
     * {@link OperationLogMapper#selectOperationLogPage}。
     */
    @Test
    void getPage_shouldReturnEmptyPage_whenCreateByMatchesNoUser() {
        when(userMapper.selectList(any())).thenReturn(List.of());

        OperationLogQueryRequest request = new OperationLogQueryRequest();
        request.setCreateBy("不存在的用户");
        request.setPage(1);
        request.setPageSize(10);

        var result = operationLogQueryService.getPage(request);

        assertThat(result.getRecords()).isEmpty();
        assertThat(result.getTotal()).isZero();
        verify(operationLogMapper, never()).selectOperationLogPage(any(), any());
    }
}
