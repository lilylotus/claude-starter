package cn.nihility.rbac.formfield.exception;

import cn.nihility.rbac.common.exception.BusinessException;

/**
 * 创建表单字段定义时，绑定的元数据字段已被另一条有效定义占用时抛出该异常。
 */
public class MetadataFieldAlreadyBoundException extends BusinessException {

    /**
     * 使用默认提示信息构造异常。
     *
     * @param message 提示信息
     */
    public MetadataFieldAlreadyBoundException(String message) {
        super(message);
    }
}
