package cn.nihility.rbac.formfield.exception;

import cn.nihility.rbac.common.exception.BusinessException;

/**
 * 控件类型配置为"下拉单选字典"或"多选字典下拉"但未关联有效字典类型时抛出该异常。
 */
public class DictTypeRequiredException extends BusinessException {

    /**
     * 使用默认提示信息构造异常。
     *
     * @param message 提示信息
     */
    public DictTypeRequiredException(String message) {
        super(message);
    }
}
