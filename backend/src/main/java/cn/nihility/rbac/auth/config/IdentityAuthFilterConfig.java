package cn.nihility.rbac.auth.config;

import cn.nihility.rbac.auth.filter.IdentityAuthFilter;
import cn.nihility.rbac.auth.service.AuthorizationService;
import cn.nihility.rbac.auth.service.PasswordService;
import cn.nihility.rbac.auth.service.TokenService;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * 注册 {@link IdentityAuthFilter}：运行在 {@code DispatcherServlet} 之前，对除白名单外的
 * 全部请求路径生效，顺序设置为高优先级以尽早拦截未登录/首登待改密/无权限请求。
 */
@Configuration
public class IdentityAuthFilterConfig {

    /**
     * 注册请求身份校验过滤器。
     *
     * @param tokenService         会话令牌业务逻辑接口
     * @param passwordService      密码业务逻辑接口
     * @param authorizationService 运行时鉴权业务逻辑接口
     * @return 过滤器注册信息
     */
    @Bean
    public FilterRegistrationBean<IdentityAuthFilter> identityAuthFilter(TokenService tokenService,
            PasswordService passwordService, AuthorizationService authorizationService) {
        FilterRegistrationBean<IdentityAuthFilter> registration = new FilterRegistrationBean<>(
                new IdentityAuthFilter(tokenService, passwordService, authorizationService));
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        registration.setName("identityAuthFilter");
        return registration;
    }
}
