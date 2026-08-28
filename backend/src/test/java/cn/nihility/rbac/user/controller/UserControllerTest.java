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
import cn.nihility.rbac.user.dto.UserCreateRequest;
import cn.nihility.rbac.user.dto.UserPositionRequest;
import cn.nihility.rbac.user.dto.UserUpdateRequest;
import cn.nihility.rbac.user.dto.UserVO;
import cn.nihility.rbac.user.service.UserService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link UserController} 审批开关分流单元测试。
 */
@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    /** 用户业务服务。 */
    @Mock
    private UserService userService;

    /** 审批申请服务。 */
    @Mock
    private ApprovalRequestService approvalRequestService;

    /** 审批开关服务。 */
    @Mock
    private ApprovalSwitchService approvalSwitchService;

    /** 被测控制器。 */
    private UserController controller;

    /**
     * 构造被测控制器。
     */
    @BeforeEach
    void setUp() {
        controller = new UserController(userService, approvalRequestService, approvalSwitchService);
    }

    /**
     * 用户审批关闭时应在控制器最外层直接执行原新增逻辑，并完整保留任职数组。
     */
    @Test
    void create_shouldCallOriginalServiceDirectly_whenApprovalDisabled() {
        UserCreateRequest request = buildRequest();
        UserVO created = UserVO.builder().id(30L).name("测试用户").build();
        when(approvalSwitchService.isEnabled(FormFieldBizType.USER)).thenReturn(false);
        when(userService.create(request)).thenReturn(created);

        WriteOperationResultVO<?> result = controller.create(request);

        assertThat(result.isApprovalEnabled()).isFalse();
        assertThat(result.getData()).isSameAs(created);
        assertThat(request.getPositions()).hasSize(1);
        verify(userService).create(request);
        verify(approvalRequestService, never()).submit(
                FormFieldBizType.USER,
                ApprovalOperationType.CREATE,
                null,
                request);
    }

    /**
     * 用户审批开启时应提交审批，不提前执行原新增逻辑。
     */
    @Test
    void create_shouldSubmitApproval_whenApprovalEnabled() {
        UserCreateRequest request = buildRequest();
        WriteOperationResultVO<?> pending = WriteOperationResultVO.pending(null);
        when(approvalSwitchService.isEnabled(FormFieldBizType.USER)).thenReturn(true);
        doReturn(pending).when(approvalRequestService).submit(
                FormFieldBizType.USER,
                ApprovalOperationType.CREATE,
                null,
                request);

        WriteOperationResultVO<?> result = controller.create(request);

        assertThat(result).isSameAs(pending);
        verify(userService, never()).create(request);
        verify(approvalRequestService).submit(
                FormFieldBizType.USER,
                ApprovalOperationType.CREATE,
                null,
                request);
    }

    /**
     * 用户审批关闭时更新请求应原样携带任职数组调用既有更新逻辑。
     */
    @Test
    void update_shouldPreservePositions_whenApprovalDisabled() {
        UserUpdateRequest request = buildUpdateRequest();
        UserVO updated = UserVO.builder().id(30L).name("更新用户").build();
        when(approvalSwitchService.isEnabled(FormFieldBizType.USER)).thenReturn(false);
        when(userService.update(30L, request)).thenReturn(updated);

        WriteOperationResultVO<?> result = controller.update(30L, request);

        assertThat(result.isApprovalEnabled()).isFalse();
        assertThat(result.getData()).isSameAs(updated);
        assertThat(request.getPositions()).hasSize(1);
        verify(userService).update(30L, request);
        verify(approvalRequestService, never()).submit(
                FormFieldBizType.USER,
                ApprovalOperationType.UPDATE,
                30L,
                request);
    }

    /**
     * 用户审批开启时更新请求应原样携带任职数组提交审批。
     */
    @Test
    void update_shouldPreservePositions_whenApprovalEnabled() {
        UserUpdateRequest request = buildUpdateRequest();
        WriteOperationResultVO<?> pending = WriteOperationResultVO.pending(null);
        when(approvalSwitchService.isEnabled(FormFieldBizType.USER)).thenReturn(true);
        doReturn(pending).when(approvalRequestService).submit(
                FormFieldBizType.USER,
                ApprovalOperationType.UPDATE,
                30L,
                request);

        WriteOperationResultVO<?> result = controller.update(30L, request);

        assertThat(result).isSameAs(pending);
        assertThat(request.getPositions()).hasSize(1);
        verify(userService, never()).update(30L, request);
        verify(approvalRequestService).submit(
                FormFieldBizType.USER,
                ApprovalOperationType.UPDATE,
                30L,
                request);
    }

    /**
     * 构造包含任职数据的用户新增请求。
     *
     * @return 用户新增请求
     */
    private UserCreateRequest buildRequest() {
        UserPositionRequest position = new UserPositionRequest();
        position.setOrgId(100L);
        position.setPositionType("primary");
        position.setShowOrder(0);

        UserCreateRequest request = new UserCreateRequest();
        request.setName("测试用户");
        request.setCode("U_TEST");
        request.setShowOrder(0);
        request.setPositions(List.of(position));
        return request;
    }

    /**
     * 构造包含任职数据的用户更新请求。
     *
     * @return 用户更新请求
     */
    private UserUpdateRequest buildUpdateRequest() {
        UserPositionRequest position = new UserPositionRequest();
        position.setId(1000L);
        position.setOrgId(100L);
        position.setPositionType("primary");
        position.setShowOrder(0);

        UserUpdateRequest request = new UserUpdateRequest();
        request.setName("更新用户");
        request.setCode("U_TEST");
        request.setShowOrder(0);
        request.setPositions(List.of(position));
        return request;
    }
}
