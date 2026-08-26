package cn.nihility.rbac.ssoprotocollog.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.ssoprotocollog.constant.SsoProtocolLogEventType;
import cn.nihility.rbac.ssoprotocollog.constant.SsoProtocolLogResult;
import cn.nihility.rbac.ssoprotocollog.dto.SsoProtocolLogQueryRequest;
import cn.nihility.rbac.ssoprotocollog.dto.SsoProtocolLogVO;
import cn.nihility.rbac.ssoprotocollog.mapper.SsoProtocolLogMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
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
 * {@link SsoProtocolLogQueryServiceImpl} 的单元测试。查询条件的动态拼接（应用/协议类型/
 * 事件类型/结果/会话标识/时间范围）与用户姓名/拒绝策略名称的关联查询均已下沉到
 * {@code SsoProtocolLogMapper.xml} 的手写 SQL 中完成（fix-sso-protocol-log-detail-display
 * change design.md Decision 1），脱离 Spring 容器的纯单元测试无法验证 XML 里的 SQL 正确性
 * （该部分由 tasks.md 5.1/5.2 手工验证覆盖），本测试类改为聚焦服务层自身职责：筛选参数原样
 * 透传给 mapper、分页参数正确转换、{@code resultLabel} 中文文案按结果码填充、mapper 返回的
 * {@code userName}/{@code deniedPolicyName} 展示字段原样透传到分页结果。
 */
@ExtendWith(MockitoExtension.class)
class SsoProtocolLogQueryServiceImplTest {

    /** 被测服务的 SSO 协议调用记录数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private SsoProtocolLogMapper ssoProtocolLogMapper;

    /** 被测服务实例。 */
    private SsoProtocolLogQueryServiceImpl ssoProtocolLogQueryService;

    /**
     * 每个用例执行前重新构造被测服务。
     */
    @BeforeEach
    void setUp() {
        ssoProtocolLogQueryService = new SsoProtocolLogQueryServiceImpl(ssoProtocolLogMapper);
    }

    /**
     * 筛选参数（应用/协议类型/事件类型/结果/会话标识/时间范围）均由服务层原样透传给 mapper，
     * 服务层自身不再做任何条件拼接或过滤。
     */
    @Test
    void getPage_shouldPassRequestThroughToMapperUnchanged() {
        Page<SsoProtocolLogVO> resultPage = new Page<>(1, 10, 0L);
        resultPage.setRecords(List.of());
        when(ssoProtocolLogMapper.selectSsoProtocolLogPage(any(IPage.class), any(SsoProtocolLogQueryRequest.class)))
                .thenReturn(resultPage);

        SsoProtocolLogQueryRequest request = new SsoProtocolLogQueryRequest();
        request.setAppRefId(100L);
        request.setProtocol("CAS");
        request.setEventType(SsoProtocolLogEventType.TOKEN);
        request.setResult(SsoProtocolLogResult.FAILED);
        request.setSessionId("session-hash-abc");
        request.setStartTime(LocalDateTime.now().minusDays(1));
        request.setEndTime(LocalDateTime.now());
        request.setPage(1);
        request.setPageSize(10);

        ssoProtocolLogQueryService.getPage(request);

        verify(ssoProtocolLogMapper).selectSsoProtocolLogPage(any(IPage.class), same(request));
    }

    /**
     * 分页参数（页码、每页条数）应原样转换为 mapper 入参的 {@link IPage}，分页结果的总条数、
     * 页码、每页条数应取自 mapper 返回的分页元信息。
     */
    @Test
    void getPage_shouldConvertPaginationParamsAndMetadata() {
        Page<SsoProtocolLogVO> resultPage = new Page<>(2, 20, 45L);
        resultPage.setRecords(List.of());
        when(ssoProtocolLogMapper.selectSsoProtocolLogPage(any(IPage.class), any(SsoProtocolLogQueryRequest.class)))
                .thenReturn(resultPage);

        SsoProtocolLogQueryRequest request = new SsoProtocolLogQueryRequest();
        request.setPage(2);
        request.setPageSize(20);

        var pageResult = ssoProtocolLogQueryService.getPage(request);

        assertThat(pageResult.getPage()).isEqualTo(2);
        assertThat(pageResult.getPageSize()).isEqualTo(20);
        assertThat(pageResult.getTotal()).isEqualTo(45L);

        ArgumentCaptor<IPage> pageCaptor = ArgumentCaptor.forClass(IPage.class);
        verify(ssoProtocolLogMapper).selectSsoProtocolLogPage(pageCaptor.capture(), any(SsoProtocolLogQueryRequest.class));
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(2L);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(20L);
    }

    /**
     * mapper 返回的记录里，{@code resultLabel} 由服务层按 {@code result} 填充中文文案；
     * mapper 已经关联查出的 {@code userName}/{@code deniedPolicyName} 原样透传，服务层不做
     * 二次加工。
     */
    @Test
    void getPage_shouldFillResultLabelAndPassThroughJoinedNames() {
        SsoProtocolLogVO success = buildVo(1L, SsoProtocolLogResult.SUCCESS, "张三", null);
        SsoProtocolLogVO deniedByPolicy = buildVo(2L, SsoProtocolLogResult.FAILED, "李四", "禁止外部访问策略");
        SsoProtocolLogVO deniedWithoutPolicy = buildVo(3L, SsoProtocolLogResult.FAILED, null, null);

        Page<SsoProtocolLogVO> resultPage = new Page<>(1, 10, 3L);
        resultPage.setRecords(List.of(success, deniedByPolicy, deniedWithoutPolicy));
        when(ssoProtocolLogMapper.selectSsoProtocolLogPage(any(IPage.class), any(SsoProtocolLogQueryRequest.class)))
                .thenReturn(resultPage);

        SsoProtocolLogQueryRequest request = new SsoProtocolLogQueryRequest();
        request.setPage(1);
        request.setPageSize(10);

        var pageResult = ssoProtocolLogQueryService.getPage(request);

        assertThat(pageResult.getRecords()).hasSize(3);
        assertThat(pageResult.getRecords().get(0).getResultLabel()).isEqualTo("成功");
        assertThat(pageResult.getRecords().get(0).getUserName()).isEqualTo("张三");
        assertThat(pageResult.getRecords().get(0).getDeniedPolicyName()).isNull();

        assertThat(pageResult.getRecords().get(1).getResultLabel()).isEqualTo("失败");
        assertThat(pageResult.getRecords().get(1).getUserName()).isEqualTo("李四");
        assertThat(pageResult.getRecords().get(1).getDeniedPolicyName()).isEqualTo("禁止外部访问策略");

        assertThat(pageResult.getRecords().get(2).getResultLabel()).isEqualTo("失败");
        assertThat(pageResult.getRecords().get(2).getUserName()).isNull();
        assertThat(pageResult.getRecords().get(2).getDeniedPolicyName()).isNull();
    }

    /**
     * 构造一个测试用的 SSO 协议调用记录视图对象，模拟 mapper 关联查询后的结果。
     *
     * @param id                主键 id
     * @param result            调用结果
     * @param userName          关联用户姓名，可为 {@code null}
     * @param deniedPolicyName  拒绝来源策略名称，可为 {@code null}
     * @return SSO 协议调用记录视图对象
     */
    private SsoProtocolLogVO buildVo(long id, int result, String userName, String deniedPolicyName) {
        return SsoProtocolLogVO.builder()
                .id(id)
                .result(result)
                .userName(userName)
                .deniedPolicyName(deniedPolicyName)
                .createTime(LocalDateTime.now())
                .build();
    }
}
