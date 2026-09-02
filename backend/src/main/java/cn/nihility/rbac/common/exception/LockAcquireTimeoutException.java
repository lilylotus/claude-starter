package cn.nihility.rbac.common.exception;

/**
 * {@code DistributedLockService#executeWithLock} 在 {@code waitSeconds} 等待时长内始终
 * 未能获取到目标锁时抛出，复用现有全局异常处理链路（{@link GlobalExceptionHandler}）统一
 * 转换为 {@code { code, message, data } } 响应，不需要新增处理分支。
 */
public class LockAcquireTimeoutException extends BusinessException {

    /**
     * 使用提示信息构造异常。
     *
     * @param message 提示信息
     */
    public LockAcquireTimeoutException(String message) {
        super(message);
    }
}
