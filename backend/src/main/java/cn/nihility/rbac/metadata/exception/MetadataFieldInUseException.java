package cn.nihility.rbac.metadata.exception;

import cn.nihility.rbac.common.exception.BusinessException;

/**
 * 元数据字段当前被至少一条有效的表单字段定义绑定，无法停用时抛出该异常。
 */
public class MetadataFieldInUseException extends BusinessException {

    /**
     * 使用默认提示信息构造异常。
     *
     * @param message 提示信息
     */
    public MetadataFieldInUseException(String message) {
        super(message);
    }
}
