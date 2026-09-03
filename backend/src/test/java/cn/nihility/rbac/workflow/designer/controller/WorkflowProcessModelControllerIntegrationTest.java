package cn.nihility.rbac.workflow.designer.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.nihility.rbac.auth.constant.AuthErrorCode;
import cn.nihility.rbac.auth.filter.IdentityAuthFilter;
import cn.nihility.rbac.auth.service.AuthorizationService;
import cn.nihility.rbac.auth.service.PasswordService;
import cn.nihility.rbac.auth.service.TokenService;
import cn.nihility.rbac.workflow.designer.dto.ProcessDefinitionVersionVO;
import cn.nihility.rbac.workflow.designer.dto.ProcessModelVO;
import cn.nihility.rbac.workflow.designer.dto.PublishResultVO;
import cn.nihility.rbac.workflow.designer.service.WorkflowProcessModelService;
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
 * {@link WorkflowProcessModelController} 集成测试（workflow-approval-engine change
 * tasks.md 10.5）：使用真实 Controller + {@link IdentityAuthFilter}，仅 Mock 业务 Service，
 * 覆盖接口映射与"仅有编辑权限的用户调用发布接口被拒绝"权限边界场景。
 */
@ExtendWith(MockitoExtension.class)
class WorkflowProcessModelControllerIntegrationTest {

    /** 测试 access-key。 */
    private static final String ACCESS_KEY = "workflow-designer-test-access-key";

    @Mock
    private WorkflowProcessModelService workflowProcessModelService;

    @Mock
    private TokenService tokenService;

    @Mock
    private PasswordService passwordService;

    @Mock
    private AuthorizationService authorizationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        WorkflowProcessModelController controller = new WorkflowProcessModelController(workflowProcessModelService);
        IdentityAuthFilter authFilter = new IdentityAuthFilter(tokenService, passwordService, authorizationService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .addFilters(authFilter)
                .build();
        lenient().when(tokenService.verifyAccessKey(ACCESS_KEY)).thenReturn(Optional.of(1L));
        lenient().when(passwordService.isFirstLogin(1L)).thenReturn(false);
    }

    /** 持有全部权限点时，草稿保存/发布/下线/启用/版本历史五个接口都应正常调用对应 Service。 */
    @Test
    void endpoints_shouldDelegateToServiceWhenAuthorized() throws Exception {
        lenient().when(authorizationService.hasPermission(eq(1L), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(true);
        when(workflowProcessModelService.publish(1L, 1L))
                .thenReturn(PublishResultVO.builder().processDefinitionId(11L).version(2).build());
        when(workflowProcessModelService.listVersions(1L))
                .thenReturn(List.of(ProcessDefinitionVersionVO.builder().id(10L).version(1).build()));
        when(workflowProcessModelService.listModels()).thenReturn(List.of(ProcessModelVO.builder().id(1L).build()));
        when(workflowProcessModelService.getModel(1L)).thenReturn(ProcessModelVO.builder().id(1L).build());
        when(workflowProcessModelService.createModel("USER_CHANGE", "人员变更审批", 1L))
                .thenReturn(ProcessModelVO.builder().id(2L).processCode("USER_CHANGE").build());
        when(workflowProcessModelService.copyModel(1L, "USER_CHANGE_COPY", "人员变更审批副本", 1L))
                .thenReturn(ProcessModelVO.builder().id(3L).processCode("USER_CHANGE_COPY").build());

        mockMvc.perform(get("/api/workflow/process-models")
                        .header("identity-token", ACCESS_KEY)
                        .header("menu", "WorkflowDesign:model:view"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/workflow/process-models/1/copy")
                        .header("identity-token", ACCESS_KEY)
                        .header("menu", "WorkflowDesign:model:edit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"processCode\":\"USER_CHANGE_COPY\",\"processName\":\"人员变更审批副本\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/workflow/process-models/1")
                        .header("identity-token", ACCESS_KEY)
                        .header("menu", "WorkflowDesign:model:view"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/workflow/process-models")
                        .header("identity-token", ACCESS_KEY)
                        .header("menu", "WorkflowDesign:model:edit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"processCode\":\"USER_CHANGE\",\"processName\":\"人员变更审批\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/workflow/process-models/1/draft")
                        .header("identity-token", ACCESS_KEY)
                        .header("menu", "WorkflowDesign:model:edit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"modelJson\":\"{}\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/workflow/process-models/1/publish")
                        .header("identity-token", ACCESS_KEY)
                        .header("menu", "WorkflowDesign:model:publish"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"version\":2")));
        mockMvc.perform(post("/api/workflow/process-models/1/disable")
                        .header("identity-token", ACCESS_KEY)
                        .header("menu", "WorkflowDesign:model:disable"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/workflow/process-models/1/enable")
                        .header("identity-token", ACCESS_KEY)
                        .header("menu", "WorkflowDesign:model:disable"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/workflow/process-models/1/versions")
                        .header("identity-token", ACCESS_KEY)
                        .header("menu", "WorkflowDesign:model:view"))
                .andExpect(status().isOk());

        verify(workflowProcessModelService).saveDraft(1L, "{}");
        verify(workflowProcessModelService).publish(1L, 1L);
        verify(workflowProcessModelService).disable(1L);
        verify(workflowProcessModelService).enable(1L);
        verify(workflowProcessModelService).listVersions(1L);
        verify(workflowProcessModelService).listModels();
        verify(workflowProcessModelService).getModel(1L);
        verify(workflowProcessModelService).createModel("USER_CHANGE", "人员变更审批", 1L);
        verify(workflowProcessModelService).copyModel(1L, "USER_CHANGE_COPY", "人员变更审批副本", 1L);
    }

    /** 仅持有编辑权限、不持有发布权限的用户调用发布接口应被过滤器拒绝，流程模型服务不被调用。 */
    @Test
    void publish_shouldRejectUserWithOnlyEditPermission() throws Exception {
        when(authorizationService.hasPermission(1L, "WorkflowDesign:model:publish")).thenReturn(false);

        mockMvc.perform(post("/api/workflow/process-models/1/publish")
                        .header("identity-token", ACCESS_KEY)
                        .header("menu", "WorkflowDesign:model:publish"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "\"code\":" + AuthErrorCode.FORBIDDEN)));

        verify(workflowProcessModelService, org.mockito.Mockito.never()).publish(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong());
    }
}
