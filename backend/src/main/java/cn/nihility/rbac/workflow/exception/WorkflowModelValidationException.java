package cn.nihility.rbac.workflow.exception;

import cn.nihility.rbac.common.exception.BusinessException;
import java.util.List;
import lombok.Getter;

/**
 * 流程模型 DSL 结构/业务规则校验失败异常，携带具体校验失败的节点/连线定位信息
 * （workflow-approval-engine change specs/workflow-process-designer"发布前结构与业务规则
 * 的强制校验"Requirement）。由 {@code GlobalExceptionHandler} 复用 {@link BusinessException}
 * 的处理逻辑统一转换为 {@code { code, message, data } } 响应，不需要单独注册异常处理器。
 */
@Getter
public class WorkflowModelValidationException extends BusinessException {

    /** 全部校验失败明细，每条携带具体节点/连线 id 定位信息。 */
    private final List<String> errors;

    /**
     * 使用多条校验错误构造异常，提示信息拼接全部错误明细。
     *
     * @param errors 校验失败明细列表，不应为空
     */
    public WorkflowModelValidationException(List<String> errors) {
        super(String.join("；", errors));
        this.errors = List.copyOf(errors);
    }

    /**
     * 使用单条校验错误构造异常。
     *
     * @param error 校验失败说明
     */
    public WorkflowModelValidationException(String error) {
        this(List.of(error));
    }
}
