package cn.nihility.rbac.common.result;

import lombok.Getter;

/**
 * 统一响应结构，所有对外接口最终都以 {@code { code, message, data } } 的形状返回。
 * 前端 axios 响应拦截器依赖此结构做统一解包，其他模块新增接口时应复用该类型，
 * 不要再自定义别的响应外壳。
 *
 * @param <T> 业务数据类型
 */
@Getter
public class Result<T> {

    /** 业务状态码，0 表示成功，非 0 表示各类业务/系统异常。 */
    private static final int SUCCESS_CODE = 0;

    /** 状态码。 */
    private final int code;

    /** 提示信息。 */
    private final String message;

    /** 业务数据，失败时通常为 null。 */
    private final T data;

    /**
     * 构造响应对象。
     *
     * @param code    状态码
     * @param message 提示信息
     * @param data    业务数据
     */
    private Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /**
     * 构造一个携带数据的成功响应。
     *
     * @param data 业务数据
     * @param <T>  业务数据类型
     * @return 成功响应
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(SUCCESS_CODE, "success", data);
    }

    /**
     * 构造一个不携带数据的成功响应。
     *
     * @return 成功响应
     */
    public static Result<Void> success() {
        return new Result<>(SUCCESS_CODE, "success", null);
    }

    /**
     * 构造一个失败响应。
     *
     * @param code    状态码
     * @param message 提示信息
     * @param <T>     业务数据类型
     * @return 失败响应
     */
    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null);
    }
}
