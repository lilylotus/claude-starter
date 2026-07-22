package cn.nihility.rbac.formfield.exception;

import cn.nihility.rbac.common.exception.BusinessException;

/**
 * 对绑定承重字段（锁定字段）的表单字段定义执行停用/删除，或试图把
 * {@code isRequired}/{@code showInCreate}/{@code showInEdit} 改为 {@code false} 时
 * 抛出该异常。
 */
public class LockedFormFieldException extends BusinessException {

    /**
     * 使用默认提示信息构造异常。
     *
     * @param message 提示信息
     */
    public LockedFormFieldException(String message) {
        super(message);
    }
}
