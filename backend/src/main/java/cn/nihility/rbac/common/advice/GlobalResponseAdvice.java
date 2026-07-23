package cn.nihility.rbac.common.advice;

import cn.nihility.rbac.common.result.Result;
import cn.nihility.rbac.common.util.JacksonUtils;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 全局响应包装器，把控制器方法的返回值统一包装成 {@link Result}。
 * 仅对 {@code cn.nihility.rbac} 包下的控制器生效，避免影响 springdoc/swagger-ui
 * 自身的接口输出。
 */
@RestControllerAdvice(basePackages = "cn.nihility.rbac")
public class GlobalResponseAdvice implements ResponseBodyAdvice<Object> {

    /**
     * 所有返回值都需要经过本处理器判断是否包装。
     *
     * @param returnType    方法返回类型
     * @param converterType 将要使用的消息转换器类型
     * @return 始终返回 true
     */
    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    /**
     * 在写出响应体之前，把业务数据包装为统一响应结构。
     *
     * @param body                  原始返回值
     * @param returnType            方法返回类型
     * @param selectedContentType   最终响应的 content-type
     * @param selectedConverterType 最终使用的消息转换器类型
     * @param request               当前请求
     * @param response              当前响应
     * @return 包装后的响应体
     */
    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType, ServerHttpRequest request,
            ServerHttpResponse response) {
        if (body instanceof Result) {
            return body;
        }
        if (StringHttpMessageConverter.class.isAssignableFrom(selectedConverterType)) {
            return writeAsJsonString(body, response);
        }
        return Result.success(body);
    }

    /**
     * 当选中的消息转换器是 {@link StringHttpMessageConverter} 时，
     * 需要手动把包装后的对象序列化为 JSON 字符串，否则会被当作普通文本写出。
     *
     * @param body     原始返回值
     * @param response 当前响应
     * @return 序列化后的 JSON 字符串
     */
    private String writeAsJsonString(Object body, ServerHttpResponse response) {
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return JacksonUtils.toJson(Result.success(body));
    }
}
