package cn.nihility.rbac.org.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.approval.constant.ApprovalOperationType;
import cn.nihility.rbac.approval.dto.WriteOperationResultVO;
import cn.nihility.rbac.approval.service.ApprovalRequestService;
import cn.nihility.rbac.approval.service.ApprovalSwitchService;
import cn.nihility.rbac.formfield.constant.FormFieldBizType;
import cn.nihility.rbac.org.dto.OrgCreateRequest;
import cn.nihility.rbac.org.dto.OrgVO;
import cn.nihility.rbac.org.service.OrgService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link OrgController} 审批开关分流单元测试。
 */
@ExtendWith(MockitoExtension.class)
class OrgControllerTest {

    /** 组织业务服务。 */
    @Mock
    private OrgService orgService;

    /** 审批申请服务。 */
    @Mock
    private ApprovalRequestService approvalRequestService;

    /** 审批开关服务。 */
    @Mock
    private ApprovalSwitchService approvalSwitchService;

    /** 被测控制器。 */
    private OrgController controller;

    /**
     * 构造被测控制器。
     */
    @BeforeEach
    void setUp() {
        controller = new OrgController(orgService, approvalRequestService, approvalSwitchService);
    }

    /**
     * 组织审批关闭时应直接执行原新增逻辑。
     */
    @Test
    void create_shouldCallOriginalServiceDirectly_whenApprovalDisabled() {
        OrgCreateRequest request = new OrgCreateRequest();
        OrgVO created = OrgVO.builder().id(10L).name("测试组织").build();
        when(approvalSwitchService.isEnabled(FormFieldBizType.ORG)).thenReturn(false);
        when(orgService.create(request)).thenReturn(created);

        WriteOperationResultVO<?> result = controller.create(request);

        assertThat(result.isApprovalEnabled()).isFalse();
        assertThat(result.getData()).isSameAs(created);
        verify(orgService).create(request);
        verify(approvalRequestService, never()).submit(
                FormFieldBizType.ORG,
                ApprovalOperationType.CREATE,
                null,
                request);
    }

    /**
     * 组织审批开启时应提交审批且不提前执行业务新增。
     */
    @Test
    void create_shouldSubmitApproval_whenApprovalEnabled() {
        OrgCreateRequest request = new OrgCreateRequest();
        WriteOperationResultVO<?> pending = WriteOperationResultVO.pending(null);
        when(approvalSwitchService.isEnabled(FormFieldBizType.ORG)).thenReturn(true);
        doReturn(pending).when(approvalRequestService).submit(
                FormFieldBizType.ORG,
                ApprovalOperationType.CREATE,
                null,
                request);

        WriteOperationResultVO<?> result = controller.create(request);

        assertThat(result).isSameAs(pending);
        verify(orgService, never()).create(request);
    }
}
