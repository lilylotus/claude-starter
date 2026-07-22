package cn.nihility.rbac.metadata.exception;

import cn.nihility.rbac.common.exception.BusinessException;

/**
 * {@code fieldCode} 在同一业务对象类型下与另一条元数据字段重复时抛出该异常。
 */
public class MetadataFieldCodeDuplicateException extends BusinessException {

    /**
     * 使用默认提示信息构造异常。
     *
     * @param message 提示信息
     */
    public MetadataFieldCodeDuplicateException(String message) {
        super(message);
    }
}
