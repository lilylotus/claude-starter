package cn.nihility.rbac.formfield.exception;

import cn.nihility.rbac.common.exception.BusinessException;

/**
 * 创建/更新表单字段定义时，绑定的元数据字段不存在或状态非启用时抛出该异常。
 */
public class MetadataFieldUnavailableException extends BusinessException {

    /**
     * 使用默认提示信息构造异常。
     *
     * @param message 提示信息
     */
    public MetadataFieldUnavailableException(String message) {
        super(message);
    }
}
