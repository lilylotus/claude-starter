package cn.nihility.rbac.user.controller;

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
import cn.nihility.rbac.user.dto.PositionCreateRequest;
import cn.nihility.rbac.user.dto.PositionVO;
import cn.nihility.rbac.user.service.PositionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link PositionController} 审批开关分流单元测试。
 */
@ExtendWith(MockitoExtension.class)
class PositionControllerTest {

    /** 任职业务服务。 */
    @Mock
    private PositionService positionService;

    /** 审批申请服务。 */
    @Mock
    private ApprovalRequestService approvalRequestService;

    /** 审批开关服务。 */
    @Mock
    private ApprovalSwitchService approvalSwitchService;

    /** 被测控制器。 */
    private PositionController controller;

    /**
     * 构造被测控制器。
     */
    @BeforeEach
    void setUp() {
        controller = new PositionController(positionService, approvalRequestService, approvalSwitchService);
    }

    /**
     * 任职审批关闭时应直接执行原新增逻辑。
     */
    @Test
    void create_shouldCallOriginalServiceDirectly_whenApprovalDisabled() {
        PositionCreateRequest request = new PositionCreateRequest();
        PositionVO created = PositionVO.builder().id(20L).build();
        when(approvalSwitchService.isEnabled(FormFieldBizType.POSITION)).thenReturn(false);
        when(positionService.create(request)).thenReturn(created);

        WriteOperationResultVO<?> result = controller.create(request);

        assertThat(result.isApprovalEnabled()).isFalse();
        assertThat(result.getData()).isSameAs(created);
        verify(positionService).create(request);
        verify(approvalRequestService, never()).submit(
                FormFieldBizType.POSITION,
                ApprovalOperationType.CREATE,
                null,
                request);
    }

    /**
     * 任职审批开启时应提交审批且不提前执行业务新增。
     */
    @Test
    void create_shouldSubmitApproval_whenApprovalEnabled() {
        PositionCreateRequest request = new PositionCreateRequest();
        WriteOperationResultVO<?> pending = WriteOperationResultVO.pending(null);
        when(approvalSwitchService.isEnabled(FormFieldBizType.POSITION)).thenReturn(true);
        doReturn(pending).when(approvalRequestService).submit(
                FormFieldBizType.POSITION,
                ApprovalOperationType.CREATE,
                null,
                request);

        WriteOperationResultVO<?> result = controller.create(request);

        assertThat(result).isSameAs(pending);
        verify(positionService, never()).create(request);
    }
}
