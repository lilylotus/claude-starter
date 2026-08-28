package cn.nihility.rbac.approval.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.nihility.rbac.approval.dto.ApprovalSubmitRequest;
import cn.nihility.rbac.approval.dto.ApprovalSwitchVO;
import cn.nihility.rbac.approval.dto.WriteOperationResultVO;
import cn.nihility.rbac.approval.service.ApprovalRequestService;
import cn.nihility.rbac.approval.service.ApprovalSwitchService;
import cn.nihility.rbac.auth.constant.AuthErrorCode;
import cn.nihility.rbac.auth.filter.IdentityAuthFilter;
import cn.nihility.rbac.auth.service.AuthorizationService;
import cn.nihility.rbac.auth.service.PasswordService;
import cn.nihility.rbac.auth.service.TokenService;
import cn.nihility.rbac.common.result.PageResult;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 审批申请与审批开关接口集成测试。
 * <p>
 * 测试使用真实 Controller、Bean Validation 与 {@link IdentityAuthFilter}，
 * 仅将业务 Service 替换为 Mock，以覆盖接口映射和鉴权边界。
 */
@ExtendWith(MockitoExtension.class)
class ApprovalControllerIntegrationTest {

    /** 测试 access-key。 */
    private static final String ACCESS_KEY = "approval-test-access-key";

    /** 审批申请服务。 */
    @Mock
    private ApprovalRequestService approvalRequestService;

    /** 审批开关服务。 */
    @Mock
    private ApprovalSwitchService approvalSwitchService;

    /** 令牌服务。 */
    @Mock
    private TokenService tokenService;

    /** 密码服务。 */
    @Mock
    private PasswordService passwordService;

    /** 权限服务。 */
    @Mock
    private AuthorizationService authorizationService;

    /** MockMvc 测试客户端。 */
    private MockMvc mockMvc;

    /**
     * 构造带身份鉴权过滤器的 Controller 测试环境。
     */
    @BeforeEach
    void setUp() {
        ApprovalRequestController requestController = new ApprovalRequestController(
                approvalRequestService,
                approvalSwitchService);
        ApprovalSwitchController switchController = new ApprovalSwitchController(approvalSwitchService);
        IdentityAuthFilter authFilter = new IdentityAuthFilter(
                tokenService,
                passwordService,
                authorizationService);
        mockMvc = MockMvcBuilders.standaloneSetup(requestController, switchController)
                .addFilters(authFilter)
                .build();
        lenient().when(tokenService.verifyAccessKey(ACCESS_KEY)).thenReturn(Optional.of(1L));
        lenient().when(passwordService.isFirstLogin(1L)).thenReturn(false);
    }

    /**
     * 审批申请六个接口应能完成参数绑定并调用对应 Service 方法。
     *
     * @throws Exception MockMvc 调用异常
     */
    @Test
    void approvalRequestEndpoints_shouldDelegateToService() throws Exception {
        lenient().when(authorizationService.hasPermission(eq(1L), anyString())).thenReturn(true);
        when(approvalSwitchService.isEnabled("APP")).thenReturn(true);
        doReturn(WriteOperationResultVO.pending(null))
                .when(approvalRequestService)
                .submit(any(ApprovalSubmitRequest.class));
        when(approvalRequestService.pageMine(null, null, null, 1, 10))
                .thenReturn(new PageResult<>(List.of(), 0L, 1, 10));
        when(approvalRequestService.pagePending(null, null, 1, 10))
                .thenReturn(new PageResult<>(List.of(), 0L, 1, 10));

        mockMvc.perform(post("/api/approval-requests")
                        .header("identity-token", ACCESS_KEY)
                        .header("menu", "ApprovalManagement:request:view")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bizType\":\"APP\",\"operationType\":\"DELETE\",\"targetId\":9}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/approval-requests/1/approve")
                        .header("identity-token", ACCESS_KEY)
                        .header("menu", "ApprovalManagement:request:approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"opinion\":\"同意\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/approval-requests/1/reject")
                        .header("identity-token", ACCESS_KEY)
                        .header("menu", "ApprovalManagement:request:approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"opinion\":\"信息不完整\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/approval-requests/1/cancel")
                        .header("identity-token", ACCESS_KEY)
                        .header("menu", "ApprovalManagement:request:view"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/approval-requests/mine")
                        .header("identity-token", ACCESS_KEY)
                        .header("menu", "ApprovalManagement:request:view"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/approval-requests/pending")
                        .header("identity-token", ACCESS_KEY)
                        .header("menu", "ApprovalManagement:request:approve"))
                .andExpect(status().isOk());

        verify(approvalRequestService).approve(1L, "同意");
        verify(approvalRequestService).reject(1L, "信息不完整");
        verify(approvalRequestService).cancel(1L);
        verify(approvalRequestService).pageMine(null, null, null, 1, 10);
        verify(approvalRequestService).pagePending(null, null, 1, 10);
    }

    /**
     * 审批开关查询与修改接口应调用对应 Service 方法。
     *
     * @throws Exception MockMvc 调用异常
     */
    @Test
    void approvalSwitchEndpoints_shouldDelegateToService() throws Exception {
        when(authorizationService.hasPermission(1L, "ApprovalManagement:switch:view")).thenReturn(true);
        when(authorizationService.hasPermission(1L, "ApprovalManagement:switch:edit")).thenReturn(true);
        when(approvalSwitchService.listAll()).thenReturn(List.of());
        when(approvalSwitchService.update("ORG", false))
                .thenReturn(ApprovalSwitchVO.builder().bizType("ORG").enabled(false).build());

        mockMvc.perform(get("/api/approval-switches")
                        .header("identity-token", ACCESS_KEY)
                        .header("menu", "ApprovalManagement:switch:view"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/approval-switches/ORG")
                        .header("identity-token", ACCESS_KEY)
                        .header("menu", "ApprovalManagement:switch:edit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk());

        verify(approvalSwitchService).listAll();
        verify(approvalSwitchService).update("ORG", false);
    }

    /**
     * 无审批处理权限时待审批接口应被过滤器拒绝。
     *
     * @throws Exception MockMvc 调用异常
     */
    @Test
    void pendingEndpoint_shouldRejectUserWithoutApprovalPermission() throws Exception {
        when(authorizationService.hasPermission(1L, "ApprovalManagement:request:approve")).thenReturn(false);

        mockMvc.perform(get("/api/approval-requests/pending")
                        .header("identity-token", ACCESS_KEY)
                        .header("menu", "ApprovalManagement:request:approve"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "\"code\":" + AuthErrorCode.FORBIDDEN)));
    }

    /**
     * 无审批开关编辑权限时修改接口应被过滤器拒绝。
     *
     * @throws Exception MockMvc 调用异常
     */
    @Test
    void switchUpdateEndpoint_shouldRejectUserWithoutEditPermission() throws Exception {
        when(authorizationService.hasPermission(1L, "ApprovalManagement:switch:edit")).thenReturn(false);

        mockMvc.perform(put("/api/approval-switches/ORG")
                        .header("identity-token", ACCESS_KEY)
                        .header("menu", "ApprovalManagement:switch:edit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "\"code\":" + AuthErrorCode.FORBIDDEN)));
    }
}
