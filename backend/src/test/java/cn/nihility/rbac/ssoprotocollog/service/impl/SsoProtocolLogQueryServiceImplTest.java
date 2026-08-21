package cn.nihility.rbac.ssoprotocollog.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.ssoprotocollog.constant.SsoProtocolLogEventType;
import cn.nihility.rbac.ssoprotocollog.constant.SsoProtocolLogResult;
import cn.nihility.rbac.ssoprotocollog.dto.SsoProtocolLogQueryRequest;
import cn.nihility.rbac.ssoprotocollog.dto.SsoProtocolLogVO;
import cn.nihility.rbac.ssoprotocollog.entity.SsoProtocolLogEntity;
import cn.nihility.rbac.ssoprotocollog.mapper.SsoProtocolLogMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link SsoProtocolLogQueryServiceImpl} 的单元测试（add-sso-protocol-access-log change
 * tasks.md 6.3），重点覆盖筛选条件按需拼接（全部参数为空/按 appRefId/protocol/eventType/
 * result 精确匹配/按时间范围）、按调用发生时间降序排列、{@code resultLabel} 中文文案填充等
 * 分支逻辑。脱离 Spring 容器的纯单元测试里调用 {@code getSqlSegment()} 做条件断言（同
 * {@code LoginLogQueryServiceImplTest} 既有模式）。
 */
@ExtendWith(MockitoExtension.class)
class SsoProtocolLogQueryServiceImplTest {

    /** 被测服务的 SSO 协议调用记录数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private SsoProtocolLogMapper ssoProtocolLogMapper;

    /** 被测服务实例。 */
    private SsoProtocolLogQueryServiceImpl ssoProtocolLogQueryService;

    /**
     * MyBatis-Plus 的 {@code LambdaQueryWrapper} 需要先有实体的 {@code TableInfo} 缓存才能在
     * 脱离 Spring 容器的纯单元测试里调用 {@code getSqlSegment()} 做条件断言（正常启动时该
     * 缓存由 Spring Boot 扫描 Mapper 时自动建立），这里手动触发一次初始化，仅供本测试类使用。
     */
    @BeforeAll
    static void primeLambdaColumnCache() {
        Configuration configuration = new Configuration();
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(configuration, "ssoProtocolLogQueryServiceImplTest");
        assistant.setCurrentNamespace(SsoProtocolLogEntity.class.getName());
        TableInfoHelper.initTableInfo(assistant, SsoProtocolLogEntity.class);
    }

    /**
     * 每个用例执行前重新构造被测服务。
     */
    @BeforeEach
    void setUp() {
        ssoProtocolLogQueryService = new SsoProtocolLogQueryServiceImpl(ssoProtocolLogMapper);
    }

    /**
     * 全部筛选参数为空时，应返回 mapper 查询到的全部记录，且查询条件里不包含任何筛选限制，
     * 仅保留按调用发生时间降序排序。
     */
    @Test
    void getPage_shouldReturnAllRecords_whenAllFiltersEmpty() {
        SsoProtocolLogEntity entity = buildEntity(1L, SsoProtocolLogEventType.LOGIN, SsoProtocolLogResult.SUCCESS);
        Page<SsoProtocolLogEntity> resultPage = new Page<>(1, 10, 1L);
        resultPage.setRecords(List.of(entity));
        when(ssoProtocolLogMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(resultPage);

        SsoProtocolLogQueryRequest request = new SsoProtocolLogQueryRequest();
        request.setPage(1);
        request.setPageSize(10);

        var pageResult = ssoProtocolLogQueryService.getPage(request);

        assertThat(pageResult.getTotal()).isEqualTo(1L);
        assertThat(pageResult.getRecords()).hasSize(1);
        SsoProtocolLogVO vo = pageResult.getRecords().get(0);
        assertThat(vo.getEventType()).isEqualTo(SsoProtocolLogEventType.LOGIN);
        assertThat(vo.getResultLabel()).isEqualTo("成功");

        ArgumentCaptor<LambdaQueryWrapper<SsoProtocolLogEntity>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(ssoProtocolLogMapper).selectPage(any(Page.class), captor.capture());
        String sqlSegment = captor.getValue().getSqlSegment();
        assertThat(sqlSegment).doesNotContain("appRefId =").doesNotContain("protocol =")
                .doesNotContain("eventType =").doesNotContain("result =").doesNotContain("sessionId =");
        assertThat(sqlSegment).contains("ORDER BY createTime DESC");
    }

    /**
     * 按 appRefId 精确匹配筛选时，查询条件应包含 appRefId 等值限制，不包含其余筛选限制。
     */
    @Test
    void getPage_shouldFilterByAppRefId_whenProvided() {
        when(ssoProtocolLogMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(new Page<>(1, 10, 0L));

        SsoProtocolLogQueryRequest request = new SsoProtocolLogQueryRequest();
        request.setAppRefId(100L);
        request.setPage(1);
        request.setPageSize(10);

        ssoProtocolLogQueryService.getPage(request);

        ArgumentCaptor<LambdaQueryWrapper<SsoProtocolLogEntity>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(ssoProtocolLogMapper).selectPage(any(Page.class), captor.capture());
        String sqlSegment = captor.getValue().getSqlSegment();
        assertThat(sqlSegment).contains("appRefId =");
        assertThat(sqlSegment).doesNotContain("protocol =").doesNotContain("eventType =").doesNotContain("result =");
    }

    /**
     * 按 protocol 精确匹配筛选时，查询条件应包含 protocol 等值限制。
     */
    @Test
    void getPage_shouldFilterByProtocol_whenProvided() {
        when(ssoProtocolLogMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(new Page<>(1, 10, 0L));

        SsoProtocolLogQueryRequest request = new SsoProtocolLogQueryRequest();
        request.setProtocol("CAS");
        request.setPage(1);
        request.setPageSize(10);

        ssoProtocolLogQueryService.getPage(request);

        ArgumentCaptor<LambdaQueryWrapper<SsoProtocolLogEntity>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(ssoProtocolLogMapper).selectPage(any(Page.class), captor.capture());
        assertThat(captor.getValue().getSqlSegment()).contains("protocol =");
    }

    /**
     * 按 eventType 精确匹配筛选时，查询条件应包含 eventType 等值限制。
     */
    @Test
    void getPage_shouldFilterByEventType_whenProvided() {
        when(ssoProtocolLogMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(new Page<>(1, 10, 0L));

        SsoProtocolLogQueryRequest request = new SsoProtocolLogQueryRequest();
        request.setEventType(SsoProtocolLogEventType.TOKEN);
        request.setPage(1);
        request.setPageSize(10);

        ssoProtocolLogQueryService.getPage(request);

        ArgumentCaptor<LambdaQueryWrapper<SsoProtocolLogEntity>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(ssoProtocolLogMapper).selectPage(any(Page.class), captor.capture());
        assertThat(captor.getValue().getSqlSegment()).contains("eventType =");
    }

    /**
     * 按 result 精确匹配筛选时，查询条件应包含 result 等值限制。
     */
    @Test
    void getPage_shouldFilterByResult_whenProvided() {
        when(ssoProtocolLogMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(new Page<>(1, 10, 0L));

        SsoProtocolLogQueryRequest request = new SsoProtocolLogQueryRequest();
        request.setResult(SsoProtocolLogResult.FAILED);
        request.setPage(1);
        request.setPageSize(10);

        ssoProtocolLogQueryService.getPage(request);

        ArgumentCaptor<LambdaQueryWrapper<SsoProtocolLogEntity>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(ssoProtocolLogMapper).selectPage(any(Page.class), captor.capture());
        assertThat(captor.getValue().getSqlSegment()).contains("result =");
    }

    /**
     * 按 sessionId 精确匹配筛选时，查询条件应包含 sessionId 等值限制（供"登录日志"页面按
     * 某条登录记录的 sessionId 查询关联的协议调用记录使用，add-sso-protocol-access-log
     * change design.md Decision 7）。
     */
    @Test
    void getPage_shouldFilterBySessionId_whenProvided() {
        when(ssoProtocolLogMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(new Page<>(1, 10, 0L));

        SsoProtocolLogQueryRequest request = new SsoProtocolLogQueryRequest();
        request.setSessionId("session-hash-abc");
        request.setPage(1);
        request.setPageSize(10);

        ssoProtocolLogQueryService.getPage(request);

        ArgumentCaptor<LambdaQueryWrapper<SsoProtocolLogEntity>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(ssoProtocolLogMapper).selectPage(any(Page.class), captor.capture());
        assertThat(captor.getValue().getSqlSegment()).contains("sessionId =");
    }

    /**
     * 按调用时间范围筛选时，查询条件应包含起止时间的比较限制。
     */
    @Test
    void getPage_shouldFilterByTimeRange_whenProvided() {
        when(ssoProtocolLogMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(new Page<>(1, 10, 0L));

        SsoProtocolLogQueryRequest request = new SsoProtocolLogQueryRequest();
        request.setStartTime(LocalDateTime.now().minusDays(1));
        request.setEndTime(LocalDateTime.now());
        request.setPage(1);
        request.setPageSize(10);

        ssoProtocolLogQueryService.getPage(request);

        ArgumentCaptor<LambdaQueryWrapper<SsoProtocolLogEntity>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(ssoProtocolLogMapper).selectPage(any(Page.class), captor.capture());
        String sqlSegment = captor.getValue().getSqlSegment();
        assertThat(sqlSegment).contains("createTime >=").contains("createTime <=");
    }

    /**
     * 构造一个测试用的 SSO 协议调用记录实体。
     *
     * @param id        主键 id
     * @param eventType 事件类型
     * @param result    调用结果
     * @return SSO 协议调用记录实体
     */
    private SsoProtocolLogEntity buildEntity(long id, String eventType, int result) {
        return SsoProtocolLogEntity.builder()
                .id(id)
                .eventType(eventType)
                .result(result)
                .createBy("test")
                .createTime(LocalDateTime.now())
                .updateBy("test")
                .updateTime(LocalDateTime.now())
                .build();
    }
}
