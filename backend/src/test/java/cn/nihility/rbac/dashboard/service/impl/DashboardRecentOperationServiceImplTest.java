package cn.nihility.rbac.dashboard.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.auth.context.CurrentUserContext;
import cn.nihility.rbac.common.result.PageResult;
import cn.nihility.rbac.operationlog.dto.OperationLogQueryRequest;
import cn.nihility.rbac.operationlog.dto.OperationLogVO;
import cn.nihility.rbac.operationlog.service.OperationLogQueryService;
import cn.nihility.rbac.user.entity.UserEntity;
import cn.nihility.rbac.user.mapper.UserMapper;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link DashboardRecentOperationServiceImpl} 的单元测试，重点覆盖"用当前用户 id 查出
 * 登录账号编码，再用该编码作为 createBy 条件复用操作日志分页查询"这一链路。
 */
@ExtendWith(MockitoExtension.class)
class DashboardRecentOperationServiceImplTest {

    /** 被测服务的用户数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private UserMapper userMapper;

    /** 被测服务的操作日志查询依赖，使用 Mockito 打桩。 */
    @Mock
    private OperationLogQueryService operationLogQueryService;

    /** 被测服务实例。 */
    private DashboardRecentOperationServiceImpl dashboardRecentOperationService;

    /**
     * 每个用例执行前重新构造被测服务，并标记当前线程的登录用户 id。
     */
    @BeforeEach
    void setUp() {
        dashboardRecentOperationService = new DashboardRecentOperationServiceImpl(userMapper, operationLogQueryService);
        CurrentUserContext.setUserId(1L);
    }

    /**
     * 每个用例结束后清理线程标记，避免影响其他测试。
     */
    @AfterEach
    void tearDown() {
        CurrentUserContext.clear();
    }

    /**
     * 应先用当前用户 id 查出其登录账号编码，再以该编码作为 {@code createBy} 精确条件、
     * 固定第 1 页第 4 条调用操作日志分页查询，并原样返回其 {@code records}。
     */
    @Test
    void getRecentOperations_shouldQueryByCurrentUserCodeAndReturnRecords() {
        UserEntity user = new UserEntity();
        user.setCode("admin");
        when(userMapper.selectById(1L)).thenReturn(user);

        OperationLogVO record = OperationLogVO.builder().id(100L).createBy("admin").build();
        PageResult<OperationLogVO> pageResult = new PageResult<>(List.of(record), 1L, 1, 4);
        when(operationLogQueryService.getPage(any(OperationLogQueryRequest.class))).thenReturn(pageResult);

        List<OperationLogVO> result = dashboardRecentOperationService.getRecentOperations();

        assertThat(result).containsExactly(record);
        ArgumentCaptor<OperationLogQueryRequest> captor = ArgumentCaptor.forClass(OperationLogQueryRequest.class);
        verify(operationLogQueryService).getPage(captor.capture());
        assertThat(captor.getValue().getCreateBy()).isEqualTo("admin");
        assertThat(captor.getValue().getPage()).isEqualTo(1);
        assertThat(captor.getValue().getPageSize()).isEqualTo(4);
        verify(userMapper).selectById(eq(1L));
    }
}
