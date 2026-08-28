package cn.nihility.rbac.app.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.app.dto.AppCreateRequest;
import cn.nihility.rbac.app.dto.AppVO;
import cn.nihility.rbac.app.service.AppService;
import cn.nihility.rbac.approval.constant.ApprovalOperationType;
import cn.nihility.rbac.approval.dto.WriteOperationResultVO;
import cn.nihility.rbac.approval.service.ApprovalRequestService;
import cn.nihility.rbac.approval.service.ApprovalSwitchService;
import cn.nihility.rbac.formfield.constant.FormFieldBizType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link AppController} 审批开关分流单元测试。
 */
@ExtendWith(MockitoExtension.class)
class AppControllerTest {

    /** 应用业务服务。 */
    @Mock
    private AppService appService;

    /** 审批申请服务。 */
    @Mock
    private ApprovalRequestService approvalRequestService;

    /** 审批开关服务。 */
    @Mock
    private ApprovalSwitchService approvalSwitchService;

    /** 被测控制器。 */
    private AppController controller;

    /**
     * 构造被测控制器。
     */
    @BeforeEach
    void setUp() {
        controller = new AppController(appService, approvalRequestService, approvalSwitchService);
    }

    /**
     * 应用审批关闭时应直接执行原新增逻辑。
     */
    @Test
    void create_shouldCallOriginalServiceDirectly_whenApprovalDisabled() {
        AppCreateRequest request = new AppCreateRequest();
        AppVO created = AppVO.builder().id(30L).name("测试应用").build();
        when(approvalSwitchService.isEnabled(FormFieldBizType.APP)).thenReturn(false);
        when(appService.create(request)).thenReturn(created);

        WriteOperationResultVO<?> result = controller.create(request);

        assertThat(result.isApprovalEnabled()).isFalse();
        assertThat(result.getData()).isSameAs(created);
        verify(appService).create(request);
        verify(approvalRequestService, never()).submit(
                FormFieldBizType.APP,
                ApprovalOperationType.CREATE,
                null,
                request);
    }

    /**
     * 应用审批开启时应提交审批且不提前执行业务新增。
     */
    @Test
    void create_shouldSubmitApproval_whenApprovalEnabled() {
        AppCreateRequest request = new AppCreateRequest();
        WriteOperationResultVO<?> pending = WriteOperationResultVO.pending(null);
        when(approvalSwitchService.isEnabled(FormFieldBizType.APP)).thenReturn(true);
        doReturn(pending).when(approvalRequestService).submit(
                FormFieldBizType.APP,
                ApprovalOperationType.CREATE,
                null,
                request);

        WriteOperationResultVO<?> result = controller.create(request);

        assertThat(result).isSameAs(pending);
        verify(appService, never()).create(request);
    }
}
