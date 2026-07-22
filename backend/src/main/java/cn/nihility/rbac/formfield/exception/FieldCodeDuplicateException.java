package cn.nihility.rbac.formfield.exception;

import cn.nihility.rbac.common.exception.BusinessException;

/**
 * {@code fieldCode} 在同一业务对象类型下与另一条有效定义重复时抛出该异常。
 */
public class FieldCodeDuplicateException extends BusinessException {

    /**
     * 使用默认提示信息构造异常。
     *
     * @param message 提示信息
     */
    public FieldCodeDuplicateException(String message) {
        super(message);
    }
}
