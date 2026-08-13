package cn.nihility.rbac.sync.openapi.config;

import cn.nihility.rbac.sync.sign.OpenApiSignInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 注册 {@link OpenApiSignInterceptor}，仅对外部同步拉取接口路径
 * {@code /open/api/sync/**} 生效（app-sync-notify-pull-api change design.md
 * Decision 9），不影响管理端接口。
 */
@Configuration
@RequiredArgsConstructor
public class OpenApiSyncWebConfig implements WebMvcConfigurer {

    /** 对外拉取接口的请求校验拦截器。 */
    private final OpenApiSignInterceptor openApiSignInterceptor;

    /**
     * {@inheritDoc}
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(openApiSignInterceptor).addPathPatterns("/open/api/sync/**");
    }
}
