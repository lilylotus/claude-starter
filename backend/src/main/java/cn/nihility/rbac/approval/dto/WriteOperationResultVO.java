package cn.nihility.rbac.approval.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 主数据写操作统一响应，根据审批开关返回审批申请或已生效业务数据。
 *
 * @param <T> 业务数据类型
 */
@Getter
@Builder
@AllArgsConstructor
@Schema(description = "主数据写操作结果")
public class WriteOperationResultVO<T> {

    /** 本次操作是否进入审批流程。 */
    private final boolean approvalEnabled;

    /** 进入审批流程时生成的申请。 */
    private final ApprovalRequestVO approvalRequest;

    /** 直接生效时返回的业务数据。 */
    private final T data;

    /**
     * 构造待审批结果。
     *
     * @param request 审批申请
     * @param <T>     业务数据类型
     * @return 待审批结果
     */
    public static <T> WriteOperationResultVO<T> pending(ApprovalRequestVO request) {
        return new WriteOperationResultVO<>(true, request, null);
    }

    /**
     * 构造直接生效结果。
     *
     * @param data 已生效业务数据
     * @param <T>  业务数据类型
     * @return 直接生效结果
     */
    public static <T> WriteOperationResultVO<T> applied(T data) {
        return new WriteOperationResultVO<>(false, null, data);
    }
}
