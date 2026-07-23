package cn.nihility.rbac.excelimport.exception;

import cn.nihility.rbac.common.exception.BusinessException;

/**
 * 对锁定（系统保护）的导入字段配置（POSITION 的 {@code __userCode}/
 * {@code __orgCode}）执行删除，或试图改绑表单字段定义、取消主键/必填标记时抛出
 * 该异常。
 */
public class LockedImportFieldConfigException extends BusinessException {

    /**
     * 使用默认提示信息构造异常。
     *
     * @param message 提示信息
     */
    public LockedImportFieldConfigException(String message) {
        super(message);
    }
}
