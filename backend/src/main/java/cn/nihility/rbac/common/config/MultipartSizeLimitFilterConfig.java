package cn.nihility.rbac.common.config;

import cn.nihility.rbac.common.filter.MultipartSizeLimitFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * 注册 {@link MultipartSizeLimitFilter}，顺序早于身份校验过滤器，超大请求尽早拒绝。
 */
@Configuration
public class MultipartSizeLimitFilterConfig {

    /**
     * 注册 multipart 上传大小守卫过滤器。
     *
     * @param uploadLimitProperties 上传大小守卫配置
     * @return 过滤器注册信息
     */
    @Bean
    public FilterRegistrationBean<MultipartSizeLimitFilter> multipartSizeLimitFilter(
            UploadLimitProperties uploadLimitProperties) {
        FilterRegistrationBean<MultipartSizeLimitFilter> registration = new FilterRegistrationBean<>(
                new MultipartSizeLimitFilter(uploadLimitProperties));
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 5);
        registration.setName("multipartSizeLimitFilter");
        return registration;
    }
}
